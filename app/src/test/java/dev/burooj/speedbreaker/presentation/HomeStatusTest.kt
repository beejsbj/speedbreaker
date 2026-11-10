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
    fun statusNote_putsRestingAheadOfNotifications() {
        assertNull(homeStatusNote(null, notificationsAllowed = true))
        assertEquals("pauses need notifications", homeStatusNote(null, notificationsAllowed = false))
        assertEquals("resting until 9 AM", homeStatusNote("9 AM", notificationsAllowed = false))
    }

    @Test
    fun statusTextAndDescription() {
        assertEquals("On", homeStatusText(null))
        assertEquals("On · resting until 9 AM", homeStatusText("resting until 9 AM"))
        assertEquals("Speedbreaker is on", homeStatusDescription(null))
        assertEquals("Speedbreaker is on, pauses need notifications", homeStatusDescription("pauses need notifications"))
    }

    private fun setup(protection: Protection, phoneAllowed: Boolean = false, hasApps: Boolean = false, hasBreathed: Boolean = false) =
        setupProgress(protection, phoneAllowed, hasApps, hasBreathed)

    @Test
    fun `setup counts down the steps a new person has left`() {
        val fresh = setup(Protection.Off)!!
        assertEquals(1, fresh.current)
        assertEquals("Three steps to start", setupTitle(fresh))

        val serviceOn = setup(Protection.CallPermission)!!
        assertEquals(2, serviceOn.current)
        assertEquals("Two steps left", setupTitle(serviceOn))

        val phoneAllowed = setup(Protection.On, phoneAllowed = true)!!
        assertEquals(3, phoneAllowed.current)
        assertEquals("One step left", setupTitle(phoneAllowed))
    }

    @Test
    fun `steps done out of order are already ticked`() {
        val appsFirst = setup(Protection.Off, hasApps = true)!!
        assertEquals(1, appsFirst.current)
        assertEquals("Two steps left", setupTitle(appsFirst))
        assertEquals(2, setup(Protection.CallPermission, hasApps = true)!!.current)

        // Phone allowed, then the service switched off again before a first breath.
        val phoneKept = setup(Protection.Off, phoneAllowed = true, hasApps = true)!!
        assertEquals(1, phoneKept.current)
        assertEquals("One step left", setupTitle(phoneKept))
    }

    @Test
    fun `setup ends when everything is done and gives way to repairs once a breath is counted`() {
        assertNull(setup(Protection.On, phoneAllowed = true, hasApps = true))
        assertNull(setup(Protection.Off, hasApps = true, hasBreathed = true))
        assertNull(setup(Protection.CallPermission, hasBreathed = true))
        assertNull(setup(Protection.On, phoneAllowed = true, hasBreathed = true))
    }

    @Test
    fun `problems that are not setup steps keep their own words`() {
        assertNull(setup(Protection.Broken))
        assertNull(setup(Protection.CallUnavailable))
        assertNull(setup(Protection.CallActive, phoneAllowed = true))
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
