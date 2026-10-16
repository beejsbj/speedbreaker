package dev.burooj.speedbreaker.presentation

import dev.burooj.speedbreaker.model.BreathStats

/** "45 s", "18 min", "1 h 12 min": whole units, rounded, never a clock. */
internal fun formatBreathDuration(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0) + 500) / 1_000
    if (seconds < 60) return "$seconds s"
    // Round to the nearest minute first, so 59 min 40 s reads "1 h", not "60 min".
    val minutes = (seconds + 30) / 60
    if (minutes < 60) return "$minutes min"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0L) "$hours h" else "$hours h $rest min"
}

internal fun totalBreathedLine(stats: BreathStats): String {
    val breaths = if (stats.totalBreaths == 1L) "1 breath" else "${stats.totalBreaths} breaths"
    return "You’ve breathed ${formatBreathDuration(stats.totalMs)} · $breaths"
}

internal fun todayBreathedLine(stats: BreathStats, today: String): String {
    val todayMs = stats.todayMsOn(today)
    return if (todayMs <= 0L) "Today: none yet" else "Today: ${formatBreathDuration(todayMs)}"
}
