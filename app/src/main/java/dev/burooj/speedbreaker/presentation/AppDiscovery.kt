package dev.burooj.speedbreaker.presentation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import dev.burooj.speedbreaker.observation.SpeedbreakerService
import java.util.Locale

internal data class LaunchableApp(
    val packageName: String,
    val label: String,
)

internal fun discoverApps(context: Context): List<LaunchableApp> {
    val packageManager = context.packageManager
    val home = packageManager.resolveActivity(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
        PackageManager.MATCH_DEFAULT_ONLY,
    )?.activityInfo?.packageName
    val defaultInput = AndroidSettings.Secure.getString(
        context.contentResolver,
        AndroidSettings.Secure.DEFAULT_INPUT_METHOD,
    )?.substringBefore('/')
    val inputPackages = context.getSystemService(InputMethodManager::class.java)
        ?.enabledInputMethodList
        ?.map { it.packageName }
        .orEmpty()
    val dialer = context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
    val excluded = setOfNotNull(context.packageName, home, defaultInput, dialer) + inputPackages + safetyPackages
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        .mapNotNull { resolveInfo ->
            val activity = resolveInfo.activityInfo ?: return@mapNotNull null
            val packageName = activity.packageName
            if (packageName in excluded || looksSafetyCritical(packageName)) return@mapNotNull null
            LaunchableApp(
                packageName = packageName,
                label = resolveInfo.loadLabel(packageManager).toString(),
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase(Locale.getDefault()) }
}

/**
 * Opens Android's Accessibility list, asking it to scroll to Speedbreaker's entry and highlight
 * it. The two extras are what Android's own Settings search uses; they are not a public contract,
 * so a phone whose Settings ignores them simply shows the list as before.
 */
internal fun openAccessibilitySettings(context: Context) {
    val entry = ComponentName(context, SpeedbreakerService::class.java).flattenToString()
    val intent = Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)
        .putExtra(SETTINGS_HIGHLIGHT_KEY, entry)
        .putExtra(SETTINGS_HIGHLIGHT_ARGS, Bundle().apply { putString(SETTINGS_HIGHLIGHT_KEY, entry) })
    try {
        context.startActivity(intent)
    } catch (_: RuntimeException) {
        context.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}

private const val SETTINGS_HIGHLIGHT_KEY = ":settings:fragment_args_key"
private const val SETTINGS_HIGHLIGHT_ARGS = ":settings:show_fragment_args"

internal fun openAppDetails(context: Context) {
    context.startActivity(
        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(
            Uri.fromParts("package", context.packageName, null),
        ),
    )
}

internal fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName),
    )
}

internal fun openSupportPage(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW,
            Uri.parse("https://beejsbj.github.io/speedbreaker/support.html")))
    } catch (_: android.content.ActivityNotFoundException) {
        android.widget.Toast.makeText(context,
            "Help: beejsbj.github.io/speedbreaker/support.html",
            android.widget.Toast.LENGTH_LONG).show()
    }
}

/** The installed version, for a bug report; null if Android cannot say. */
internal fun installedVersionName(context: Context): String? =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()

internal fun openPrivacyPolicy(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW,
            Uri.parse("https://beejsbj.github.io/speedbreaker/privacy.html")))
    } catch (_: android.content.ActivityNotFoundException) {
        android.widget.Toast.makeText(context,
            "Privacy: beejsbj.github.io/speedbreaker/privacy.html",
            android.widget.Toast.LENGTH_LONG).show()
    }
}

private fun looksSafetyCritical(packageName: String): Boolean {
    val normalized = packageName.lowercase(Locale.US)
    return safetyMarkers.any { it in normalized }
}

private val safetyPackages = setOf(
    "com.android.settings",
    "com.android.systemui",
    "com.android.permissioncontroller",
    "com.google.android.permissioncontroller",
    "com.android.packageinstaller",
)
private val safetyMarkers = listOf(
    "systemui",
    "permissioncontroller",
    "packageinstaller",
    "emergency",
    "dialer",
    "incall",
    "telecom",
    "inputmethod",
)
