package dev.burooj.speedbreaker.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class BreathProgressTest {
    @Test fun progressIsDerivedFromStartNotAccumulatedTicks() {
        // Same answer whether the overlay was attached throughout or reattached mid-breath.
        assertEquals(0.25f, breathProgress(10_000L, 13_000L, 12_000L), 0f)
        assertEquals(0.5f, breathProgress(10_000L, 16_000L, 12_000L), 0f)
    }

    @Test fun progressIsContinuousBetweenServiceTicks() {
        val early = breathProgress(0L, 3_000L, 12_000L)
        val frame = breathProgress(0L, 3_016L, 12_000L)
        assertEquals(true, frame > early)
        assertEquals(16f / 12_000f, frame - early, 1e-5f)
    }

    @Test fun progressIsClampedToTheBreath() {
        assertEquals(0f, breathProgress(5_000L, 4_000L, 8_000L), 0f)
        assertEquals(1f, breathProgress(0L, 20_000L, 8_000L), 0f)
        assertEquals(1f, breathProgress(0L, 1L, 0L), 0f)
    }
}
