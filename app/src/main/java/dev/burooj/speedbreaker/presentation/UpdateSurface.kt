package dev.burooj.speedbreaker.presentation

import androidx.compose.runtime.Composable

/**
 * What a distribution adds to the app UI so it can update itself. Only the GitHub build
 * supplies one; the F-Droid and Play builds get `null` from `rememberUpdateSurface()` and
 * contain no network code. It lives in the app UI only, never in the breathing overlay.
 */
internal class UpdateSurface(
    /** A calm card on Home once there is an update to offer or one under way; empty otherwise. */
    val homeCard: @Composable () -> Unit,
    /** Settings' Updates group: the check, its answer, and the automatic-check toggle. */
    val settingsSection: @Composable () -> Unit,
)
