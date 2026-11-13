package dev.burooj.speedbreaker.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.burooj.speedbreaker.model.Redirects
import dev.burooj.speedbreaker.model.Settings
import dev.burooj.speedbreaker.presentation.components.ActionRow
import dev.burooj.speedbreaker.presentation.components.NavRow
import dev.burooj.speedbreaker.presentation.components.Page
import dev.burooj.speedbreaker.presentation.components.Glyphs
import dev.burooj.speedbreaker.presentation.components.RowIcon
import dev.burooj.speedbreaker.presentation.components.Section
import dev.burooj.speedbreaker.presentation.components.StatusDot
import dev.burooj.speedbreaker.presentation.components.appCatalog

/**
 * Everything a person sets or checks, in one list, with single controls set in place: what every app starts from, what keeps
 * breaths reliable, updates (GitHub build only), and where to read more. Home keeps what is
 * happening; a fault still shows there, with its repair.
 */
@Composable
internal fun SettingsScreen(
    settings: Settings,
    notificationsAllowed: Boolean,
    onBack: () -> Unit,
    onBreathChange: (Int) -> Unit,
    onScheduleTransform: (ScheduleTransform) -> Unit,
    onDetours: () -> Unit,
    onNotifications: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onHelp: () -> Unit,
    onPrivacy: () -> Unit,
    onCallAccess: () -> Unit = {},
    phoneNeedsSettings: Boolean = false,
    phoneAllowed: Boolean = true,
    onSupport: () -> Unit = {},
    supportLabel: String = "Buy me a coffee",
    supportSummary: String = "Opens ko-fi.com in your browser",
    supportAvailable: Boolean = true,
    canResetStats: Boolean = false,
    onResetStats: () -> Unit = {},
    versionName: String? = null,
    updatesSection: (@Composable () -> Unit)? = null,
) {
    val catalog = appCatalog()
    var confirmingReset by remember { mutableStateOf(false) }

    Page(title = "Settings", onBack = onBack, eyebrow = "Speedbreaker") {
        Section(
            title = "Defaults",
            footnote = "What every chosen app starts from. An app can keep its own schedule and " +
                "detours: open it from Apps on Home.",
        ) {
            // Each of these is a single control, so it is set here and not on a page behind a row.
            BreathLengthTile(settings, onBreathChange)
            DefaultScheduleTiles(settings, onScheduleTransform)
            NavRow(
                title = "Detours",
                summary = detoursSummary(
                    settings.redirects.takeIf { Redirects.isValid(it) }.orEmpty().map(catalog::label),
                ),
                onClick = onDetours,
                leading = { RowIcon(Glyphs.Detour) },
            )
        }

        Section(title = "Reliability") {
            NavRow(
                title = "Notifications",
                summary = if (notificationsAllowed) {
                    "Allowed. Only used while a 15-minute pause is running."
                } else {
                    "Not allowed. The Pause 15 min button needs them."
                },
                onClick = onNotifications,
                leading = { RowIcon(Glyphs.Bell) },
                // Off is a choice here, not a fault: only the optional Pause needs them.
                trailing = { StateDot(on = notificationsAllowed) },
            )
            // Optional: breaths work without it, so off is a choice here too.
            NavRow(
                title = "Phone permission",
                summary = when {
                    phoneAllowed -> "Allowed. A breath steps aside for a ringing call, even a silent one."
                    phoneNeedsSettings -> PHONE_APP_INFO
                    else -> PHONE_OPTIONAL
                },
                onClick = if (phoneAllowed) onOpenAppInfo else onCallAccess,
                // The app-info wording replaces the summary after a press, so a screen reader has to be told.
                modifier = if (phoneNeedsSettings && !phoneAllowed) {
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                } else {
                    Modifier
                },
                leading = { RowIcon(Glyphs.Phone) },
                trailing = { StateDot(on = phoneAllowed) },
            )
            NavRow(
                title = "Battery and background",
                summary = "If breaths stop appearing after a while, allow background use. " +
                    "On Samsung, set Battery to Unrestricted.",
                onClick = onOpenAppInfo,
                leading = { RowIcon(Glyphs.Battery) },
            )
        }

        updatesSection?.invoke()

        Section(title = "About") {
            NavRow(
                title = "Help",
                summary = "How it works, and what to check when a breath doesn’t appear",
                onClick = onHelp,
                leading = { RowIcon(Glyphs.Help) },
            )
            NavRow(
                title = "Privacy policy",
                summary = "Opens in your browser",
                onClick = onPrivacy,
                leading = { RowIcon(Glyphs.Shield) },
            )
            if (supportAvailable) {
                NavRow(
                    title = supportLabel,
                    summary = supportSummary,
                    onClick = onSupport,
                    leading = { RowIcon(Glyphs.Heart) },
                )
            }
            if (canResetStats) {
                ActionRow(
                    title = "Reset breathing total",
                    summary = "Sets the total and everything counted with it back to zero",
                    onClick = { confirmingReset = true },
                    leading = { RowIcon(Glyphs.Reset) },
                )
            }
        }

        if (versionName != null) {
            Text(
                text = "Speedbreaker $versionName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    if (confirmingReset) {
        AlertDialog(
            onDismissRequest = { confirmingReset = false },
            title = { Text("Reset your breathing total?") },
            text = {
                Text(
                    "The total, the breath count, what followed your breaths, your detour counts and " +
                        "the week all go back to zero. This stays on this phone and can’t be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmingReset = false; onResetStats() }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmingReset = false }) { Text("Keep it") } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        )
    }
}

/** Moss when the optional thing is allowed, plain when it is not: a state, never a fault. */
@Composable
private fun StateDot(on: Boolean) {
    StatusDot(
        color = if (on) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(start = 12.dp),
    )
}

private const val PHONE_OPTIONAL = "Optional. Without it, a call that rings without a ringtone (silent, vibrate or Do Not " +
    "Disturb) can stay hidden behind a breath. Android words this as “make and manage phone calls”; Speedbreaker can’t make calls and " +
    "doesn’t read numbers, call history, or audio."

/** Once Android has stopped showing its prompt, the row says so and leads to app info instead. */
private const val PHONE_APP_INFO = "Not allowed, and Android won’t ask again here. Tap to open app info, then " +
    "Permissions → Phone → Allow. Speedbreaker opens again once it’s allowed."
