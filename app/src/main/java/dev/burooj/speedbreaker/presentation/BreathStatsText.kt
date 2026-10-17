package dev.burooj.speedbreaker.presentation

import dev.burooj.speedbreaker.model.BreathStats

/** "12 seconds", "18 minutes", "1 hour 12 minutes": whole units, rounded, never a clock. */
internal fun formatBreathTotal(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0) + 500) / 1_000
    if (seconds < 60) return plural(seconds, "second")
    // Round to the nearest minute first, so 59 min 40 s reads "1 hour", not "60 minutes".
    val minutes = (seconds + 30) / 60
    if (minutes < 60) return plural(minutes, "minute")
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0L) plural(hours, "hour") else "${plural(hours, "hour")} ${plural(rest, "minute")}"
}

private fun plural(count: Long, unit: String): String = if (count == 1L) "1 $unit" else "$count ${unit}s"

/** "94 breaths · 2 minutes today", or "none yet today" before the first breath of the day. */
internal fun breathSubline(stats: BreathStats, today: String): String {
    val todayMs = stats.todayMsOn(today)
    val todayPart = if (todayMs <= 0L) "none yet today" else "${formatBreathTotal(todayMs)} today"
    return "${plural(stats.totalBreaths, "breath")} · $todayPart"
}

/** The one quiet line Home keeps when a repair notice leads the page. */
internal fun compactBreathLine(stats: BreathStats): String = "${formatBreathTotal(stats.totalMs)} breathed so far."
