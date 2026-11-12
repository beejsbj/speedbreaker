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
 * leads; blocked notifications only stop Pause, and Settings' Notifications row is where to allow them.
 */
internal fun homeStatusNote(restingUntil: String?, notificationsAllowed: Boolean): String? = when {
    restingUntil != null -> "resting until $restingUntil"
    !notificationsAllowed -> "Pause 15 min needs notifications"
    else -> null
}

internal fun homeStatusText(note: String?): String = if (note == null) "On" else "On · $note"

/** What TalkBack hears: the dot is decoration, so the words carry the meaning. */
internal fun homeStatusDescription(note: String?): String =
    if (note == null) "Speedbreaker is on" else "Speedbreaker is on, $note"

/** Which of the two first-time steps are done; [current] is the one to do next. */
internal data class SetupProgress(val accessibility: Boolean, val apps: Boolean) {
    val remaining: Int get() = listOf(accessibility, apps).count { !it }
    val current: Int get() = if (!accessibility) 1 else 2
}

/**
 * Setup progress while the breathing total is empty and a step is still open; null otherwise.
 * Once a breath has been counted the same states are repairs and keep their own words. Resetting
 * the total empties it again, so a repair soon after a reset reads as setup; the steps are the
 * same either way.
 */
internal fun setupProgress(
    protection: Protection,
    hasApps: Boolean,
    hasBreathed: Boolean,
): SetupProgress? = when {
    hasBreathed -> null
    protection == Protection.Off -> SetupProgress(accessibility = false, apps = hasApps)
    protection == Protection.On && !hasApps -> SetupProgress(accessibility = true, apps = false)
    else -> null
}

internal fun setupTitle(progress: SetupProgress): String =
    if (progress.remaining == 1) "One step left" else "Two steps to start"

/**
 * Whether Android will no longer show a permission's prompt, so only app info can change it.
 * Android stops after two refusals. Three signs, any of which is enough once a request comes back
 * refused with no further ask allowed:
 * - it could ask before this request and cannot now, so this was the final refusal;
 * - the answer came back at once, so no prompt was shown;
 * - the same thing happened on the request before ([earlierSilentRefusals]), which covers a slow
 *   phone where "at once" takes longer than the threshold.
 * A prompt someone read and dismissed shows none of them the first time, and is asked again.
 */
internal fun promptWasWithheld(
    granted: Boolean,
    couldAskBefore: Boolean,
    mayAskAgain: Boolean,
    answeredAfterMs: Long,
    earlierSilentRefusals: Int,
): Boolean = !granted && !mayAskAgain &&
    (couldAskBefore || answeredAfterMs in 0 until PROMPT_SHOWN_AFTER_MS || earlierSilentRefusals >= 1)

private const val PROMPT_SHOWN_AFTER_MS = 500L
