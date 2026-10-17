package dev.burooj.speedbreaker.presentation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.burooj.speedbreaker.observation.ServiceStatus
import dev.burooj.speedbreaker.presentation.components.PrimaryAction
import dev.burooj.speedbreaker.presentation.components.QuietAction
import dev.burooj.speedbreaker.presentation.components.Section
import dev.burooj.speedbreaker.presentation.components.ToggleRow
import dev.burooj.speedbreaker.updates.UpdateController
import dev.burooj.speedbreaker.updates.UpdateState

/** The GitHub build's updater: a Home card, the footer check, and the automatic-check toggle. */
@Composable
internal fun rememberUpdateSurface(): UpdateSurface? {
    val context = LocalContext.current
    val controller = remember(context) { UpdateController.get(context) }
    val breathShowing by ServiceStatus.breathShowing.collectAsStateWithLifecycle()
    val confirmation by controller.confirmation.collectAsStateWithLifecycle()

    LifecycleResumeEffect(controller) {
        controller.onAppResumed()
        onPauseOrDispose { }
    }
    // Android's own install prompt opens from the app UI, never over a breath.
    LaunchedEffect(confirmation, breathShowing) {
        val intent = confirmation ?: return@LaunchedEffect
        if (breathShowing) return@LaunchedEffect
        controller.confirmationShown()
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            controller.onInstallStatus(android.content.pm.PackageInstaller.STATUS_FAILURE, null)
        }
    }

    return remember(controller) {
        UpdateSurface(
            homeCard = { UpdateCard(controller) },
            onCheck = { controller.check() },
            defaultsSection = { AutoCheckSection(controller) },
        )
    }
}

@Composable
private fun UpdateCard(controller: UpdateController) {
    val context = LocalContext.current
    val state by controller.state.collectAsStateWithLifecycle()
    UpdateCardContent(
        state = state,
        onUpdate = controller::update,
        onCancel = controller::cancel,
        onDismiss = controller::dismiss,
        onInstallSettings = { openInstallSettings(context) },
    )
}

@Composable
private fun AutoCheckSection(controller: UpdateController) {
    val enabled by controller.autoCheck.collectAsStateWithLifecycle()
    Section(title = "Updates") {
        ToggleRow(
            title = "Check for updates automatically",
            summary = "Asks GitHub once a day when you open Speedbreaker",
            checked = enabled,
            onCheckedChange = controller::setAutoCheck,
        )
    }
}

/** Stateless so each step reads the same in previews and on the phone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UpdateCardContent(
    state: UpdateState,
    onUpdate: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onInstallSettings: () -> Unit,
) {
    if (state == UpdateState.Idle) return
    var showingNotes by rememberSaveable { mutableStateOf(false) }
    val notes = (state as? UpdateState.Available)?.release?.notes.orEmpty()

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val (title, body) = cardText(state)
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (body != null) {
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (state) {
                is UpdateState.Downloading -> LinearProgressIndicator(
                    progress = { state.fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
                is UpdateState.Verifying, UpdateState.Checking -> LinearProgressIndicator(Modifier.fillMaxWidth())
                else -> Unit
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state) {
                    is UpdateState.Available -> {
                        PrimaryAction("Update", onUpdate)
                        if (notes.isNotEmpty()) QuietAction("What’s new", { showingNotes = true })
                        QuietAction("Not now", onDismiss)
                    }
                    is UpdateState.NeedsInstallPermission -> {
                        PrimaryAction("Open settings", onInstallSettings)
                        QuietAction("Not now", onDismiss)
                    }
                    is UpdateState.Downloading, is UpdateState.Verifying, is UpdateState.Installing ->
                        QuietAction("Cancel", onCancel)
                    is UpdateState.Failed -> {
                        if (state.release != null) QuietAction("Try again", onUpdate)
                        QuietAction("OK", onDismiss)
                    }
                    is UpdateState.UpToDate -> QuietAction("OK", onDismiss)
                    UpdateState.Checking, UpdateState.Idle -> Unit
                }
            }
        }
    }

    if (showingNotes && notes.isNotEmpty()) {
        val version = (state as? UpdateState.Available)?.release?.version
        AlertDialog(
            onDismissRequest = { showingNotes = false },
            title = { Text("What’s new in $version") },
            text = {
                Text(
                    notes,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = { showingNotes = false }) { Text("Close") } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        )
    }
}

private fun cardText(state: UpdateState): Pair<String, String?> = when (state) {
    UpdateState.Idle -> "" to null
    UpdateState.Checking -> "Checking for updates…" to "Asking GitHub for the latest release."
    is UpdateState.UpToDate -> "You have the latest version" to "Speedbreaker ${state.installed} is up to date."
    is UpdateState.Available -> "Speedbreaker ${state.release.version} is ready" to (
        state.note ?: "You have ${state.installed}. The update comes from GitHub Releases and keeps your settings."
    )
    is UpdateState.NeedsInstallPermission -> "Allow updates from Speedbreaker" to
        "Android asks once before an app can install updates. Turn on Allow from this source under " +
        "Install unknown apps, then come back; the update continues here. Android still installs it " +
        "only if it’s signed by the same developer."
    is UpdateState.Downloading -> "Downloading ${state.release.version}" to "${(state.fraction * 100).toInt()}%"
    is UpdateState.Verifying -> "Checking the download" to "Comparing it with the checksum published on GitHub."
    is UpdateState.Installing -> "Ready to install ${state.release.version}" to
        "Android will ask you to confirm. Speedbreaker restarts with your settings kept."
    is UpdateState.Failed -> "Update didn’t finish" to state.message
}

private fun openInstallSettings(context: Context) {
    val intent = Intent(
        AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
    )
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        openAppDetails(context)
    }
}
