package dev.burooj.speedbreaker.presentation

import dev.burooj.speedbreaker.model.BreathStats
import org.junit.Assert.assertEquals
import org.junit.Test

class BreathStatsTextTest {
    @Test
    fun duration_usesWholeHumanUnits() {
        assertEquals("0 s", formatBreathDuration(0))
        assertEquals("45 s", formatBreathDuration(45_000))
        assertEquals("45 s", formatBreathDuration(44_600))
        assertEquals("1 min", formatBreathDuration(59_600))
        assertEquals("18 min", formatBreathDuration(18 * 60_000L))
        assertEquals("18 min", formatBreathDuration(18 * 60_000L + 20_000))
        assertEquals("19 min", formatBreathDuration(18 * 60_000L + 30_000))
        assertEquals("59 min", formatBreathDuration(59 * 60_000L))
        assertEquals("1 h", formatBreathDuration(59 * 60_000L + 40_000))
        assertEquals("1 h 12 min", formatBreathDuration(72 * 60_000L))
        assertEquals("3 h", formatBreathDuration(3 * 3_600_000L))
    }

    @Test
    fun totalLine_namesBreathsAndSingular() {
        assertEquals(
            "You’ve breathed 18 min · 94 breaths",
            totalBreathedLine(BreathStats(18 * 60_000L, 94, "2026-10-16", 0)),
        )
        assertEquals("You’ve breathed 8 s · 1 breath", totalBreathedLine(BreathStats(8_000, 1)))
    }

    @Test
    fun todayLine_readsZeroAfterMidnight() {
        val stats = BreathStats(600_000, 5, "2026-10-16", 120_000)
        assertEquals("Today: 2 min", todayBreathedLine(stats, "2026-10-16"))
        assertEquals("Today: none yet", todayBreathedLine(stats, "2026-10-17"))
    }
}
