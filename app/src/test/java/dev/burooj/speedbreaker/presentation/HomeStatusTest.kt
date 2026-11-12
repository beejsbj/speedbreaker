package dev.burooj.speedbreaker.presentation

import dev.burooj.speedbreaker.model.AppPolicy
import dev.burooj.speedbreaker.model.Settings
import dev.burooj.speedbreaker.model.TimeWindow
import dev.burooj.speedbreaker.model.WeeklySchedule
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class HomeStatusTest {
    private val originalLocale = Locale.getDefault()

    @Before
    fun useEnglishClock() = Locale.setDefault(Locale.US)

    @After
    fun restoreLocale() = Locale.setDefault(originalLocale)

    private val zone = ZoneId.of("America/Toronto")
    private val weekdays9to5 = WeeklySchedule((1..5).associateWith { TimeWindow(9 * 60, 17 * 60) })

    // 2026-10-19 is a Monday.
    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 10, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun settings(vararg policies: AppPolicy, schedule: WeeklySchedule? = weekdays9to5) =
        Settings(apps = policies.mapIndexed { i, p -> "app.$i" to p }.toMap(), schedule = schedule)

    private fun resting(settings: Settings, now: Long, use24Hour: Boolean = false) =
        restingUntil(settings, now, zone, use24Hour)

    @Test
    fun restsUntilTodaysStartBeforeHours() {
        assertEquals("9 AM", resting(settings(AppPolicy()), at(19, 6, 30)))
    }

    @Test
    fun restsUntilNamedDayAfterHoursEnd() {
        assertEquals("Tue 9 AM", resting(settings(AppPolicy()), at(19, 17)))
        assertEquals("Mon 9 AM", resting(settings(AppPolicy()), at(23, 18)))
    }

    @Test
    fun usesTheUsersClockFormat() {
        assertEquals("09:00", resting(settings(AppPolicy()), at(19, 6), use24Hour = true))
    }

    @Test
    fun isNullWhileAnyAppIsActive() {
        val custom = AppPolicy(schedule = WeeklySchedule((1..7).associateWith { TimeWindow(0, 1440) }))
        assertNull(resting(settings(AppPolicy()), at(19, 10)))
        assertNull(resting(settings(AppPolicy(), custom), at(19, 6)))
    }

    @Test
    fun isNullForAlwaysOnAndForNoApps() {
        assertNull(resting(settings(AppPolicy(), schedule = null), at(19, 6)))
        assertNull(resting(settings(schedule = weekdays9to5), at(19, 6)))
    }

    @Test
    fun picksTheSoonestStartAcrossApps() {
        val evenings = AppPolicy(schedule = WeeklySchedule((1..7).associateWith { TimeWindow(18 * 60, 23 * 60) }))
        assertEquals("9 AM", resting(settings(AppPolicy(), evenings), at(19, 6)))
        // After 5 PM the evening-only app is the sooner start, and it is the same day.
        val sevenToTen = AppPolicy(schedule = WeeklySchedule((1..7).associateWith { TimeWindow(19 * 60, 22 * 60) }))
        assertEquals("7 PM", resting(settings(AppPolicy(), sevenToTen), at(19, 17, 30)))
    }

    @Test
    fun anOvernightWindowStillCountsAsActiveAfterMidnight() {
        val overnight = AppPolicy(schedule = WeeklySchedule(mapOf(1 to TimeWindow(22 * 60, 6 * 60))))
        // Tuesday 01:00 is inside Monday's 22:00-06:00 window.
        assertNull(resting(settings(overnight), at(20, 1)))
        assertEquals("Mon 10 PM", resting(settings(overnight), at(20, 7)))
    }

    @Test
    fun isNullWhenNoScheduleEverStarts() {
        assertNull(resting(settings(AppPolicy(), schedule = WeeklySchedule()), at(19, 6)))
    }

    @Test
    fun thePillSaysActiveWithTheAppCountOrWhenItRestsUntil() {
        assertEquals("Active · 3 apps", statusPillText(3, null))
        assertEquals("Active · 1 app", statusPillText(1, null))
        assertEquals("Resting until 9 AM", statusPillText(3, "9 AM"))
    }

    @Test
    fun thePillIsDescribedInWords() {
        assertEquals("Speedbreaker is active on 3 apps", statusPillDescription(3, null))
        assertEquals("Speedbreaker is on, resting until 9 AM", statusPillDescription(1, "9 AM"))
    }

    @Test
    fun blockedNotificationsAreAQuietLineAndWaitWhileResting() {
        assertNull(pauseNote(null, notificationsAllowed = true))
        assertEquals(
            "Pause 15 min needs notifications. Allow them in Settings.",
            pauseNote(null, notificationsAllowed = false),
        )
        assertNull(pauseNote("9 AM", notificationsAllowed = false))
    }

    private fun setup(protection: Protection, hasApps: Boolean = false, hasBreathed: Boolean = false) =
        setupProgress(protection, hasApps, hasBreathed)

    @Test
    fun `setup counts down the steps a new person has left`() {
        val fresh = setup(Protection.Off)!!
        assertEquals(1, fresh.current)
        assertEquals("Two steps to start", setupTitle(fresh))

        val serviceOn = setup(Protection.On)!!
        assertEquals(2, serviceOn.current)
        assertEquals("One step left", setupTitle(serviceOn))
    }

    @Test
    fun `apps chosen before the service is on are already ticked`() {
        val appsFirst = setup(Protection.Off, hasApps = true)!!
        assertEquals(1, appsFirst.current)
        assertEquals("One step left", setupTitle(appsFirst))
    }

    @Test
    fun `setup ends when everything is done and gives way to repairs once a breath is counted`() {
        assertNull(setup(Protection.On, hasApps = true))
        assertNull(setup(Protection.Off, hasApps = true, hasBreathed = true))
        assertNull(setup(Protection.On, hasBreathed = true))
    }

    @Test
    fun `problems that are not setup steps keep their own words`() {
        assertNull(setup(Protection.Broken))
        assertNull(setup(Protection.CallUnavailable))
        assertNull(setup(Protection.CallActive))
    }

    private fun withheld(
        granted: Boolean = false,
        couldAskBefore: Boolean = false,
        mayAskAgain: Boolean = false,
        answeredAfterMs: Long = 3_000,
        earlierSilentRefusals: Int = 0,
    ) = promptWasWithheld(granted, couldAskBefore, mayAskAgain, answeredAfterMs, earlierSilentRefusals)

    @Test
    fun `the final refusal sends someone to app info at once`() {
        // Android could ask before this request and cannot now: that was the second "Don't allow".
        assertEquals(true, withheld(couldAskBefore = true))
    }

    @Test
    fun `a refusal Android made without asking sends someone to app info`() {
        assertEquals(true, withheld(answeredAfterMs = 40))
        assertEquals(true, withheld(answeredAfterMs = 499))
        // A slow phone can take longer than the threshold; the second such answer settles it.
        assertEquals(false, withheld(answeredAfterMs = 500))
        assertEquals(true, withheld(answeredAfterMs = 900, earlierSilentRefusals = 1))
    }

    @Test
    fun `a prompt that can still be shown is asked again`() {
        // Read and dismissed for the first time.
        assertEquals(false, withheld(answeredAfterMs = 3_000))
        // Refused once: Android will show it again.
        assertEquals(false, withheld(mayAskAgain = true, answeredAfterMs = 40))
        assertEquals(false, withheld(granted = true, couldAskBefore = true, answeredAfterMs = 40))
    }
}
