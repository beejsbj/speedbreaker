package dev.burooj.speedbreaker.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.burooj.speedbreaker.R
import dev.burooj.speedbreaker.presentation.components.NavRow
import dev.burooj.speedbreaker.presentation.components.Page
import dev.burooj.speedbreaker.presentation.components.RowDivider
import dev.burooj.speedbreaker.presentation.components.Section

/**
 * The short answers, readable with no connection: how a breath goes, why one might not appear,
 * and how to switch Speedbreaker off. The longer guide and bug reports open in the browser.
 */
@Composable
internal fun HelpScreen(
    onBack: () -> Unit,
    onGuide: () -> Unit,
    onPrivacy: () -> Unit,
    versionName: String?,
) = Page(title = "Help", onBack = onBack, eyebrow = "Speedbreaker") {
    val serviceName = stringResource(R.string.accessibility_service_label)
    HelpTopic(
        heading = "How a breath goes",
        body = "Opening a chosen app starts a breath. For the first eight seconds there is only the breath. " +
            "Then you can Leave, open another app you picked as a detour (set in Defaults), or pause " +
            "for 15 minutes. Continue unlocks when the whole breath is done.",
    )
    HelpTopic(
        heading = "If a breath doesn’t appear",
        body = "Check that Home says On, that the app is listed under Apps, and that its Schedule (in " +
            "Defaults, or on the app’s page) covers this hour. There is no breath during a 15-minute " +
            "pause, during a call, when you step out for up to 10 seconds after Continue, or while the " +
            "app is still on screen in a small window. If breaths stop after the phone has sat idle, " +
            "see Battery and background on Home.",
    )
    HelpTopic(
        heading = "Checking in",
        body = "Stay in a chosen app and another breath comes after ten minutes of use. Tap the app " +
            "under Apps on Home to change the interval or turn it off.",
    )
    HelpTopic(
        heading = "Pause 15 min",
        body = "At the top of a breath, once the first eight seconds pass. It stops breaths for that one " +
            "app for fifteen minutes; each app gets two a day. It needs notifications: a running pause " +
            "shows one with End now, and Home lists it too. Allow them under Notifications on Home.",
    )
    HelpTopic(
        heading = "Switch it off",
        body = "Turn off “$serviceName” in Android’s Accessibility settings. Your settings are kept. " +
            "Uninstalling removes them; there is no copy anywhere else.",
    )
    Section(title = "More") {
        NavRow(
            title = "Setup guide and reporting a problem",
            summary = "Opens in your browser",
            onClick = onGuide,
        )
        RowDivider()
        NavRow(title = "Privacy policy", summary = "Opens in your browser", onClick = onPrivacy)
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

@Composable
private fun HelpTopic(heading: String, body: String) {
    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Not Eyebrow: that keeps to one line, and these are long enough to need two at large text.
        Text(
            text = heading.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        Text(body, style = MaterialTheme.typography.bodyLarge)
    }
}
