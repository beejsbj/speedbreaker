package dev.burooj.speedbreaker.presentation

import androidx.compose.runtime.Composable

/**
 * What a distribution adds to the app UI so it can update itself. Only the GitHub build
 * supplies one; the F-Droid and Play builds get `null` from `rememberUpdateSurface()` and
 * contain no network code. It lives in the app UI only, never in the breathing overlay.
 */
internal class UpdateSurface(
    /** A calm card on Home, empty while nothing is happening. */
    val homeCard: @Composable () -> Unit,
    /** The Home footer's "Check for updates". */
    val onCheck: () -> Unit,
    /** The automatic-check toggle at the foot of Defaults. */
    val defaultsSection: @Composable () -> Unit,
)
