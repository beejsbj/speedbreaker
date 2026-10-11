package dev.burooj.speedbreaker.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

internal data class DistributionSupport(val label: String, val summary: String, val onClick: () -> Unit)

@Composable
internal fun rememberDistributionSupport(): DistributionSupport {
    val model: PlayTipsModel = viewModel()
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    var showChooser by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(lifecycle, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) model.resume()
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) model.resume()
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (showChooser) {
        val activity = context.findActivity()
        AlertDialog(
            onDismissRequest = { showChooser = false },
            title = { Text("Support Speedbreaker") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("An optional one-time tip through Google Play. Every feature stays free; a tip grants nothing.")
                    Text(state.message)
                    state.offers.forEach { offer ->
                        val name = when (offer.details.productId) {
                            "speedbreaker_tip_small" -> "Small tip"
                            "speedbreaker_tip_medium" -> "Medium tip"
                            else -> "Large tip"
                        }
                        OutlinedButton(
                            onClick = { activity?.let { model.buy(it, offer) } },
                            enabled = state.canBuy && activity != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("$name · ${offer.price}") }
                    }
                    if (activity == null) Text("Checkout requires an open app screen.")
                }
            },
            confirmButton = {
                TextButton(onClick = model::retry, enabled = !state.busy) { Text("Retry") }
            },
            dismissButton = {
                TextButton(onClick = { showChooser = false }) { Text("Close") }
            },
        )
    }
    return DistributionSupport("Leave a tip", "Optional support through Google Play") {
        showChooser = true
        model.retry()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext === this) null else baseContext.findActivity()
    else -> null
}
