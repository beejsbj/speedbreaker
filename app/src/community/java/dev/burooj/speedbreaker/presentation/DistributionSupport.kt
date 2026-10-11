package dev.burooj.speedbreaker.presentation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

internal data class DistributionSupport(val label: String, val summary: String, val available: Boolean = true, val onClick: () -> Unit)

@Composable
internal fun rememberDistributionSupport(): DistributionSupport {
    val context = LocalContext.current
    return DistributionSupport("Buy me a coffee", "Opens buymeacoffee.com in your browser") {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/beejsbj"))
                    .addCategory(Intent.CATEGORY_BROWSABLE),
            )
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Open buymeacoffee.com/beejsbj in a browser to support Speedbreaker.",
                Toast.LENGTH_LONG).show()
        }
    }
}
