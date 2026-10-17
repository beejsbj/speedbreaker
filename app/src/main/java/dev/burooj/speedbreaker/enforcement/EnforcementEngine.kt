package dev.burooj.speedbreaker.enforcement

import dev.burooj.speedbreaker.model.PauseState
import dev.burooj.speedbreaker.model.Settings
import dev.burooj.speedbreaker.model.WeeklySchedule
import dev.burooj.speedbreaker.model.effectiveRedirects
import dev.burooj.speedbreaker.model.isActiveAt
import java.time.Instant
import java.time.ZoneId

internal data class Observation(
    val visiblePackages: Set<String>,
    val interactive: Boolean,
    val safetyUiVisible: Boolean,
    val navigationAway: Boolean = false,
    /** The user pressed Back on the overlay itself; the visible target is not a reason to hold. */
    val userBack: Boolean = false,
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
        val startedElapsedMs: Long,
        var activeMs: Long,
        var lastElapsedMs: Long,
        var wasActivelyVisible: Boolean,
        var absentSinceElapsedMs: Long?,
        var continuousEnabled: Boolean,
    )

    private data class Reconciliation(
        val expiredPauses: Set<String>,
        val activeNow: Map<String, Boolean>,
        /** Diagnostics only: how long a resumed session's target was away. */
        val resumedAfterMs: Map<String, Long> = emptyMap(),
    )

    private val sessions = mutableMapOf<String, Session>()
    private val previouslyActive = mutableMapOf<String, Boolean>()
    private val suppressedUntilInactive = mutableSetOf<String>()
    private val pendingTriggers = mutableMapOf<String, Trigger>()
    private var pauses: Map<String, PauseState> = initialPauses.toMap()
    private var breaker: Breaker? = null
    private var navigationPendingDuringHardLock = false

    // Diagnostics only. Nothing below is read by enforcement decisions.
    private val decisions = ArrayDeque<EngineDecision>()

    /** Per selected target in view: the last opening verdict logged, null while it has a breath. */
    private val lastVerdict = mutableMapOf<String, OpeningSkip?>()

    /**
     * Drains what the engine decided since the last call, oldest first. The caller logs it;
     * the engine stays free of Android. Unread decisions are capped, oldest dropped first.
     */
    fun takeDecisions(): List<EngineDecision> = decisions.toList().also { decisions.clear() }

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
                clearBreaker(BreakerEnd.CHOICE_CONTINUE, observation.elapsedMs)
                navigationPendingDuringHardLock = false
                sessions[current.packageName] = Session(
                    startedElapsedMs = observation.elapsedMs,
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
                finishAndSuppress(current.packageName, BreakerEnd.CHOICE_LEAVE, observation.elapsedMs)
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
                clearBreaker(BreakerEnd.CHOICE_PAUSE, observation.elapsedMs)
                navigationPendingDuringHardLock = false
                sessions.remove(current.packageName)
                previouslyActive[current.packageName] = false
            }

            is Choice.Redirect -> {
                if (!hardLockFinished) return result()
                val redirects = settings.effectiveRedirects(current.packageName)
                if (
                    choice.packageName !in redirects ||
                    choice.packageName in settings.apps
                ) {
                    return result()
                }
                finishAndSuppress(current.packageName, BreakerEnd.CHOICE_REDIRECT, observation.elapsedMs)
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
            clearBreaker(BreakerEnd.PAUSE_ENDED, observation.elapsedMs)
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
        lastVerdict.keys.retainAll(selected)

        if (!settings.consentAccepted) {
            clearBreaker(BreakerEnd.CONSENT_OFF, observation.elapsedMs)
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
            // The target is gone and another application owns the screen (for example a
            // notification tap from the shade). That is navigation even though no
            // launcher or Recents event announced it.
            val targetReplaced = current.packageName !in observation.visiblePackages &&
                observation.visiblePackages.any { it != SYSTEM_UI_PACKAGE }
            val navigationAway = observation.navigationAway || targetReplaced
            when {
                mustYield -> {
                    clearBreaker(yieldReason(current, settings, observation), observation.elapsedMs)
                    navigationPendingDuringHardLock = false
                    sessions.remove(current.packageName)
                }
                hardLockFinished && (navigationAway || navigationPendingDuringHardLock) -> {
                    if (observation.userBack || current.packageName !in observation.visiblePackages) {
                        finishAndSuppress(
                            current.packageName,
                            if (observation.userBack) BreakerEnd.USER_BACK else BreakerEnd.NAV_RELEASE,
                            observation.elapsedMs,
                        )
                    } else {
                        // The target is on screen again (the user came back within the lock, or
                        // a transient frame hid it). Navigation that no longer holds must not
                        // remove the breath before its choices appear.
                        navigationPendingDuringHardLock = false
                    }
                }
                navigationAway && current.packageName !in observation.visiblePackages -> {
                    // The navigation signal is one-shot. If Android already hid the
                    // target during hard lock, remember it and yield exactly at 8s.
                    navigationPendingDuringHardLock = true
                }
            }
        }

        val overlayShowing = breaker != null
        val resumedAfterMs = mutableMapOf<String, Long>()
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

            val activelyVisible = activeNow[packageName] == true
            if (activelyVisible) {
                val absence = session.absentSinceElapsedMs?.let {
                    nonNegativeDelta(observation.elapsedMs, it)
                }
                if (absence != null && absence > ABSENCE_GRACE_MS) {
                    sessions.remove(packageName)
                    continue
                }
                if (absence != null) resumedAfterMs[packageName] = absence
                if (session.wasActivelyVisible && !overlayShowing) {
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

        return Reconciliation(expiredPauses, activeNow, resumedAfterMs)
    }

    private fun maybeStartBreaker(
        settings: Settings,
        observation: Observation,
        reconciliation: Reconciliation,
    ) {
        if (!settings.consentAccepted) {
            noteTargets(settings, observation, reconciliation, openingSkips(settings, observation), null)
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

        val skips = openingSkips(settings, observation)
        rememberActivity(reconciliation.activeNow)
        // A navigation event must yield the surface before another queued
        // split-screen target can be presented from the same stale window set.
        val started = if (breaker != null || observation.navigationAway) {
            null
        } else {
            startNextPending(settings, observation)
        }
        noteTargets(settings, observation, reconciliation, skips, started)
    }

    private fun startNextPending(settings: Settings, observation: Observation): String? {
        val next = pendingTriggers.entries
            .sortedWith(compareBy<Map.Entry<String, Trigger>> { it.value != Trigger.OPENING }.thenBy { it.key })
            .firstOrNull()
            ?: return null
        pendingTriggers.remove(next.key)
        if (next.value == Trigger.CONTINUOUS) sessions[next.key]?.activeMs = 0
        startBreaker(next.key, next.value, settings, observation)
        return next.key
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
        val started = Breaker(
            packageName = packageName,
            trigger = trigger,
            startedElapsedMs = observation.elapsedMs,
            breathSeconds = settings.breathSeconds.coerceIn(MIN_BREATH_SECONDS, MAX_BREATH_SECONDS),
        )
        breaker = started
        pendingTriggers.remove(packageName)
        navigationPendingDuringHardLock = false
        record(EngineDecision.BreakerStarted(packageName, trigger, started.breathSeconds))
        lastVerdict[packageName] = null
    }

    private fun canActOn(
        current: Breaker,
        settings: Settings,
        observation: Observation,
    ): Boolean = breaker == current && isEligible(current.packageName, settings, observation)

    private fun finishAndSuppress(packageName: String, reason: BreakerEnd, nowElapsedMs: Long) {
        clearBreaker(reason, nowElapsedMs)
        navigationPendingDuringHardLock = false
        sessions.remove(packageName)
        pendingTriggers.remove(packageName)
        suppressedUntilInactive += packageName
    }

    /** Drops the breath, noting why. Every place that ends one goes through here. */
    private fun clearBreaker(reason: BreakerEnd, nowElapsedMs: Long) {
        val ended = breaker ?: return
        breaker = null
        record(
            EngineDecision.BreakerEnded(
                packageName = ended.packageName,
                reason = reason,
                ageMs = nonNegativeDelta(nowElapsedMs, ended.startedElapsedMs),
            ),
        )
        // The end itself explains why the target gets no breath next; only a different
        // verdict while it stays in view is worth another line.
        if (reason == BreakerEnd.PAUSE_ENDED) {
            lastVerdict.remove(ended.packageName)
        } else {
            lastVerdict[ended.packageName] = expectedSkip(reason)
        }
    }

    private fun expectedSkip(reason: BreakerEnd): OpeningSkip? = when (reason) {
        BreakerEnd.CHOICE_CONTINUE -> OpeningSkip.IN_SESSION
        BreakerEnd.CHOICE_LEAVE,
        BreakerEnd.CHOICE_REDIRECT,
        BreakerEnd.NAV_RELEASE,
        BreakerEnd.USER_BACK -> OpeningSkip.SUPPRESSED_UNTIL_INACTIVE
        BreakerEnd.CHOICE_PAUSE,
        BreakerEnd.PAUSED -> OpeningSkip.PAUSED
        BreakerEnd.SAFETY -> OpeningSkip.SAFETY
        BreakerEnd.NONINTERACTIVE -> OpeningSkip.NONINTERACTIVE
        BreakerEnd.SCHEDULE -> OpeningSkip.SCHEDULE_INACTIVE
        BreakerEnd.CONSENT_OFF -> OpeningSkip.CONSENT_OFF
        BreakerEnd.DESELECTED,
        BreakerEnd.PAUSE_ENDED,
        BreakerEnd.ENGINE_RESET -> null
    }

    private fun yieldReason(current: Breaker, settings: Settings, observation: Observation): BreakerEnd = when {
        !observation.interactive -> BreakerEnd.NONINTERACTIVE
        observation.safetyUiVisible -> BreakerEnd.SAFETY
        current.packageName !in settings.apps -> BreakerEnd.DESELECTED
        isPaused(current.packageName, observation.epochMs) -> BreakerEnd.PAUSED
        else -> BreakerEnd.SCHEDULE
    }

    /** Verdicts for the selected targets in view, taken before this observation is remembered. */
    private fun openingSkips(settings: Settings, observation: Observation): Map<String, OpeningSkip?> =
        settings.apps.keys
            .filter { it in observation.visiblePackages }
            .associateWith { packageName ->
                openingSkipReason(
                    OpeningFacts(
                        consentAccepted = settings.consentAccepted,
                        interactive = observation.interactive,
                        safetyUiVisible = observation.safetyUiVisible,
                        paused = isPaused(packageName, observation.epochMs),
                        scheduleActive = isScheduleActive(
                            effectiveSchedule(packageName, settings),
                            observation,
                        ),
                        triggerQueued = packageName in pendingTriggers,
                        inSession = packageName in sessions,
                        suppressedUntilInactive = packageName in suppressedUntilInactive,
                        previouslyActive = previouslyActive[packageName] == true,
                        otherBreakerActive = breaker?.let { it.packageName != packageName } == true,
                        navigationAway = observation.navigationAway,
                    ),
                )
            }

    /**
     * Records a target leaving view, and why a target in view got no breath. A verdict is
     * recorded when it changes, not at every observation, so a capture shows decisions
     * rather than the 250 ms tick.
     */
    private fun noteTargets(
        settings: Settings,
        observation: Observation,
        reconciliation: Reconciliation,
        skips: Map<String, OpeningSkip?>,
        started: String?,
    ) {
        for (packageName in settings.apps.keys.sorted()) {
            if (packageName !in observation.visiblePackages) {
                if (lastVerdict.containsKey(packageName)) {
                    lastVerdict.remove(packageName)
                    record(EngineDecision.TargetHidden(packageName))
                }
                continue
            }
            val current = breaker
            val verdict = when {
                packageName == started || current?.packageName == packageName -> null
                // The breath that started this observation belongs to someone else.
                skips[packageName] == null && current != null -> OpeningSkip.OTHER_BREAKER_ACTIVE
                else -> skips[packageName]
            }
            if (lastVerdict.containsKey(packageName) && lastVerdict[packageName] == verdict) continue
            lastVerdict[packageName] = verdict
            if (verdict == null) continue
            val session = sessions[packageName].takeIf { verdict == OpeningSkip.IN_SESSION }
            record(
                EngineDecision.OpeningSkipped(
                    packageName = packageName,
                    reason = verdict,
                    absenceMs = session?.let { reconciliation.resumedAfterMs[packageName] },
                    sessionAgeMs = session?.let {
                        nonNegativeDelta(observation.elapsedMs, it.startedElapsedMs)
                    },
                ),
            )
        }
    }

    private fun record(decision: EngineDecision) {
        if (decisions.size >= MAX_UNREAD_DECISIONS) decisions.removeFirst()
        decisions.addLast(decision)
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

    private fun isScheduleActive(schedule: WeeklySchedule?, observation: Observation): Boolean =
        schedule.isActiveAt(Instant.ofEpochMilli(observation.epochMs).atZone(observation.zoneId))

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
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        const val MILLIS_PER_SECOND = 1_000L
        const val HARD_LOCK_MS = 8_000L
        const val ABSENCE_GRACE_MS = 10_000L
        const val PAUSE_DURATION_MS = 15 * 60 * 1_000L
        const val PAUSES_PER_DAY = 2
        const val MIN_BREATH_SECONDS = 8
        const val MAX_BREATH_SECONDS = 60
        const val MAX_UNREAD_DECISIONS = 64
    }
}
