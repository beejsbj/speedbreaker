package dev.burooj.speedbreaker.model

import java.time.LocalTime
import java.time.ZonedDateTime

internal const val MINUTES_PER_HOUR = 60
internal const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

internal fun TimeWindow.isValid(): Boolean =
    startMinute in 0 until MINUTES_PER_DAY &&
        endMinute in 0..MINUTES_PER_DAY &&
        startMinute != endMinute

/** Whether the schedule applies at [local]. Null means always; an overnight window runs into the next day. */
internal fun WeeklySchedule?.isActiveAt(local: ZonedDateTime): Boolean {
    if (this == null) return true
    val minute = local.hour * MINUTES_PER_HOUR + local.minute
    val today = local.dayOfWeek.value
    val yesterday = if (today == 1) 7 else today - 1

    val todayWindow = days[today]
    if (todayWindow != null && todayWindow.isValid()) {
        if (todayWindow.startMinute < todayWindow.endMinute) {
            if (minute >= todayWindow.startMinute && minute < todayWindow.endMinute) return true
        } else if (minute >= todayWindow.startMinute) {
            return true
        }
    }

    val yesterdayWindow = days[yesterday]
    return yesterdayWindow != null &&
        yesterdayWindow.isValid() &&
        yesterdayWindow.startMinute > yesterdayWindow.endMinute &&
        minute < yesterdayWindow.endMinute
}

/**
 * The next moment after [from] when a window of this schedule opens, or null when it never does
 * (an empty week, or only invalid windows). Null schedules are always on and never "start".
 */
internal fun WeeklySchedule?.nextStartAfter(from: ZonedDateTime): ZonedDateTime? {
    if (this == null) return null
    for (offset in 0L..7L) {
        val date = from.toLocalDate().plusDays(offset)
        val window = days[date.dayOfWeek.value]?.takeIf { it.isValid() } ?: continue
        val start = ZonedDateTime.of(date, LocalTime.of(window.startMinute / MINUTES_PER_HOUR, window.startMinute % MINUTES_PER_HOUR), from.zone)
        if (start.isAfter(from)) return start
    }
    return null
}
