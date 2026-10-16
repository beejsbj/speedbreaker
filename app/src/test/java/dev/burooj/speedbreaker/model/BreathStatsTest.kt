package dev.burooj.speedbreaker.model

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BreathStatsTest {
    @Test
    fun countedMs_isCappedAtTheConfiguredBreath() {
        assertEquals(12_000L, countedBreathMs(shownMs = 40_000, breathSeconds = 12))
        assertEquals(7_300L, countedBreathMs(shownMs = 7_300, breathSeconds = 12))
    }

    @Test
    fun countedMs_ignoresTransientRestarts() {
        assertNull(countedBreathMs(shownMs = 250, breathSeconds = 12))
        assertNull(countedBreathMs(shownMs = 999, breathSeconds = 12))
        assertEquals(1_000L, countedBreathMs(shownMs = 1_000, breathSeconds = 12))
    }

    @Test
    fun withBreath_accumulatesTotalsAndToday() {
        val stats = BreathStats()
            .withBreath(8_000, "2026-10-16")
            .withBreath(12_000, "2026-10-16")
        assertEquals(BreathStats(20_000, 2, "2026-10-16", 20_000), stats)
        assertEquals(20_000L, stats.todayMsOn("2026-10-16"))
    }

    @Test
    fun dayRollover_restartsTodayButKeepsTotals() {
        val yesterday = BreathStats().withBreath(10_000, "2026-10-16")
        assertEquals(0L, yesterday.todayMsOn("2026-10-17"))
        val next = yesterday.withBreath(5_000, "2026-10-17")
        assertEquals(BreathStats(15_000, 2, "2026-10-17", 5_000), next)
    }

    @Test
    fun emptiness_followsTheTotals() {
        assertTrue(BreathStats().isEmpty)
        assertEquals(false, BreathStats().withBreath(1_000, "2026-10-16").isEmpty)
    }

    @Test
    fun localDayKey_usesTheLocalZone() {
        val epoch = 1_792_000_000_000L // 2026-10-14T17:46:40Z
        assertEquals("2026-10-14", localDayKey(epoch, ZoneId.of("UTC")))
        assertEquals("2026-10-15", localDayKey(epoch, ZoneId.of("Pacific/Auckland")))
    }
}
