package dev.burooj.speedbreaker.model

import java.time.LocalDate

/** How a counted breath ended, in the words Home uses for it. */
internal enum class BreathEnding {
    /** The Leave button, or Home, Back or another app once the first eight seconds allow it. */
    Left,
    Continued,
    Detoured,

    /** Pause 15 min: breaths off for that one app for a while. */
    TimeOff,

    /** Nothing the person did: a call, the lock screen, a schedule or settings change. */
    Other,
}

/**
 * What followed the breaths, kept beside [BreathStats] and counted only from the version that
 * introduced it ([since] is the first local day it counted). Running counts and seven daily
 * totals: never a list of breaths, a time of day, or the app a breath was for.
 */
internal data class BreathChoices(
    val since: String = "",
    val left: Long = 0,
    val continued: Long = 0,
    val detoured: Long = 0,
    val timeOff: Long = 0,
    val other: Long = 0,
    /** Detour destination package to times taken. Only apps still chosen as a detour are kept. */
    val detours: Map<String, Long> = emptyMap(),
    /** Local ISO day to milliseconds breathed, for the seven days ending on the latest breath. */
    val days: Map<String, Long> = emptyMap(),
) {
    val total: Long get() = left + continued + detoured + timeOff + other

    fun count(ending: BreathEnding): Long = when (ending) {
        BreathEnding.Left -> left
        BreathEnding.Continued -> continued
        BreathEnding.Detoured -> detoured
        BreathEnding.TimeOff -> timeOff
        BreathEnding.Other -> other
    }

    /**
     * Adds one counted breath of [shownMs] that ended as [ending] on the local day [today].
     * [detour] is the destination taken, when it is still one of the chosen detours.
     */
    fun withBreath(shownMs: Long, today: String, ending: BreathEnding, detour: String? = null): BreathChoices {
        val counted = when (ending) {
            BreathEnding.Left -> copy(left = left + 1)
            BreathEnding.Continued -> copy(continued = continued + 1)
            BreathEnding.Detoured -> copy(detoured = detoured + 1)
            BreathEnding.TimeOff -> copy(timeOff = timeOff + 1)
            BreathEnding.Other -> copy(other = other + 1)
        }
        return counted.copy(
            since = since.ifEmpty { today },
            detours = if (ending == BreathEnding.Detoured && detour != null) {
                detours + (detour to (detours[detour] ?: 0L) + 1)
            } else {
                detours
            },
        ).withinWeekOf(today).let { week -> week.copy(days = week.days + (today to (week.days[today] ?: 0L) + shownMs)) }
    }

    /** Forgets every day total outside the seven local days ending on [today]. */
    fun withinWeekOf(today: String): BreathChoices {
        val kept = weekOf(today).toSet()
        return if (days.keys.all { it in kept }) this else copy(days = days.filterKeys { it in kept })
    }

    /** Forgets the count of any app that is no longer a detour anywhere. */
    fun keepingDetours(chosen: Set<String>): BreathChoices =
        if (detours.keys.all { it in chosen }) this else copy(detours = detours.filterKeys { it in chosen })

    /** The seven local days ending on [today], oldest first, with the milliseconds breathed on each. */
    fun week(today: String): List<Pair<String, Long>> = weekOf(today).map { it to (days[it] ?: 0L) }
}

internal const val WEEK_DAYS = 7

/** ISO keys for the seven local days ending on [today]; just [today] when it is not a date. */
internal fun weekOf(today: String): List<String> {
    val last = runCatching { LocalDate.parse(today) }.getOrNull() ?: return listOf(today)
    return (WEEK_DAYS - 1 downTo 0).map { last.minusDays(it.toLong()).toString() }
}
