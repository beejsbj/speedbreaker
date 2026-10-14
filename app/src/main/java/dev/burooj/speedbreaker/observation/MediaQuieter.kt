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

/**
 * Asks other apps to stop playing while a breath is on screen. Speedbreaker plays no sound;
 * this only claims audio focus, so well-behaved players pause. Apps that ignore focus are
 * not affected.
 *
 * One request per breath (keyed by the immutable [Breaker]), released on every path that
 * ends it. Never requested while call safety is active, so a call is never disturbed.
 */
internal class MediaQuieter(private val focus: AudioFocusHolder) {
    private var held: Breaker? = null

    /** Call with the breaker after each engine update; [safetyActive] is call/lock/screen-off yield. */
    fun sync(breaker: Breaker?, safetyActive: Boolean) {
        if (breaker == null || safetyActive) {
            release()
            return
        }
        if (held == breaker) return
        // A different breath replaces the previous one; take a fresh grant for it.
        release()
        // A refused grant is not retried every tick; the next breath asks again.
        focus.request()
        held = breaker
    }

    fun release() {
        if (held == null) return
        held = null
        focus.abandon()
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
