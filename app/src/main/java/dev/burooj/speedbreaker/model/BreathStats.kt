package dev.burooj.speedbreaker.model

import java.time.Instant
import java.time.ZoneId

/** A breath shorter than this was a transient restart, not a breath someone took. */
internal const val MIN_COUNTED_BREATH_MS = 1_000L

/**
 * The only breathing record: running totals, never a list. [todayMs] belongs to [todayKey]
 * (a local ISO date, as pause days use) and reads as zero on any other day.
 */
internal data class BreathStats(
    val totalMs: Long = 0,
    val totalBreaths: Long = 0,
    val todayKey: String = "",
    val todayMs: Long = 0,
) {
    val isEmpty: Boolean get() = totalBreaths == 0L && totalMs == 0L

    fun todayMsOn(today: String): Long = if (todayKey == today) todayMs else 0

    /** Adds one counted breath of [shownMs], starting a fresh "today" after local midnight. */
    fun withBreath(shownMs: Long, today: String): BreathStats = copy(
        totalMs = totalMs + shownMs,
        totalBreaths = totalBreaths + 1,
        todayKey = today,
        todayMs = todayMsOn(today) + shownMs,
    )
}

/**
 * What one finished breath adds: the time it was on screen, capped at its configured length,
 * or null when it was shown too briefly to count.
 */
internal fun countedBreathMs(shownMs: Long, breathSeconds: Int): Long? =
    minOf(shownMs, breathSeconds * 1_000L).takeIf { it >= MIN_COUNTED_BREATH_MS }

/** The local-day key shared with pause days. */
internal fun localDayKey(epochMs: Long, zoneId: ZoneId): String =
    Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate().toString()
