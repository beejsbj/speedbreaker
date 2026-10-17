package dev.burooj.speedbreaker.presentation

import androidx.compose.runtime.Composable

/** F-Droid and Play builds never update themselves and have no network access. */
@Composable
internal fun rememberUpdateSurface(): UpdateSurface? = null
