package dev.burooj.speedbreaker.presentation

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.burooj.speedbreaker.model.Settings
import dev.burooj.speedbreaker.presentation.components.AppGlyph
import dev.burooj.speedbreaker.presentation.components.ContentMaxWidth
import dev.burooj.speedbreaker.presentation.components.Eyebrow
import dev.burooj.speedbreaker.presentation.components.Glyphs
import dev.burooj.speedbreaker.presentation.components.NavRow
import dev.burooj.speedbreaker.presentation.components.Page
import dev.burooj.speedbreaker.presentation.components.PrimaryAction
import dev.burooj.speedbreaker.presentation.components.QuietAction
import dev.burooj.speedbreaker.presentation.components.RowDivider
import dev.burooj.speedbreaker.presentation.components.Section
import dev.burooj.speedbreaker.presentation.components.StatusDot
import dev.burooj.speedbreaker.presentation.components.appCatalog

/* ---------- First run ---------- */

/** One calm page that says what Speedbreaker does, what it cannot do, and what it keeps. */
@Composable
internal fun WelcomeScreen(onAccept: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 24.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            BreathMark(Modifier.size(72.dp))
            Spacer(Modifier.height(32.dp))
            Text(
                text = "Make the next tap intentional.",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Speedbreaker puts a short breath between you and the apps you choose. " +
                    "Then you decide: continue, leave, or go somewhere else.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(36.dp))
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Promise(
                    heading = "What it sees",
                    body = "Only which chosen app is on screen, read on this phone through Accessibility.",
                )
                Promise(
                    heading = "The first eight seconds",
                    body = "Back, Home, and Recents can’t dismiss the breath. Calls, the dialer, " +
                        "the lock screen, and Accessibility settings always get through.",
                )
                Promise(
                    heading = "What it keeps",
                    body = "Your settings and today’s pauses. No usage history, no answers, " +
                        "no analytics, no cloud. No Usage Access or overlay permission.",
                )
            }
            Spacer(Modifier.height(40.dp))
            PrimaryAction("I understand — continue", onAccept, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Promise(heading: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Eyebrow(heading)
        Text(body, style = MaterialTheme.typography.bodyLarge)
    }
}

/** A still version of the overlay's breath, used as the app's mark. */
@Composable
internal fun BreathMark(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onBackground
    Canvas(modifier) {
        val outer = size.minDimension / 2f
        drawCircle(ink.copy(alpha = 0.05f), radius = outer)
        drawCircle(ink.copy(alpha = 0.10f), radius = outer * 0.68f)
        drawCircle(ink.copy(alpha = 0.6f), radius = outer * 0.68f, style = Stroke(width = 1.25.dp.toPx()))
        drawCircle(ink, radius = 2.5.dp.toPx())
    }
}

@Composable
internal fun LoadingScreen() {
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BreathMark(Modifier.size(56.dp))
            Spacer(Modifier.height(20.dp))
            Text(
                "Opening your settings…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun StorageFailureScreen(error: String?) {
    Page(title = "Speedbreaker is off", onBack = null, eyebrow = "Speedbreaker") {
        Notice(
            tone = NoticeTone.Repair,
            text = error ?: "Local settings aren’t available, so protection can’t start.",
        )
        Text(
            "Restart the app after resolving the storage problem.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/* ---------- Home ---------- */

internal enum class Protection {
    /** Accessibility is off: nothing is paused. */
    Off,

    /** Accessibility is on but the service is not connected or reported an error. */
    Broken,

    /** Connected and able to pause chosen apps. */
    On,
}

/**
 * Home reads top-down as: is it working, which apps, how it behaves, what
 * keeps it reliable. Setup is the same page — undone steps simply lead.
 */
@Composable
internal fun HomeScreen(
    settings: Settings,
    protection: Protection,
    runtimeError: String?,
    notificationsAllowed: Boolean,
    onEnableAccessibility: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onNotifications: () -> Unit,
    onChooseApps: () -> Unit,
    onDefaults: () -> Unit,
    onApp: (String) -> Unit,
) {
    val appCount = settings.apps.size
    val title = when (protection) {
        Protection.Off -> "Off for now"
        Protection.Broken -> "Needs attention"
        Protection.On -> when (appCount) {
            0 -> "Ready when you are"
            1 -> "On for 1 app"
            else -> "On for $appCount apps"
        }
    }
    Page(title = title, onBack = null, eyebrow = "Speedbreaker") {
        StatusPanel(
            protection = protection,
            runtimeError = runtimeError,
            hasApps = appCount > 0,
            breathSeconds = settings.breathSeconds,
            onEnableAccessibility = onEnableAccessibility,
            onOpenAppInfo = onOpenAppInfo,
            onChooseApps = onChooseApps,
        )

        val catalog = appCatalog()
        val use24Hour = DateFormat.is24HourFormat(LocalContext.current)
        Section(
            title = "Apps",
            footnote = if (appCount == 0) "Nothing is chosen until you pick it." else null,
        ) {
            settings.apps.entries
                .sortedBy { catalog.label(it.key).lowercase() }
                .forEach { (packageName, policy) ->
                    val label = catalog.label(packageName)
                    NavRow(
                        title = label,
                        summary = appSummary(policy, settings, use24Hour),
                        onClick = { onApp(packageName) },
                        leading = { AppGlyph(packageName, label) },
                    )
                    RowDivider()
                }
            NavRow(
                title = if (appCount == 0) "Choose apps" else "Add or remove apps",
                onClick = onChooseApps,
                leading = { LeadingGlyphCircle() },
            )
        }

        Section(title = "How pauses work") {
            NavRow(
                title = "Defaults",
                summary = defaultsSummary(settings, use24Hour),
                onClick = onDefaults,
            )
        }

        Section(
            title = "Reliability",
            footnote = "Samsung may put apps to sleep. In app info → Battery, choose Unrestricted so pauses keep arriving.",
        ) {
            NavRow(
                title = "Pause notifications",
                summary = if (notificationsAllowed) {
                    "Allowed"
                } else {
                    "Needed for Pause and its End now action"
                },
                onClick = onNotifications,
                leading = {
                    StatusDot(
                        if (notificationsAllowed) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    )
                },
            )
            RowDivider()
            NavRow(
                title = "Battery and background",
                summary = "Open Speedbreaker’s app info",
                onClick = onOpenAppInfo,
                leading = { StatusDot(Color.Transparent) },
            )
        }
    }
}

@Composable
private fun LeadingGlyphCircle() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Glyphs.Plus, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun StatusPanel(
    protection: Protection,
    runtimeError: String?,
    hasApps: Boolean,
    breathSeconds: Int,
    onEnableAccessibility: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onChooseApps: () -> Unit,
) {
    when (protection) {
        Protection.Off -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Notice(
                tone = NoticeTone.Repair,
                text = "Speedbreaker’s Accessibility switch is off, so nothing is paused. Your settings are kept.",
            )
            PrimaryAction("Turn on in Accessibility", onEnableAccessibility, Modifier.fillMaxWidth())
            Text(
                text = "If Android says the setting is restricted: open app info, tap ⋮ → Allow restricted settings, " +
                    "confirm it’s you, then come back and turn it on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            QuietAction("Open app info", onOpenAppInfo)
        }
        Protection.Broken -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Notice(
                tone = NoticeTone.Repair,
                text = runtimeError ?: "Accessibility is on, but Speedbreaker hasn’t connected. " +
                    "Turning it off and on again usually helps.",
            )
            PrimaryAction("Open Accessibility", onEnableAccessibility, Modifier.fillMaxWidth())
        }
        Protection.On -> if (hasApps) {
            Notice(
                tone = NoticeTone.Calm,
                text = "Opening a chosen app starts a $breathSeconds-second breath.",
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Notice(tone = NoticeTone.Calm, text = "Accessibility is connected. Choose the apps that pull at you.")
                PrimaryAction("Choose apps", onChooseApps, Modifier.fillMaxWidth())
            }
        }
    }
}

internal enum class NoticeTone { Calm, Repair }

/** A status sentence led by a dot: moss when all is well, brick when something needs fixing. */
@Composable
internal fun Notice(tone: NoticeTone, text: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (tone == NoticeTone.Repair) colors.errorContainer else colors.tertiaryContainer,
        contentColor = if (tone == NoticeTone.Repair) colors.onErrorContainer else colors.onTertiaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.Top) {
            StatusDot(
                color = if (tone == NoticeTone.Repair) colors.error else colors.tertiary,
                modifier = Modifier.padding(top = 7.dp),
            )
            Spacer(Modifier.size(14.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
