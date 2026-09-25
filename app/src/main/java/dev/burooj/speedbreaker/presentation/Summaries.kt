package dev.burooj.speedbreaker.presentation

import dev.burooj.speedbreaker.model.AppPolicy
import dev.burooj.speedbreaker.model.Settings
import dev.burooj.speedbreaker.model.TimeWindow
import dev.burooj.speedbreaker.model.WeeklySchedule
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/* Plain-language readouts of settings, so rows can say what is true at a glance. */

internal val WEEKDAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

internal fun formatMinute(minute: Int, use24Hour: Boolean): String {
    if (minute >= 1440) return if (use24Hour) "24:00" else "Midnight"
    val time = LocalTime.of(minute / 60, minute % 60)
    val pattern = when {
        use24Hour -> "HH:mm"
        minute % 60 == 0 -> "h a"
        else -> "h:mm a"
    }
    return time.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

internal fun daysSummary(days: Set<Int>): String = when (days) {
    (1..7).toSet() -> "Every day"
    (1..5).toSet() -> "Weekdays"
    setOf(6, 7) -> "Weekends"
    else -> dayRuns(days.sorted()).joinToString(", ") { run ->
        if (run.size >= 3) "${WEEKDAY_NAMES[run.first() - 1]}–${WEEKDAY_NAMES[run.last() - 1]}"
        else run.joinToString(", ") { WEEKDAY_NAMES[it - 1] }
    }
}

private fun dayRuns(sorted: List<Int>): List<List<Int>> =
    sorted.fold(mutableListOf<MutableList<Int>>()) { runs, day ->
        if (runs.isNotEmpty() && runs.last().last() == day - 1) runs.last().add(day) else runs.add(mutableListOf(day))
        runs
    }

/** Lowercases a leading summary word ("Weekdays" → "weekdays") but never a day name. */
internal fun String.inSentence(): String =
    if (substringBefore(' ') in SENTENCE_WORDS) replaceFirstChar { it.lowercase() } else this

private val SENTENCE_WORDS = setOf("Always", "Weekdays", "Weekends", "Every", "No")

internal fun isAllDay(schedule: WeeklySchedule): Boolean =
    schedule.days.size == 7 && schedule.days.values.all { it == ALL_DAY }

internal fun scheduleSummary(schedule: WeeklySchedule?, use24Hour: Boolean = true): String = when {
    schedule == null || isAllDay(schedule) -> "Always on"
    schedule.days.isEmpty() -> "No active days"
    else -> {
        val windows = schedule.days.values.toSet()
        val days = daysSummary(schedule.days.keys)
        if (windows.size == 1) {
            val window = windows.single()
            "$days · ${formatMinute(window.startMinute, use24Hour)}–${formatMinute(window.endMinute, use24Hour)}"
        } else {
            "$days · varied hours"
        }
    }
}

internal fun intervalSummary(policy: AppPolicy): String =
    policy.continuousSeconds?.let { "Check-in every ${it / 60} min" } ?: "On opening only"

internal fun appSummary(policy: AppPolicy, settings: Settings, use24Hour: Boolean): String {
    val schedule = policy.schedule ?: settings.schedule
    return "${intervalSummary(policy)} · ${scheduleSummary(schedule, use24Hour).inSentence()}"
}

internal fun defaultsSummary(settings: Settings, use24Hour: Boolean): String {
    val redirects = if (settings.redirects.size == 4) "4 redirects" else "no redirects"
    val schedule = scheduleSummary(settings.schedule, use24Hour).inSentence()
    return "${settings.breathSeconds}-second breath · $schedule · $redirects"
}

internal fun allDaySchedule(): WeeklySchedule = WeeklySchedule((1..7).associateWith { ALL_DAY })

/** Where a new custom schedule starts: an editable, visible weekday window. */
internal fun startingSchedule(): WeeklySchedule = WeeklySchedule((1..5).associateWith { DEFAULT_WINDOW })

private val ALL_DAY = TimeWindow(startMinute = 0, endMinute = 1440)
