package dev.burooj.speedbreaker.observation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.telecom.TelecomManager
import androidx.core.content.PermissionChecker

/** No call identity, notification content, or call history is read or retained. */
internal class CallSafety(private val context: Context) {
    enum class Status { Idle, InCall, Unavailable }

    /**
     * Whether Phone permission is granted. It is optional: without it the Telecom check is
     * skipped and only the audio mode is read, so a silent ringing call is not seen.
     */
    fun phoneAllowed(): Boolean = try {
        PermissionChecker.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PermissionChecker.PERMISSION_GRANTED
    } catch (_: RuntimeException) {
        false
    }

    // Telecom is only asked behind phoneAllowed(), which lint does not follow.
    @SuppressLint("MissingPermission")
    fun status(): Status {
        return try {
            if (phoneAllowed()) {
                val telecom = context.getSystemService(TelecomManager::class.java)
                    ?: return Status.Unavailable
                // Aggregate state includes ringing (even silent), held, and self-managed calls.
                // Query every observation: an event-only latch can miss a call already in progress
                // when the service reconnects, or briefly recreate an overlay between call events.
                if (telecom.isInCall) return Status.InCall
            }
            val audio = context.getSystemService(AudioManager::class.java)
                ?: return Status.Unavailable
            // With permission, a backstop for communication apps that do not register with
            // Telecom. Without it, the only call signal: ringtone and call audio change the mode,
            // a silent ringing call does not.
            if (audio.mode != AudioManager.MODE_NORMAL) Status.InCall else Status.Idle
        } catch (_: RuntimeException) {
            // Revocation and unavailable system services must never make an overlay unsafe.
            Status.Unavailable
        }
    }

    /**
     * The aggregate signals behind [status], for the diagnostic log only. A signal that
     * cannot be read is null. Booleans and an audio-mode number; nothing about any call.
     */
    data class Detail(val status: Status, val telecomInCall: Boolean?, val audioMode: Int?)

    @SuppressLint("MissingPermission")
    fun detail(): Detail = Detail(
        status = status(),
        telecomInCall = try {
            // Without Phone permission Telecom is not asked.
            if (phoneAllowed()) context.getSystemService(TelecomManager::class.java)?.isInCall else null
        } catch (_: RuntimeException) {
            null
        },
        audioMode = try {
            context.getSystemService(AudioManager::class.java)?.mode
        } catch (_: RuntimeException) {
            null
        },
    )
}
