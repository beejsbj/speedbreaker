package dev.burooj.speedbreaker.observation

import dev.burooj.speedbreaker.enforcement.BreakerEnd
import dev.burooj.speedbreaker.enforcement.EngineDecision
import dev.burooj.speedbreaker.model.BreathEnding
import dev.burooj.speedbreaker.model.countedBreathMs

/**
 * Turns the engine's ordered start/end decisions into counted breath time. The engine stays
 * pure; this remembers only the configured length of the breath now showing.
 */
internal class BreathCounter {
    private var breathSeconds: Int? = null

    /** The milliseconds to add when [decision] ends a breath that counts, otherwise null. */
    fun consume(decision: EngineDecision, fallbackBreathSeconds: Int): Long? = when (decision) {
        is EngineDecision.BreakerStarted -> {
            breathSeconds = decision.breathSeconds
            null
        }
        is EngineDecision.BreakerEnded -> {
            val seconds = breathSeconds ?: fallbackBreathSeconds
            breathSeconds = null
            countedBreathMs(decision.ageMs, seconds)
        }
        else -> null
    }
}

/**
 * How the person would describe the way a breath ended. Going Home, Back or to another app once
 * the first eight seconds allow it is leaving, the same as pressing Leave. Anything they did not
 * do themselves (a call, the lock screen, a schedule or settings change) is [BreathEnding.Other].
 */
internal fun BreakerEnd.asEnding(): BreathEnding = when (this) {
    BreakerEnd.CHOICE_LEAVE, BreakerEnd.NAV_RELEASE, BreakerEnd.USER_BACK -> BreathEnding.Left
    BreakerEnd.CHOICE_CONTINUE -> BreathEnding.Continued
    BreakerEnd.CHOICE_REDIRECT -> BreathEnding.Detoured
    BreakerEnd.CHOICE_PAUSE -> BreathEnding.TimeOff
    else -> BreathEnding.Other
}
