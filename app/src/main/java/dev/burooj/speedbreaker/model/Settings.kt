package dev.burooj.speedbreaker.model

/** Persisted preferences only; runtime app visibility is never stored. */
internal data class Settings(
    val consentAccepted: Boolean = false,
    val breathSeconds: Int = 12,
    val apps: Map<String, AppPolicy> = emptyMap(),
    val redirects: List<String> = emptyList(),
    val schedule: WeeklySchedule? = null,
)

internal data class AppPolicy(
    val continuousSeconds: Int? = 600,
    val schedule: WeeklySchedule? = null,
    val redirects: List<String>? = null,
)

/** ISO weekday (Monday=1), one local-time window per selected day. Null means always. */
internal data class WeeklySchedule(val days: Map<Int, TimeWindow> = emptyMap())

/** End earlier than start crosses midnight; equal endpoints are invalid. */
internal data class TimeWindow(val startMinute: Int, val endMinute: Int)

/** No pause history is retained. An active pause may extend beyond its token day. */
internal data class PauseState(val day: String, val used: Int, val untilEpochMs: Long = 0)
