package dev.burooj.speedbreaker.observation

import dev.burooj.speedbreaker.enforcement.Breaker
import dev.burooj.speedbreaker.enforcement.Trigger
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaQuieterTest {
    private class FakeFocus : AudioFocusHolder {
        var requests = 0
        var abandons = 0
        override fun request() { requests++ }
        override fun abandon() { abandons++ }
    }

    private val focus = FakeFocus()
    private val quieter = MediaQuieter(focus)
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
}
