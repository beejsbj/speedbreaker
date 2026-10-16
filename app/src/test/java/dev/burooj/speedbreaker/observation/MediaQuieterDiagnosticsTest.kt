package dev.burooj.speedbreaker.observation

import dev.burooj.speedbreaker.enforcement.Breaker
import dev.burooj.speedbreaker.enforcement.Trigger
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaQuieterDiagnosticsTest {
    private class Focus : AudioFocusHolder {
        override fun request() = Unit
        override fun abandon() = Unit
    }

    private class Muter : StreamMuter {
        var muted = false
        var allow = true
        override fun isMusicActive() = true
        override fun isMusicMuted() = muted
        override fun mute(): Boolean {
            if (!allow) return false
            muted = true
            return true
        }
        override fun unmute() { muted = false }
    }

    private class Marker : MuteMarker { override var set = false }

    private val lines = mutableListOf<String>()
    private val muter = Muter()
    private val marker = Marker()
    private val quieter = MediaQuieter(Focus(), muter, marker) { lines += it }
    private val breath = Breaker("reels.app", Trigger.OPENING, 1_000L, 8)

    @Test fun logsTheMuteAndTheUnmuteOfOneBreath() {
        quieter.sync(breath, safetyActive = false, nowMs = 0)
        quieter.sync(breath, safetyActive = false, nowMs = 600)
        quieter.sync(breath, safetyActive = false, nowMs = 850)
        quieter.sync(null, safetyActive = false, nowMs = 900)
        quieter.sync(null, safetyActive = false, nowMs = 1_150)

        assertEquals(
            listOf("media mute applied afterMs=600", "media focus abandoned", "media unmute"),
            lines,
        )
    }

    @Test fun logsARefusedMuteOnceAndNoUnmute() {
        muter.allow = false

        quieter.sync(breath, safetyActive = false, nowMs = 0)
        quieter.sync(breath, safetyActive = false, nowMs = 600)
        quieter.sync(breath, safetyActive = false, nowMs = 850)
        quieter.sync(null, safetyActive = false, nowMs = 900)

        assertEquals(listOf("media mute refused afterMs=600", "media focus abandoned"), lines)
    }

    @Test fun logsWhenTheUserUnmutedByHandDuringTheBreath() {
        quieter.sync(breath, safetyActive = false, nowMs = 0)
        quieter.sync(breath, safetyActive = false, nowMs = 600)
        muter.muted = false

        quieter.sync(null, safetyActive = false, nowMs = 700)

        assertEquals(
            listOf(
                "media mute applied afterMs=600",
                "media focus abandoned",
                "media unmute skipped, already unmuted by the user",
            ),
            lines,
        )
    }

    @Test fun logsACrashRecoveryUnmuteOnlyWhenTheMarkerWasLeftBehind() {
        quieter.recoverAfterCrash()
        assertEquals(emptyList<String>(), lines)

        marker.set = true
        muter.muted = true
        quieter.recoverAfterCrash()

        assertEquals(listOf("media unmute after crash recovery"), lines)
        assertEquals(false, muter.muted)
    }
}
