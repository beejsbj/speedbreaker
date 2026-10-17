package dev.burooj.speedbreaker.presentation

import dev.burooj.speedbreaker.model.Settings
import dev.burooj.speedbreaker.model.isActiveAt
import dev.burooj.speedbreaker.model.nextStartAfter
import java.time.Instant
import java.time.ZoneId

/**
 * When every chosen app is outside its effective schedule, the soonest start among them ("9 AM",
 * or "Mon 9 AM" when it is not today). Null when any app is active now, none is chosen, or no
 * schedule ever starts again.
 */
internal fun restingUntil(settings: Settings, nowEpochMs: Long, zone: ZoneId, use24Hour: Boolean): String? {
    if (settings.apps.isEmpty()) return null
    val now = Instant.ofEpochMilli(nowEpochMs).atZone(zone)
    val schedules = settings.apps.values.map { it.schedule ?: settings.schedule }
    if (schedules.any { it.isActiveAt(now) }) return null
    val next = schedules.mapNotNull { it.nextStartAfter(now) }.minByOrNull { it.toInstant() } ?: return null
    val time = formatMinute(next.hour * 60 + next.minute, use24Hour)
    return if (next.toLocalDate() == now.toLocalDate()) time else "${WEEKDAY_NAMES[next.dayOfWeek.value - 1]} $time"
}

/**
 * The note after "On" while Speedbreaker is able to breathe. Resting is the current truth, so it
 * leads; blocked notifications only stop Pause, and the Reliability row marks them.
 */
internal fun homeStatusNote(restingUntil: String?, notificationsAllowed: Boolean): String? = when {
    restingUntil != null -> "resting until $restingUntil"
    !notificationsAllowed -> "pauses need notifications"
    else -> null
}

internal fun homeStatusText(note: String?): String = if (note == null) "On" else "On · $note"

/** What TalkBack hears: the dot is decoration, so the words carry the meaning. */
internal fun homeStatusDescription(note: String?): String =
    if (note == null) "Speedbreaker is on" else "Speedbreaker is on, $note"
