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

internal class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = SpeedbreakerRepository.get(appContext)
                val ready = withTimeoutOrNull(8_000L) { repository.ready.first { it } } == true
                if (ready && repository.error.value == null) {
                    PauseNotifications(appContext).reconcile(
                        repository.pauses.value,
                        System.currentTimeMillis(),
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }
}
