package dev.burooj.speedbreaker.systemaction

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.widget.Toast
import dev.burooj.speedbreaker.R
import dev.burooj.speedbreaker.enforcement.Effect

/** Executes the small, explicit set of Android actions emitted by the engine. */
internal class SystemActionController(
    private val service: AccessibilityService,
) {
    private var suppressNextAcknowledgement = false

    fun execute(effect: Effect) {
        when (effect) {
            Effect.GoHome -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            is Effect.LaunchApp -> launch(effect.packageName)
            Effect.Acknowledge -> {
                if (suppressNextAcknowledgement) {
                    suppressNextAcknowledgement = false
                } else {
                    Toast.makeText(service, "A deliberate choice.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun performBack(): Boolean =
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)

    private fun launch(packageName: String) {
        val intent = service.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            unavailableRedirect()
            return
        }
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
        )
        runCatching { service.startActivity(intent) }
            .onFailure { unavailableRedirect() }
    }

    private fun unavailableRedirect() {
        suppressNextAcknowledgement = true
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        Toast.makeText(
            service,
            service.getString(R.string.redirect_unavailable),
            Toast.LENGTH_SHORT,
        ).show()
    }
}
