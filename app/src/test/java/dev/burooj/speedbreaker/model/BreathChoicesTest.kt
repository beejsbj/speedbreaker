package dev.burooj.speedbreaker.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BreathChoicesTest {
    @Test
    fun eachEndingIsCountedOnceAndTheyAddUpToTheBreaths() {
        val choices = BreathEnding.entries.fold(BreathChoices()) { sum, ending ->
            sum.withBreath(10_000, "2026-11-12", ending)
        }.withBreath(10_000, "2026-11-12", BreathEnding.Left)
        assertEquals(2L, choices.left)
        assertEquals(1L, choices.continued)
        assertEquals(1L, choices.detoured)
        assertEquals(1L, choices.timeOff)
        assertEquals(1L, choices.other)
        assertEquals(6L, choices.total)
        BreathEnding.entries.forEach { assertEquals(if (it == BreathEnding.Left) 2L else 1L, choices.count(it)) }
    }

    @Test
    fun countingStartsOnTheDayOfTheFirstBreathAndStaysThere() {
        val choices = BreathChoices()
            .withBreath(8_000, "2026-11-12", BreathEnding.Left)
            .withBreath(8_000, "2026-11-20", BreathEnding.Left)
        assertEquals("2026-11-12", choices.since)
    }

    @Test
    fun aDetourIsTalliedByWhereItWentAndOnlyWhenOneWasTaken() {
        val choices = BreathChoices()
            .withBreath(8_000, "2026-11-12", BreathEnding.Detoured, "kindle")
            .withBreath(8_000, "2026-11-12", BreathEnding.Detoured, "kindle")
            .withBreath(8_000, "2026-11-12", BreathEnding.Detoured, "notes")
            // A destination that is no longer chosen is counted as a detour, but not by name.
            .withBreath(8_000, "2026-11-12", BreathEnding.Detoured, null)
            // A destination passed with any other ending is ignored.
            .withBreath(8_000, "2026-11-12", BreathEnding.Left, "maps")
        assertEquals(4L, choices.detoured)
        assertEquals(mapOf("kindle" to 2L, "notes" to 1L), choices.detours)
    }

    @Test
    fun anAppThatStopsBeingADetourLosesItsCount() {
        val choices = BreathChoices(detoured = 3, detours = mapOf("kindle" to 2L, "notes" to 1L))
        assertEquals(mapOf("kindle" to 2L), choices.keepingDetours(setOf("kindle", "maps")).detours)
        // The total of detours taken is history, not a per-app figure, and stays.
        assertEquals(3L, choices.keepingDetours(emptySet()).detoured)
        assertSame(choices, choices.keepingDetours(setOf("kindle", "notes")))
    }

    @Test
    fun onlyTheSevenDaysEndingTodayAreKept() {
        var choices = BreathChoices()
        (1..10).forEach { day ->
            choices = choices.withBreath(day * 1_000L, "2026-11-%02d".format(day), BreathEnding.Continued)
        }
        assertEquals((4..10).map { "2026-11-%02d".format(it) }.toSet(), choices.days.keys)
        assertEquals(
            (4..10).map { "2026-11-%02d".format(it) to it * 1_000L },
            choices.week("2026-11-10"),
        )
    }

    @Test
    fun aDayAddsUpAndQuietDaysReadAsZero() {
        val choices = BreathChoices()
            .withBreath(8_000, "2026-11-10", BreathEnding.Left)
            .withBreath(4_000, "2026-11-12", BreathEnding.Left)
            .withBreath(6_000, "2026-11-12", BreathEnding.Continued)
        val week = choices.week("2026-11-12")
        assertEquals(7, week.size)
        assertEquals("2026-11-06" to 0L, week.first())
        assertEquals("2026-11-10" to 8_000L, week[4])
        assertEquals("2026-11-11" to 0L, week[5])
        assertEquals("2026-11-12" to 10_000L, week.last())
    }

    @Test
    fun aWeekReadOnALaterDayLeavesOlderDaysOut() {
        val choices = BreathChoices().withBreath(8_000, "2026-11-01", BreathEnding.Left)
        assertEquals(0L, choices.week("2026-11-12").sumOf { it.second })
    }

    @Test
    fun aClockSetBackDropsDaysThatWouldNowBeInTheFuture() {
        val choices = BreathChoices()
            .withBreath(8_000, "2026-11-12", BreathEnding.Left)
            .withBreath(5_000, "2026-11-03", BreathEnding.Left)
        assertEquals(mapOf("2026-11-03" to 5_000L), choices.days)
    }

    @Test
    fun everyAppThatIsADetourSomewhereCounts() {
        val settings = Settings(
            apps = mapOf(
                "target.a" to AppPolicy(redirects = listOf("notes", "maps")),
                "target.b" to AppPolicy(redirects = emptyList()),
                "target.c" to AppPolicy(),
            ),
            redirects = listOf("kindle", "notes"),
        )
        assertEquals(setOf("kindle", "notes", "maps"), settings.detourDestinations())
    }

    @Test
    fun aSetThatWouldNotBeOfferedAddsNothing() {
        val settings = Settings(
            apps = mapOf("target.a" to AppPolicy(redirects = listOf("target.a", "maps"))),
            redirects = listOf("kindle"),
        )
        assertEquals(setOf("kindle"), settings.detourDestinations())
    }
}
