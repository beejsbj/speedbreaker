package dev.burooj.speedbreaker.observation

import dev.burooj.speedbreaker.enforcement.Breaker
import dev.burooj.speedbreaker.enforcement.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaQuieterTest {
    private class FakeFocus : AudioFocusHolder {
        var requests = 0
        var abandons = 0
        override fun request() { requests++ }
        override fun abandon() { abandons++ }
    }

    private class FakeMuter : StreamMuter {
        var active = true
        var muted = false
        var allow = true
        var mutes = 0
        var unmutes = 0
        override fun isMusicActive() = active
        override fun isMusicMuted() = muted
        override fun mute(): Boolean {
            if (!allow) return false
            mutes++
            muted = true
            return true
        }
        override fun unmute() { unmutes++; muted = false }
    }

    private class FakeMarker : MuteMarker { override var set = false }

    private val focus = FakeFocus()
    private val muter = FakeMuter()
    private val marker = FakeMarker()
    private val quieter = MediaQuieter(focus, muter, marker)
    private var now = 0L

    private fun MediaQuieter.sync(breaker: Breaker?, safetyActive: Boolean) =
        sync(breaker, safetyActive, now)

    private fun tickPastDelay(breaker: Breaker) {
        quieter.sync(breaker, safetyActive = false)
        now += 600
        quieter.sync(breaker, safetyActive = false)
    }
    private val first = Breaker("reels.app", Trigger.OPENING, 1_000L, 8)
    private val second = Breaker("reels.app", Trigger.OPENING, 90_000L, 8)

    @Test fun requestsOncePerBreathAcrossTicks() {
        repeat(20) { quieter.sync(first, safetyActive = false) }
        assertEquals(1, focus.requests)
        assertEquals(0, focus.abandons)
    }

    @Test fun abandonsWhenBreathEnds() {
        quieter.sync(first, safetyActive = false)
        quieter.sync(null, safetyActive = false)
        quieter.sync(null, safetyActive = false)
        assertEquals(1, focus.abandons)
    }

    @Test fun neverRequestsDuringSafetyYield() {
        repeat(5) { quieter.sync(first, safetyActive = true) }
        assertEquals(0, focus.requests)
        assertEquals(0, focus.abandons)
    }

    @Test fun abandonsWhenSafetyYieldStartsMidBreath() {
        quieter.sync(first, safetyActive = false)
        quieter.sync(first, safetyActive = true)
        assertEquals(1, focus.abandons)
        // Once safety clears, a still-visible target gets a fresh breath and a fresh request.
        quieter.sync(second, safetyActive = false)
        assertEquals(2, focus.requests)
    }

    @Test fun newBreathReplacesPreviousGrant() {
        quieter.sync(first, safetyActive = false)
        quieter.sync(second, safetyActive = false)
        assertEquals(2, focus.requests)
        assertEquals(1, focus.abandons)
    }

    @Test fun releaseIsIdempotentAndSafeWhenNothingHeld() {
        quieter.release()
        quieter.sync(first, safetyActive = false)
        quieter.release()
        quieter.release()
        assertEquals(1, focus.abandons)
    }

    @Test fun mutesOnlyAfterDelayWhenMusicStillActive() {
        quieter.sync(first, safetyActive = false)
        now += 250
        quieter.sync(first, safetyActive = false)
        assertEquals(0, muter.mutes)
        now += 300
        quieter.sync(first, safetyActive = false)
        assertEquals(1, muter.mutes)
        assertTrue(marker.set)
        repeat(10) { now += 250; quieter.sync(first, safetyActive = false) }
        assertEquals(1, muter.mutes)
    }

    @Test fun doesNotMuteWhenFocusSilencedThePlayer() {
        muter.active = false
        tickPastDelay(first)
        assertEquals(0, muter.mutes)
        assertFalse(marker.set)
        quieter.sync(null, safetyActive = false)
        assertEquals(0, muter.unmutes)
    }

    @Test fun neverMutesDuringSafety() {
        repeat(5) { now += 600; quieter.sync(first, safetyActive = true) }
        assertEquals(0, muter.mutes)
        assertFalse(marker.set)
    }

    @Test fun unmutesWhenBreathEnds() {
        tickPastDelay(first)
        quieter.sync(null, safetyActive = false)
        assertEquals(1, muter.unmutes)
        assertFalse(muter.muted)
        assertFalse(marker.set)
        quieter.sync(null, safetyActive = false)
        assertEquals(1, muter.unmutes)
    }

    @Test fun unmutesWhenSafetyYieldStartsMidBreath() {
        tickPastDelay(first)
        quieter.sync(first, safetyActive = true)
        assertEquals(1, muter.unmutes)
        assertEquals(1, focus.abandons)
    }

    @Test fun unmutesOnExplicitRelease() {
        tickPastDelay(first)
        quieter.release()
        assertEquals(1, muter.unmutes)
        assertFalse(marker.set)
    }

    @Test fun unmutesBetweenBreaths() {
        tickPastDelay(first)
        quieter.sync(second, safetyActive = false)
        assertEquals(1, muter.unmutes)
    }

    @Test fun leavesStreamAloneWhenUserAlreadyUnmuted() {
        tickPastDelay(first)
        muter.muted = false
        quieter.sync(null, safetyActive = false)
        assertEquals(0, muter.unmutes)
        assertFalse(marker.set)
    }

    @Test fun doesNotClaimAStreamTheUserAlreadyMuted() {
        muter.muted = true
        tickPastDelay(first)
        assertEquals(0, muter.mutes)
        quieter.sync(null, safetyActive = false)
        assertEquals(0, muter.unmutes)
        assertTrue(muter.muted)
    }

    @Test fun refusedMuteLeavesNoMarker() {
        muter.allow = false
        tickPastDelay(first)
        assertFalse(marker.set)
        quieter.sync(null, safetyActive = false)
        assertEquals(0, muter.unmutes)
    }

    @Test fun crashRecoveryUnmutesWhenMarkerSet() {
        marker.set = true
        muter.muted = true
        quieter.recoverAfterCrash()
        assertEquals(1, muter.unmutes)
        assertFalse(marker.set)
    }

    @Test fun crashRecoveryDoesNothingWithoutMarker() {
        muter.muted = true
        quieter.recoverAfterCrash()
        assertEquals(0, muter.unmutes)
        assertTrue(muter.muted)
    }
}
