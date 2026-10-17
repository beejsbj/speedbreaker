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
}
