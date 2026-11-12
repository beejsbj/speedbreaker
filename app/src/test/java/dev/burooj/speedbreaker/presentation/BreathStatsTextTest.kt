package dev.burooj.speedbreaker.presentation

import dev.burooj.speedbreaker.model.AppPolicy
import dev.burooj.speedbreaker.model.BreathChoices
import dev.burooj.speedbreaker.model.BreathEnding
import dev.burooj.speedbreaker.model.BreathStats
import dev.burooj.speedbreaker.model.Settings
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class BreathStatsTextTest {
    private val originalLocale = Locale.getDefault()

    @Before
    fun useEnglish() = Locale.setDefault(Locale.US)

    @After
    fun restoreLocale() = Locale.setDefault(originalLocale)

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
    fun figuresAreANumberAndASmallUnit() {
        assertEquals(listOf(FigurePart("0", "sec")), breathFigure(0))
        assertEquals(listOf(FigurePart("42", "sec")), breathFigure(42_000))
        assertEquals(listOf(FigurePart("18", "min")), breathFigure(18 * 60_000L))
        assertEquals(listOf(FigurePart("1", "h")), breathFigure(59 * 60_000L + 40_000))
        assertEquals(listOf(FigurePart("1", "h"), FigurePart("12", "min")), breathFigure(72 * 60_000L))
        assertEquals(listOf(FigurePart("1,240", "h"), FigurePart("5", "min")), breathFigure(1_240 * 3_600_000L + 5 * 60_000L))
        assertEquals("1,240", formatCount(1_240))
    }

    @Test
    fun theFiguresAreReadAloudAsWords() {
        assertEquals(
            "18 minutes breathed in total. 94 breaths, 2 minutes today.",
            breathedDescription(BreathStats(18 * 60_000L, 94, "2026-10-16", 120_000), "2026-10-16"),
        )
        assertEquals(
            "10 minutes breathed in total. 1 breath, none yet today.",
            breathedDescription(BreathStats(600_000, 1, "2026-10-16", 120_000), "2026-10-17"),
        )
    }

    @Test
    fun endingsKeepOneOrderAndAnotherWayOnlyShowsOnceItHappens() {
        val choices = BreathChoices(left = 41, continued = 38, detoured = 12, timeOff = 3)
        assertEquals(
            listOf("Left" to 41L, "Continued" to 38L, "Took a detour" to 12L, "Took 15 min off" to 3L),
            endingCounts(choices, everyEnding = false).map { it.first.label() to it.second },
        )
        assertEquals(
            listOf("Left", "Continued", "Took a detour", "Took 15 min off", "Ended another way"),
            endingCounts(choices, everyEnding = true).map { it.first.label() },
        )
        assertEquals(
            BreathEnding.Other to 2L,
            endingCounts(choices.copy(other = 2), everyEnding = false).last(),
        )
        // Nothing chosen yet still lists the four choices, at zero.
        assertEquals(4, endingCounts(BreathChoices(), everyEnding = false).size)
    }

    @Test
    fun detoursListEveryChosenAppMostTakenFirstThenByName() {
        val settings = Settings(
            apps = mapOf("target" to AppPolicy(redirects = listOf("signal", "spotify"))),
            redirects = listOf("notes", "kindle", "maps"),
        )
        val choices = BreathChoices(detoured = 9, detours = mapOf("kindle" to 6L, "notes" to 3L, "gone" to 4L))
        val labels = mapOf("kindle" to "Kindle", "notes" to "Notes", "maps" to "Maps", "signal" to "Signal", "spotify" to "Spotify")
        assertEquals(
            listOf("Kindle" to 6L, "Notes" to 3L, "Maps" to 0L, "Signal" to 0L, "Spotify" to 0L),
            detourTallies(settings, choices) { labels[it] ?: it }.map { it.label to it.count },
        )
        assertEquals(emptyList<DetourTally>(), detourTallies(Settings(), choices) { it })
    }

    @Test
    fun theWeekIsSevenDaysOldestFirstWithTodayMarked() {
        // 2026-11-12 is a Thursday.
        val choices = BreathChoices(days = mapOf("2026-11-10" to 60_000L, "2026-11-12" to 120_000L))
        val bars = weekBars(choices, "2026-11-12")
        assertEquals(listOf("F", "S", "S", "M", "T", "W", "T"), bars.map { it.letter })
        assertEquals(listOf(0L, 0L, 0L, 0L, 60_000L, 0L, 120_000L), bars.map { it.ms })
        assertEquals(listOf(false, false, false, false, false, false, true), bars.map { it.isToday })
        assertEquals(
            "Time breathed each day. Fri none, Sat none, Sun none, Mon none, Tue 1 minute, Wed none, Today 2 minutes",
            weekDescription(bars),
        )
    }

    @Test
    fun countingSinceIsADateOrNothingBeforeTheFirstBreath() {
        assertEquals("12 November 2026", countedSince(BreathChoices(since = "2026-11-12")))
        assertEquals(null, countedSince(BreathChoices()))
    }

    @Test
    fun compactLine_isOneQuietSentence() {
        assertEquals("18 minutes breathed so far.", compactBreathLine(BreathStats(18 * 60_000L, 94)))
        assertEquals("1 hour 12 minutes breathed so far.", compactBreathLine(BreathStats(72 * 60_000L, 300)))
    }
}
