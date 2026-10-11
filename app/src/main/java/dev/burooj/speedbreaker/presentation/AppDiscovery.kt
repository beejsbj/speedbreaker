package dev.burooj.speedbreaker.presentation

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
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
