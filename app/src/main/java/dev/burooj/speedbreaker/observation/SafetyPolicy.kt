package dev.burooj.speedbreaker.observation

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import dev.burooj.speedbreaker.model.Settings

/** Runtime backstop for packages that must never become enforcement targets. */
internal class SafetyPolicy(context: Context) {
    private val appContext = context.applicationContext
    private val callSafety = CallSafety(appContext)
    private val launcherPackages = mutableSetOf<String>()
    private val excludedTargets = mutableSetOf<String>()
    private val immediateYieldPackages = mutableSetOf<String>()

    init {
        excludedTargets += appContext.packageName
        excludedTargets += setOf(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
        )
        immediateYieldPackages += setOf(
            "com.android.settings",
            "com.android.incallui",
            "com.samsung.android.incallui",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.android.dialer",
            "com.android.emergency",
            "com.google.android.emergency",
            "com.samsung.android.emergency",
        )
        excludedTargets += immediateYieldPackages

        val packageManager = appContext.packageManager
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.queryIntentActivities(homeIntent, 0).forEach { info ->
            launcherPackages += info.activityInfo.packageName
        }
        packageManager.resolveActivity(homeIntent, 0)?.activityInfo?.packageName?.let {
            launcherPackages += it
        }
        excludedTargets += launcherPackages

        val inputMethods = appContext.getSystemService(InputMethodManager::class.java)
        inputMethods?.enabledInputMethodList?.forEach { inputMethod ->
            excludedTargets += inputMethod.packageName
        }

        appContext.getSystemService(TelecomManager::class.java)
            ?.defaultDialerPackage
            ?.let { dialer ->
                excludedTargets += dialer
                immediateYieldPackages += dialer
            }
    }

    fun filter(settings: Settings): Settings = settings.copy(
        apps = settings.apps.filterKeys { it !in excludedTargets },
    )

    fun isNavigationPackage(packageName: String?): Boolean =
        packageName != null &&
            (packageName in launcherPackages || packageName == "com.android.systemui")

    fun isImmediateYieldPackage(packageName: String?): Boolean =
        packageName != null && packageName in immediateYieldPackages

    /** Why safety may be holding breaths back, for the diagnostic log only. */
    fun safetyDetail(visiblePackages: Set<String>): SafetyDetail {
        val call = callSafety.detail()
        return SafetyDetail(
            callSafety = call.status,
            telecomInCall = call.telecomInCall,
            audioMode = call.audioMode,
            keyguardLocked = try {
                appContext.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked
            } catch (_: RuntimeException) {
                null
            },
            yieldPackageVisible = visiblePackages.any { it in immediateYieldPackages },
        )
    }

    fun safetyUiVisible(visiblePackages: Set<String>): Boolean {
        if (callSafety.status() != CallSafety.Status.Idle) return true
        val keyguard = appContext.getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked == true) return true
        return visiblePackages.any { it in immediateYieldPackages }
    }
}

/** Aggregate booleans and small integers only; no package name, caller or call content. */
internal data class SafetyDetail(
    val callSafety: CallSafety.Status,
    val telecomInCall: Boolean?,
    val audioMode: Int?,
    val keyguardLocked: Boolean?,
    val yieldPackageVisible: Boolean,
) {
    fun logLine(interactive: Boolean): String =
        "safety detail interactive=$interactive callSafety=$callSafety " +
            "telecomInCall=${telecomInCall ?: "unknown"} audioMode=${audioMode ?: "unknown"} " +
            "keyguardLocked=${keyguardLocked ?: "unknown"} yieldPackageVisible=$yieldPackageVisible"
}
