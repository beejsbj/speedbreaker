package dev.burooj.speedbreaker.presentation

import dev.burooj.speedbreaker.model.BreathStats
import org.junit.Assert.assertEquals
import org.junit.Test

class BreathStatsTextTest {
    @Test
    fun total_usesWholeHumanUnitsInWords() {
        assertEquals("0 seconds", formatBreathTotal(0))
        assertEquals("1 second", formatBreathTotal(1_000))
        assertEquals("12 seconds", formatBreathTotal(12_000))
        assertEquals("45 seconds", formatBreathTotal(44_600))
        assertEquals("1 minute", formatBreathTotal(59_600))
        assertEquals("18 minutes", formatBreathTotal(18 * 60_000L))
        assertEquals("18 minutes", formatBreathTotal(18 * 60_000L + 20_000))
        assertEquals("19 minutes", formatBreathTotal(18 * 60_000L + 30_000))
        assertEquals("59 minutes", formatBreathTotal(59 * 60_000L))
    }

    @Test
    fun total_joinsHoursAndMinutesAndDropsZeroMinutes() {
        assertEquals("1 hour", formatBreathTotal(59 * 60_000L + 40_000))
        assertEquals("1 hour 1 minute", formatBreathTotal(61 * 60_000L))
        assertEquals("1 hour 12 minutes", formatBreathTotal(72 * 60_000L))
        assertEquals("2 hours", formatBreathTotal(2 * 3_600_000L))
        assertEquals("3 hours 5 minutes", formatBreathTotal(3 * 3_600_000L + 5 * 60_000L))
    }

    @Test
    fun subline_namesBreathsAndTodayWithSingulars() {
        assertEquals(
            "94 breaths · 2 minutes today",
            breathSubline(BreathStats(18 * 60_000L, 94, "2026-10-16", 120_000), "2026-10-16"),
        )
        assertEquals(
            "1 breath · 12 seconds today",
            breathSubline(BreathStats(12_000, 1, "2026-10-16", 12_000), "2026-10-16"),
        )
    }

    @Test
    fun subline_readsNoneYetAfterMidnight() {
        val stats = BreathStats(600_000, 94, "2026-10-16", 120_000)
        assertEquals("94 breaths · none yet today", breathSubline(stats, "2026-10-17"))
    }

    @Test
    fun compactLine_isOneQuietSentence() {
        assertEquals("18 minutes breathed so far.", compactBreathLine(BreathStats(18 * 60_000L, 94)))
        assertEquals("1 hour 12 minutes breathed so far.", compactBreathLine(BreathStats(72 * 60_000L, 300)))
    }
}
