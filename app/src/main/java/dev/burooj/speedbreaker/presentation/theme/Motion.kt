package dev.burooj.speedbreaker.presentation.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring

/*
 * Material 3 Expressive's *standard* motion scheme: springs rather than
 * timed curves. Spatial springs move and resize things; effects springs fade
 * and recolor, never overshooting. Values are the published StandardMotionTokens
 * (material3 keeps its MotionScheme internal in 1.4, so they are restated here).
 * The bouncier *expressive* scheme is deliberately not used.
 */
internal object Motion {
    fun <T> spatialFast(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 1400f)
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 700f)
    fun <T> spatialSlow(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 300f)

    fun <T> effectsFast(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 3800f)
    fun <T> effects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)
    fun <T> effectsSlow(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 800f)

    /** The given spec, or an instant change when the system asks for less motion. */
    fun <T> orSnap(motionEnabled: Boolean, spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
        if (motionEnabled) spec else snap()
}
