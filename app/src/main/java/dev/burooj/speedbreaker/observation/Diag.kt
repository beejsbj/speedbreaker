package dev.burooj.speedbreaker.observation

import android.util.Log
import dev.burooj.speedbreaker.enforcement.BreakerEnd
import dev.burooj.speedbreaker.enforcement.EngineDecision
import dev.burooj.speedbreaker.enforcement.OpeningSkip

/**
 * Logcat-only diagnostics: `adb logcat -s Speedbreaker:I`. Nothing is stored, uploaded or
 * counted. Lines carry reason codes, booleans, small integers and elapsed milliseconds. The
 * only package names are the user's own selected targets; never another app's name, a window
 * title, or any screen content.
 */
internal object Diag {
    const val TAG = "Speedbreaker"

    fun i(message: String) {
        try {
            Log.i(TAG, message)
        } catch (_: RuntimeException) {
            // Diagnostics must never disturb enforcement, and a plain JVM test has no log.
        }
    }

    fun log(decision: EngineDecision) = i(decision.logLine())
}

internal fun EngineDecision.logLine(): String = when (this) {
    is EngineDecision.BreakerStarted ->
        "breaker started pkg=$packageName trigger=$trigger breathSec=$breathSeconds"
    is EngineDecision.BreakerEnded ->
        "breaker ended pkg=$packageName reason=$reason ageMs=$ageMs"
    is EngineDecision.OpeningSkipped -> buildString {
        append("opening skipped pkg=").append(packageName).append(" reason=").append(reason)
        absenceMs?.let { append(" absenceMs=").append(it) }
        sessionAgeMs?.let { append(" sessionAgeMs=").append(it) }
    }
    is EngineDecision.TargetHidden -> "target hidden pkg=$packageName"
}

/** Whether the safety detail line should follow this decision into the log. */
internal fun EngineDecision.involvesSafety(): Boolean = when (this) {
    is EngineDecision.BreakerEnded -> reason == BreakerEnd.SAFETY || reason == BreakerEnd.NONINTERACTIVE
    is EngineDecision.OpeningSkipped ->
        reason == OpeningSkip.SAFETY || reason == OpeningSkip.NONINTERACTIVE
    else -> false
}
