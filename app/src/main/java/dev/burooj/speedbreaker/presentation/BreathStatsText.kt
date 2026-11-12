package dev.burooj.speedbreaker.presentation

import dev.burooj.speedbreaker.model.BreathChoices
import dev.burooj.speedbreaker.model.BreathEnding
import dev.burooj.speedbreaker.model.BreathStats
import dev.burooj.speedbreaker.model.Settings
import dev.burooj.speedbreaker.model.detourDestinations
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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

/** The one quiet line Home keeps when a repair notice leads the page. */
internal fun compactBreathLine(stats: BreathStats): String = "${formatBreathTotal(stats.totalMs)} breathed so far."

/* ---------- Figures: a number set alone, its unit a small label beside it ---------- */

internal data class FigurePart(val value: String, val unit: String = "")

/** "18 min", "42 sec", "1 h 12 min": the rounding of [formatBreathTotal], as figures. */
internal fun breathFigure(ms: Long): List<FigurePart> {
    val seconds = (ms.coerceAtLeast(0) + 500) / 1_000
    if (seconds < 60) return listOf(FigurePart("$seconds", "sec"))
    val minutes = (seconds + 30) / 60
    if (minutes < 60) return listOf(FigurePart("$minutes", "min"))
    val hours = minutes / 60
    val rest = minutes % 60
    return listOfNotNull(FigurePart(formatCount(hours), "h"), FigurePart("$rest", "min").takeIf { rest != 0L })
}

/** "94", "1,240": a count with the locale's grouping. */
internal fun formatCount(count: Long): String = NumberFormat.getIntegerInstance().format(count)

/** What TalkBack hears for the header's three figures. */
internal fun breathedDescription(stats: BreathStats, today: String): String {
    val todayMs = stats.todayMsOn(today)
    val todayPart = if (todayMs <= 0L) "none yet today" else "${formatBreathTotal(todayMs)} today"
    return "${formatBreathTotal(stats.totalMs)} breathed in total. ${plural(stats.totalBreaths, "breath")}, $todayPart."
}

/* ---------- What followed a breath ---------- */

/** Plain past-tense words, in the order they are always shown. No ending is better than another. */
internal val ENDING_ORDER = listOf(
    BreathEnding.Left,
    BreathEnding.Continued,
    BreathEnding.Detoured,
    BreathEnding.TimeOff,
    BreathEnding.Other,
)

internal fun BreathEnding.label(): String = when (this) {
    BreathEnding.Left -> "Left"
    BreathEnding.Continued -> "Continued"
    BreathEnding.Detoured -> "Took a detour"
    BreathEnding.TimeOff -> "Took 15 min off"
    BreathEnding.Other -> "Ended another way"
}

/**
 * The endings a summary lists with their counts. The four choices always show, so an empty one
 * reads as zero; "another way" only once it has happened, and always on the full page.
 */
internal fun endingCounts(choices: BreathChoices, everyEnding: Boolean): List<Pair<BreathEnding, Long>> =
    ENDING_ORDER
        .map { it to choices.count(it) }
        .filter { (ending, count) -> everyEnding || ending != BreathEnding.Other || count > 0 }

/** One chosen detour and how often it was taken since counting began. */
internal data class DetourTally(val packageName: String, val label: String, val count: Long)

/** Every app that is a detour somewhere, most taken first, then by name. An untaken one reads zero. */
internal fun detourTallies(settings: Settings, choices: BreathChoices, label: (String) -> String): List<DetourTally> =
    settings.detourDestinations()
        .map { DetourTally(it, label(it), choices.detours[it] ?: 0L) }
        .sortedWith(compareByDescending<DetourTally> { it.count }.thenBy { it.label.lowercase() })

/** How many detours Home names before "+N more". */
internal const val HOME_DETOUR_CHIPS = 3

/** One day of the week on the full page: its initial under the bar, its full name read aloud. */
internal data class DayBar(val letter: String, val name: String, val ms: Long, val isToday: Boolean)

private val WEEKDAY_FULL_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

/** The seven days ending [today], oldest first. */
internal fun weekBars(choices: BreathChoices, today: String): List<DayBar> = choices.week(today).map { (day, ms) ->
    val name = runCatching { WEEKDAY_FULL_NAMES[LocalDate.parse(day).dayOfWeek.value - 1] }.getOrDefault("")
    DayBar(letter = name.take(1), name = name, ms = ms, isToday = day == today)
}

internal fun weekDescription(bars: List<DayBar>): String = bars.joinToString(", ", prefix = "Time breathed each day. ") {
    "${if (it.isToday) "Today" else it.name} ${if (it.ms <= 0L) "none" else formatBreathTotal(it.ms)}"
}

/** "12 November 2026" for the day counting began, or null before the first counted breath. */
internal fun countedSince(choices: BreathChoices): String? = runCatching {
    LocalDate.parse(choices.since).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()))
}.getOrNull()
