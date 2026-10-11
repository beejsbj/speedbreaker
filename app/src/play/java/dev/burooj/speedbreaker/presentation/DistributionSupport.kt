package dev.burooj.speedbreaker.presentation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.burooj.speedbreaker.R

internal data class DistributionSupport(
    val label: String,
    val summary: String,
    val available: Boolean = true,
    val onClick: () -> Unit,
)

/** External direct tips only; no billing SDK, Internet permission or BMC fallback. */
@Composable
internal fun rememberDistributionSupport(): DistributionSupport {
    val context = LocalContext.current
    val url = context.getString(R.string.play_direct_tip_url)
    val uri = Uri.parse(url)
    val verified = context.resources.getBoolean(R.bool.play_direct_tip_verified)
    val available = verified && uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
    return DistributionSupport("Support development", "Optional direct tip in your browser", available) {
        if (available) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, "Open $url in your browser to support Speedbreaker.",
                    Toast.LENGTH_LONG).show()
            }
        }
    }
}
