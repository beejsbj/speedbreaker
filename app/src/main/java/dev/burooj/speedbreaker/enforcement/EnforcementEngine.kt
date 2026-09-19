package dev.burooj.speedbreaker.enforcement

import dev.burooj.speedbreaker.model.PauseState
import dev.burooj.speedbreaker.model.Settings
import dev.burooj.speedbreaker.model.TimeWindow
import dev.burooj.speedbreaker.model.WeeklySchedule
import java.time.Instant
import java.time.ZoneId

internal data class Observation(
    val visiblePackages: Set<String>,
    val interactive: Boolean,
    val safetyUiVisible: Boolean,
    val navigationAway: Boolean = false,
    val elapsedMs: Long,
    val epochMs: Long,
    val zoneId: ZoneId,
)

internal enum class Trigger { OPENING, CONTINUOUS }

internal data class Breaker(
    val packageName: String,
    val trigger: Trigger,
    val startedElapsedMs: Long,
    val breathSeconds: Int,
)

internal sealed interface Choice {
    data object Continue : Choice
    data object Leave : Choice
    data object Pause : Choice
    data class Redirect(val packageName: String) : Choice
}

internal sealed interface Effect {
    data object GoHome : Effect
    data class LaunchApp(val packageName: String) : Effect
    data object Acknowledge : Effect
}

internal data class EngineUpdate(
    val breaker: Breaker?,
    val pauses: Map<String, PauseState>,
    val effects: List<Effect>,
)

/**
 * Deterministic, process-local enforcement state machine.
 *
 * The caller serializes access to an instance. Only [pauses] belongs in persistence;
 * visibility, sessions, and the current breaker deliberately die with the process.
 */
internal class EnforcementEngine(initialPauses: Map<String, PauseState> = emptyMap()) {
    private data class Session(
        var activeMs: Long,
        var lastElapsedMs: Long,
        var wasActivelyVisible: Boolean,
        var absentSinceElapsedMs: Long?,
        var continuousEnabled: Boolean,
    )

    private data class Reconciliation(
        val expiredPauses: Set<String>,
        val activeNow: Map<String, Boolean>,
    )

    private val sessions = mutableMapOf<String, Session>()
    private val previouslyActive = mutableMapOf<String, Boolean>()
    private val suppressedUntilInactive = mutableSetOf<String>()
    private val pendingTriggers = mutableMapOf<String, Trigger>()
    private var pauses: Map<String, PauseState> = initialPauses.toMap()
    private var breaker: Breaker? = null
    private var navigationPendingDuringHardLock = false

    fun update(settings: Settings, observation: Observation): EngineUpdate {
        val reconciliation = reconcile(settings, observation)
        maybeStartBreaker(settings, observation, reconciliation)
        return result()
    }

    fun choose(
        choice: Choice,
        settings: Settings,
        observation: Observation,
    ): EngineUpdate {
        val reconciliation = reconcile(settings, observation)
        maybeStartBreaker(settings, observation, reconciliation)

        val current = breaker ?: return result()
        if (!canActOn(current, settings, observation)) return result()

        val elapsed = nonNegativeDelta(observation.elapsedMs, current.startedElapsedMs)
        val hardLockFinished = elapsed >= HARD_LOCK_MS
        when (choice) {
            Choice.Continue -> {
                val breathMs = current.breathSeconds * MILLIS_PER_SECOND
                if (elapsed < breathMs) return result()
                breaker = null
                navigationPendingDuringHardLock = false
                sessions[current.packageName] = Session(
                    activeMs = 0,
                    lastElapsedMs = observation.elapsedMs,
                    wasActivelyVisible = true,
                    absentSinceElapsedMs = null,
                    continuousEnabled = settings.apps[current.packageName]
                        ?.continuousSeconds
                        ?.let { it > 0 } == true,
                )
            }

            Choice.Leave -> {
                if (!hardLockFinished) return result()
                finishAndSuppress(current.packageName)
                return result(Effect.GoHome, Effect.Acknowledge)
            }

            Choice.Pause -> {
                if (!hardLockFinished) return result()
                val today = localDay(observation)
                val state = pauses[current.packageName]
                    ?.takeIf { it.day == today }
                    ?: PauseState(day = today, used = 0)
                if (state.used >= PAUSES_PER_DAY || state.untilEpochMs > observation.epochMs) {
                    return result()
                }
                pauses = pauses + (current.packageName to state.copy(
                    used = state.used + 1,
                    untilEpochMs = safeAdd(observation.epochMs, PAUSE_DURATION_MS),
                ))
                breaker = null
                navigationPendingDuringHardLock = false
                sessions.remove(current.packageName)
                previouslyActive[current.packageName] = false
            }

            is Choice.Redirect -> {
                if (!hardLockFinished) return result()
                val redirects = effectiveRedirects(current.packageName, settings)
                if (
                    redirects.size != REQUIRED_REDIRECTS ||
                    redirects.toSet().size != REQUIRED_REDIRECTS ||
                    choice.packageName !in redirects ||
                    choice.packageName in settings.apps
                ) {
                    return result()
                }
                finishAndSuppress(current.packageName)
                return result(Effect.LaunchApp(choice.packageName), Effect.Acknowledge)
            }
        }
        return result()
    }

    fun endPause(
        packageName: String,
        settings: Settings,
        observation: Observation,
    ): EngineUpdate {
        val reconciliation = reconcile(settings, observation)
        maybeStartBreaker(settings, observation, reconciliation)

        val state = pauses[packageName] ?: return result()
        if (state.untilEpochMs <= observation.epochMs) return result()

        pauses = pauses + (packageName to state.copy(untilEpochMs = 0))
        sessions.remove(packageName)
        previouslyActive[packageName] = false
        if (breaker?.packageName == packageName) {
            breaker = null
            navigationPendingDuringHardLock = false
        }

        val active = isEligible(packageName, settings, observation)
        previouslyActive[packageName] = active
        if (breaker == null && active && packageName !in suppressedUntilInactive) {
            startBreaker(packageName, Trigger.OPENING, settings, observation)
        } else if (active && packageName !in suppressedUntilInactive) {
            pendingTriggers[packageName] = Trigger.OPENING
        }
        return result()
    }

    private fun reconcile(settings: Settings, observation: Observation): Reconciliation {
        val expiredPauses = normalizePauses(settings, observation)
        val selected = settings.apps.keys

        sessions.keys.retainAll(selected)
        previouslyActive.keys.retainAll(selected)
        suppressedUntilInactive.retainAll(selected)
        pendingTriggers.keys.retainAll(selected)

        if (!settings.consentAccepted) {
            breaker = null
            navigationPendingDuringHardLock = false
            sessions.clear()
            previouslyActive.clear()
            suppressedUntilInactive.clear()
            pendingTriggers.clear()
            return Reconciliation(expiredPauses, emptyMap())
        }

        val activeNow = selected.associateWith { isEligible(it, settings, observation) }
        suppressedUntilInactive.removeAll { activeNow[it] != true }
        pendingTriggers.keys.removeAll { activeNow[it] != true }

        // A locked or non-interactive phone, and system safety UI, end active-use
        // sessions. Time in either state must never be reconstructed as app use.
        if (!observation.interactive || observation.safetyUiVisible) {
            sessions.clear()
        }

        val current = breaker
        if (current != null) {
            val hardLockFinished = nonNegativeDelta(
                observation.elapsedMs,
                current.startedElapsedMs,
            ) >= HARD_LOCK_MS
            val selectedAndScheduled = current.packageName in settings.apps &&
                isScheduleActive(effectiveSchedule(current.packageName, settings), observation) &&
                !isPaused(current.packageName, observation.epochMs)
            val mustYield = !selectedAndScheduled ||
                !observation.interactive ||
                observation.safetyUiVisible
            when {
                mustYield -> {
                    breaker = null
                    navigationPendingDuringHardLock = false
                    sessions.remove(current.packageName)
                }
                hardLockFinished &&
                    (observation.navigationAway || navigationPendingDuringHardLock) -> {
                    finishAndSuppress(current.packageName)
                }
                observation.navigationAway &&
                    current.packageName !in observation.visiblePackages -> {
                    // The navigation signal is one-shot. If Android already hid the
                    // target during hard lock, remember it and yield exactly at 8s.
                    navigationPendingDuringHardLock = true
                }
            }
        }

        val overlayShowing = breaker != null
        for (packageName in selected.sorted()) {
            val session = sessions[packageName] ?: continue
            val scheduleActive = isScheduleActive(effectiveSchedule(packageName, settings), observation)
            val paused = isPaused(packageName, observation.epochMs)
            if (!scheduleActive || paused) {
                sessions.remove(packageName)
                continue
            }

            val continuousEnabled = settings.apps[packageName]
                ?.continuousSeconds
                ?.let { it > 0 } == true
            if (!continuousEnabled) session.activeMs = 0
            if (continuousEnabled && !session.continuousEnabled) {
                // Enabling an interval starts it now; time spent with the feature
                // disabled is not reconstructed as continuous use.
                session.activeMs = 0
                session.lastElapsedMs = observation.elapsedMs
            }
            session.continuousEnabled = continuousEnabled

            val usable = activeNow[packageName] == true && !overlayShowing
            if (usable) {
                val absence = session.absentSinceElapsedMs?.let {
                    nonNegativeDelta(observation.elapsedMs, it)
                }
                if (absence != null && absence > ABSENCE_GRACE_MS) {
                    sessions.remove(packageName)
                    continue
                }
                if (session.wasActivelyVisible) {
                    session.activeMs = safeAdd(
                        session.activeMs,
                        nonNegativeDelta(observation.elapsedMs, session.lastElapsedMs),
                    )
                }
                session.wasActivelyVisible = true
                session.absentSinceElapsedMs = null
            } else {
                if (session.wasActivelyVisible) {
                    session.absentSinceElapsedMs = observation.elapsedMs
                }
                session.wasActivelyVisible = false
                val absence = session.absentSinceElapsedMs?.let {
                    nonNegativeDelta(observation.elapsedMs, it)
                }
                if (absence != null && absence > ABSENCE_GRACE_MS) {
                    sessions.remove(packageName)
                    continue
                }
            }
            session.lastElapsedMs = observation.elapsedMs
        }

        return Reconciliation(expiredPauses, activeNow)
    }

    private fun maybeStartBreaker(
        settings: Settings,
        observation: Observation,
        reconciliation: Reconciliation,
    ) {
        if (!settings.consentAccepted) {
            rememberActivity(reconciliation.activeNow)
            return
        }

        val openingCandidates = reconciliation.activeNow.keys
            .asSequence()
            .filter { reconciliation.activeNow[it] == true }
            .filter { it !in sessions }
            .filter { it !in suppressedUntilInactive }
            .filter { previouslyActive[it] != true || it in reconciliation.expiredPauses }
            .sorted()
            .toList()
        openingCandidates.forEach { pendingTriggers[it] = Trigger.OPENING }

        val continuousCandidates = sessions.keys
            .asSequence()
            .filter { reconciliation.activeNow[it] == true }
            .filter { packageName ->
                val seconds = settings.apps[packageName]?.continuousSeconds
                seconds != null && seconds > 0 &&
                    sessions.getValue(packageName).activeMs >= seconds * MILLIS_PER_SECOND
            }
            .sorted()
            .toList()
        continuousCandidates.forEach {
            pendingTriggers.putIfAbsent(it, Trigger.CONTINUOUS)
        }

        rememberActivity(reconciliation.activeNow)
        // A navigation event must yield the surface before another queued
        // split-screen target can be presented from the same stale window set.
        if (breaker != null || observation.navigationAway) return

        val next = pendingTriggers.entries
            .sortedWith(compareBy<Map.Entry<String, Trigger>> { it.value != Trigger.OPENING }.thenBy { it.key })
            .firstOrNull()
            ?: return
        pendingTriggers.remove(next.key)
        if (next.value == Trigger.CONTINUOUS) sessions[next.key]?.activeMs = 0
        startBreaker(next.key, next.value, settings, observation)
    }

    private fun rememberActivity(activeNow: Map<String, Boolean>) {
        for ((packageName, active) in activeNow) previouslyActive[packageName] = active
    }

    private fun startBreaker(
        packageName: String,
        trigger: Trigger,
        settings: Settings,
        observation: Observation,
    ) {
        breaker = Breaker(
            packageName = packageName,
            trigger = trigger,
            startedElapsedMs = observation.elapsedMs,
            breathSeconds = settings.breathSeconds.coerceIn(MIN_BREATH_SECONDS, MAX_BREATH_SECONDS),
        )
        pendingTriggers.remove(packageName)
        navigationPendingDuringHardLock = false
    }

    private fun canActOn(
        current: Breaker,
        settings: Settings,
        observation: Observation,
    ): Boolean = breaker == current && isEligible(current.packageName, settings, observation)

    private fun finishAndSuppress(packageName: String) {
        breaker = null
        navigationPendingDuringHardLock = false
        sessions.remove(packageName)
        pendingTriggers.remove(packageName)
        suppressedUntilInactive += packageName
    }

    private fun normalizePauses(settings: Settings, observation: Observation): Set<String> {
        val today = localDay(observation)
        val expired = mutableSetOf<String>()
        val normalized = buildMap {
            for ((packageName, original) in pauses) {
                if (packageName !in settings.apps) continue
                val wasActive = original.untilEpochMs > observation.epochMs
                val activeUntil = original.untilEpochMs.takeIf { wasActive } ?: 0
                val state = if (original.day == today) {
                    original.copy(used = original.used.coerceIn(0, PAUSES_PER_DAY), untilEpochMs = activeUntil)
                } else {
                    PauseState(day = today, used = 0, untilEpochMs = activeUntil)
                }
                put(packageName, state)
                if (original.untilEpochMs > 0 && !wasActive) expired += packageName
            }
        }
        pauses = normalized
        return expired
    }

    private fun isEligible(
        packageName: String,
        settings: Settings,
        observation: Observation,
    ): Boolean = settings.consentAccepted &&
        packageName in settings.apps &&
        packageName in observation.visiblePackages &&
        observation.interactive &&
        !observation.safetyUiVisible &&
        !isPaused(packageName, observation.epochMs) &&
        isScheduleActive(effectiveSchedule(packageName, settings), observation)

    private fun isPaused(packageName: String, epochMs: Long): Boolean =
        pauses[packageName]?.untilEpochMs?.let { it > epochMs } == true

    private fun effectiveSchedule(packageName: String, settings: Settings): WeeklySchedule? =
        settings.apps[packageName]?.schedule ?: settings.schedule

    private fun effectiveRedirects(packageName: String, settings: Settings): List<String> =
        settings.apps[packageName]?.redirects ?: settings.redirects

    private fun isScheduleActive(schedule: WeeklySchedule?, observation: Observation): Boolean {
        if (schedule == null) return true
        val local = Instant.ofEpochMilli(observation.epochMs).atZone(observation.zoneId)
        val minute = local.hour * MINUTES_PER_HOUR + local.minute
        val today = local.dayOfWeek.value
        val yesterday = if (today == 1) 7 else today - 1

        val todayWindow = schedule.days[today]
        if (todayWindow != null && todayWindow.isValid()) {
            if (todayWindow.startMinute < todayWindow.endMinute) {
                if (minute >= todayWindow.startMinute && minute < todayWindow.endMinute) return true
            } else if (minute >= todayWindow.startMinute) {
                return true
            }
        }

        val yesterdayWindow = schedule.days[yesterday]
        return yesterdayWindow != null &&
            yesterdayWindow.isValid() &&
            yesterdayWindow.startMinute > yesterdayWindow.endMinute &&
            minute < yesterdayWindow.endMinute
    }

    private fun TimeWindow.isValid(): Boolean =
        startMinute in 0 until MINUTES_PER_DAY &&
            endMinute in 0..MINUTES_PER_DAY &&
            startMinute != endMinute

    private fun localDay(observation: Observation): String =
        Instant.ofEpochMilli(observation.epochMs)
            .atZone(observation.zoneId)
            .toLocalDate()
            .toString()

    private fun result(vararg effects: Effect): EngineUpdate = EngineUpdate(
        breaker = breaker,
        pauses = pauses.toMap(),
        effects = effects.toList(),
    )

    private fun nonNegativeDelta(later: Long, earlier: Long): Long =
        if (later >= earlier) later - earlier else 0

    private fun safeAdd(left: Long, right: Long): Long =
        if (right > 0 && left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val HARD_LOCK_MS = 8_000L
        const val ABSENCE_GRACE_MS = 60_000L
        const val PAUSE_DURATION_MS = 15 * 60 * 1_000L
        const val PAUSES_PER_DAY = 2
        const val REQUIRED_REDIRECTS = 4
        const val MIN_BREATH_SECONDS = 8
        const val MAX_BREATH_SECONDS = 60
        const val MINUTES_PER_HOUR = 60
        const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
    }
}
