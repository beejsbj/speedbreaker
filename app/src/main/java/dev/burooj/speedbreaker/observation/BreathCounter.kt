package dev.burooj.speedbreaker.observation

import dev.burooj.speedbreaker.enforcement.EngineDecision
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
