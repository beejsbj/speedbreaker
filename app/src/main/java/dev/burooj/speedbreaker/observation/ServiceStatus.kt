package dev.burooj.speedbreaker.observation

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object ServiceStatus {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun isEnabled(context: Context): Boolean {
        val expected = ComponentName(context, SpeedbreakerService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return enabled.split(':').any { flattened ->
            ComponentName.unflattenFromString(flattened) == expected
        }
    }

    internal fun setConnected(value: Boolean) {
        _connected.value = value
        if (value) _error.value = null
    }

    internal fun reportError(message: String?) {
        _error.value = message
    }
}
