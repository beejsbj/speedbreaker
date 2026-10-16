package dev.burooj.speedbreaker.enforcement

/** Why a breath left the screen. Reported for diagnostics only; it never steers the engine. */
internal enum class BreakerEnd {
    CHOICE_CONTINUE,
    CHOICE_LEAVE,
    CHOICE_PAUSE,
    CHOICE_REDIRECT,

    /** Launcher, Recents or another app took the screen once the hard lock allowed release. */
    NAV_RELEASE,
    USER_BACK,
    SAFETY,
    NONINTERACTIVE,
    SCHEDULE,
    PAUSED,

    /** The target left the selected set, or safety policy excluded it. */
    DESELECTED,
    CONSENT_OFF,

    /** End now on the target's pause, while its breath was showing. */
    PAUSE_ENDED,

    /** The service dropped or rebuilt the engine under a showing breath. */
    ENGINE_RESET,
}

/** Why a visible selected target did not get a breath. */
internal enum class OpeningSkip {
    CONSENT_OFF,
    NONINTERACTIVE,
    SAFETY,
    PAUSED,
    SCHEDULE_INACTIVE,

    /** Returned within the ten-second absence grace, or never observed leaving. */
    IN_SESSION,
    SUPPRESSED_UNTIL_INACTIVE,

    /** Active at the previous observation, so this is not a new opening. */
    PREVIOUSLY_ACTIVE,
    OTHER_BREAKER_ACTIVE,

    /** A navigation event is yielding the surface; the queued breath starts after it. */
    NAVIGATION_PENDING,
}

/** The engine's view of one visible selected target at one observation. */
internal data class OpeningFacts(
    val consentAccepted: Boolean,
    val interactive: Boolean,
    val safetyUiVisible: Boolean,
    val paused: Boolean,
    val scheduleActive: Boolean,
    /** An opening or check-in is queued for this target. */
    val triggerQueued: Boolean,
    val inSession: Boolean,
    val suppressedUntilInactive: Boolean,
    val previouslyActive: Boolean,
    /** A breath for a different target is showing. */
    val otherBreakerActive: Boolean,
    val navigationAway: Boolean,
)

/**
 * Pure explanation of why a visible selected target is not starting a breath, or null when
 * one starts (or is queued to). The ineligibility reasons come first, in the order a reader
 * of a capture can rule them out.
 */
internal fun openingSkipReason(facts: OpeningFacts): OpeningSkip? = when {
    !facts.consentAccepted -> OpeningSkip.CONSENT_OFF
    !facts.interactive -> OpeningSkip.NONINTERACTIVE
    facts.safetyUiVisible -> OpeningSkip.SAFETY
    facts.paused -> OpeningSkip.PAUSED
    !facts.scheduleActive -> OpeningSkip.SCHEDULE_INACTIVE
    facts.triggerQueued -> when {
        facts.otherBreakerActive -> OpeningSkip.OTHER_BREAKER_ACTIVE
        facts.navigationAway -> OpeningSkip.NAVIGATION_PENDING
        else -> null
    }
    facts.inSession -> OpeningSkip.IN_SESSION
    facts.suppressedUntilInactive -> OpeningSkip.SUPPRESSED_UNTIL_INACTIVE
    facts.previouslyActive -> OpeningSkip.PREVIOUSLY_ACTIVE
    else -> null
}

/**
 * Something the engine decided, in a form a diagnostic log can print. Only selected target
 * packages appear: the user's own configured targets, never any other app.
 */
internal sealed interface EngineDecision {
    data class BreakerStarted(
        val packageName: String,
        val trigger: Trigger,
        val breathSeconds: Int,
    ) : EngineDecision

    data class BreakerEnded(
        val packageName: String,
        val reason: BreakerEnd,
        val ageMs: Long,
    ) : EngineDecision

    /** [absenceMs] and [sessionAgeMs] are set for [OpeningSkip.IN_SESSION] when known. */
    data class OpeningSkipped(
        val packageName: String,
        val reason: OpeningSkip,
        val absenceMs: Long? = null,
        val sessionAgeMs: Long? = null,
    ) : EngineDecision

    /** A selected target left the visible windows; a later return is a new sighting. */
    data class TargetHidden(val packageName: String) : EngineDecision
}
