package dev.burooj.speedbreaker.observation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import dev.burooj.speedbreaker.enforcement.Breaker

/** Holds and releases the one thing Speedbreaker asks of the audio system. */
internal interface AudioFocusHolder {
    fun request()
    fun abandon()
}

/** The music stream, for players that ignore audio focus. */
internal interface StreamMuter {
    fun isMusicActive(): Boolean
    fun isMusicMuted(): Boolean

    /** Mutes the music stream; false when the system refused, so nothing was changed. */
    fun mute(): Boolean
    fun unmute()
}

/** Survives a process death so a stream we muted is never left muted. Not usage history. */
internal interface MuteMarker {
    var set: Boolean
}

/**
 * Asks other apps to stop playing while a breath is on screen. Speedbreaker plays no sound;
 * this claims audio focus so well-behaved players pause. Players that ignore focus (Instagram
 * Reels) keep going, so from shortly after the request, once music is active the music
 * stream is muted for the rest of the breath.
 *
 * One request per breath (keyed by the immutable [Breaker]), released on every path that
 * ends it. Never requested or muted while call safety is active, so a call is never disturbed.
 *
 * End order: abandon focus first, then unmute. A background player that resumes on the
 * focus gain starts into a still-muted stream for a few milliseconds instead of the
 * ignoring target getting a blip of sound before it is sent away; the unmute follows
 * immediately. We only unmute if the stream is still muted, so a user who unmuted by hand
 * during the breath is left alone.
 */
internal class MediaQuieter(
    private val focus: AudioFocusHolder,
    private val muter: StreamMuter,
    private val marker: MuteMarker,
) {
    private var held: Breaker? = null
    private var requestedAtMs = 0L
    private var muteSettled = false
    private var weMuted = false

    /**
     * Call with the breaker after each engine update; [safetyActive] is call/lock/screen-off
     * yield and [nowMs] a monotonic clock. The ticker drives the delayed mute check.
     */
    fun sync(breaker: Breaker?, safetyActive: Boolean, nowMs: Long) {
        if (breaker == null || safetyActive) {
            release()
            return
        }
        if (held != breaker) {
            // A different breath replaces the previous one; take a fresh grant for it.
            release()
            // A refused grant is not retried every tick; the next breath asks again.
            focus.request()
            held = breaker
            requestedAtMs = nowMs
            muteSettled = false
        }
        // Checked every tick after the delay, not once: Reels often starts playing only after
        // the target finishes loading, well into the breath. Settles after one mute or one
        // refusal, so a user who turns the volume back up is not muted again.
        if (!muteSettled && nowMs - requestedAtMs >= MUTE_DELAY_MS) {
            // Still playing after the focus request means the player ignored it. A stream the
            // user already muted is theirs; leave it and do not claim it.
            if (muter.isMusicActive() && !muter.isMusicMuted()) {
                muteSettled = true
                // Marker first: dying between the two leaves a harmless unmute, not a stuck mute.
                marker.set = true
                if (muter.mute()) weMuted = true else marker.set = false
            }
        }
    }

    fun release() {
        if (held != null) {
            held = null
            focus.abandon()
        }
        if (weMuted) {
            weMuted = false
            if (muter.isMusicMuted()) muter.unmute()
            marker.set = false
        }
    }

    /** Service start: undo a mute left behind by a process that died mid-breath. */
    fun recoverAfterCrash() {
        if (!marker.set) return
        if (muter.isMusicMuted()) muter.unmute()
        marker.set = false
    }

    private companion object {
        // Long enough for a well-behaved player to react to the focus loss, short enough
        // that an ignoring one is silenced almost at once.
        const val MUTE_DELAY_MS = 500L
    }
}

internal class SystemAudioFocusHolder(context: Context) : AudioFocusHolder {
    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)

    // Transient focus. Audio focus is phone-wide, so a permanent grant would also stop
    // background music or a podcast for good on every breath. A transient loss pauses
    // every player for the breath and lets background players resume afterwards; the
    // target itself stops on its own once Leave or Redirect sends it to the background.
    private val request: AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener { }
            .build()

    override fun request() {
        try {
            audio?.requestAudioFocus(request)
        } catch (_: RuntimeException) {
        }
    }

    override fun abandon() {
        try {
            audio?.abandonAudioFocusRequest(request)
        } catch (_: RuntimeException) {
        }
    }
}

internal class SystemStreamMuter(context: Context) : StreamMuter {
    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)

    override fun isMusicActive(): Boolean = try {
        audio?.isMusicActive == true
    } catch (_: RuntimeException) {
        false
    }

    override fun isMusicMuted(): Boolean = try {
        audio?.isStreamMute(AudioManager.STREAM_MUSIC) == true
    } catch (_: RuntimeException) {
        false
    }

    // No UI flag: no volume panel pops over the breath. A SecurityException (for example when
    // Do Not Disturb policy forbids it) is caught and we simply do nothing.
    override fun mute(): Boolean = try {
        audio?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
        audio != null && audio.isStreamMute(AudioManager.STREAM_MUSIC)
    } catch (_: RuntimeException) {
        false
    }

    override fun unmute() {
        try {
            audio?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
        } catch (_: RuntimeException) {
        }
    }
}

internal class PrefsMuteMarker(context: Context) : MuteMarker {
    private val prefs = context.applicationContext
        .getSharedPreferences("media_quieter", Context.MODE_PRIVATE)

    override var set: Boolean
        get() = prefs.getBoolean(KEY, false)
        set(value) {
            // commit, not apply: the write must land before the process can die.
            prefs.edit().putBoolean(KEY, value).commit()
        }

    private companion object {
        const val KEY = "we_muted_music"
    }
}
