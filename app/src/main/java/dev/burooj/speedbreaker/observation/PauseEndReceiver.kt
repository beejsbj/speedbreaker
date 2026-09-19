package dev.burooj.speedbreaker.observation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.burooj.speedbreaker.persistence.SpeedbreakerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal class PauseEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)?.takeIf { it.isNotBlank() }
            ?: return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = SpeedbreakerRepository.get(appContext)
                val ready = withTimeoutOrNull(8_000L) { repository.ready.first { it } } == true
                if (!ready || repository.error.value != null) return@launch
                val ended = PausePersistenceCoordinator.withLock {
                    repository.endPause(packageName, System.currentTimeMillis())
                }
                if (ended && repository.error.value == null) {
                    PauseNotifications(appContext).cancel(packageName)
                    SpeedbreakerService.notifyPauseEnded(packageName)
                }
            } finally {
                pending.finish()
            }
        }
    }

    internal companion object {
        const val EXTRA_PACKAGE_NAME = "target_package_name"
    }
}
