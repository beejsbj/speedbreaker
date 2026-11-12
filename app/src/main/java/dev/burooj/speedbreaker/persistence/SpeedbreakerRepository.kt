package dev.burooj.speedbreaker.persistence

import android.content.Context
import androidx.room.Room
import dev.burooj.speedbreaker.model.BreathChoices
import dev.burooj.speedbreaker.model.BreathEnding
import dev.burooj.speedbreaker.model.BreathStats
import dev.burooj.speedbreaker.model.PauseState
import dev.burooj.speedbreaker.model.Settings
import dev.burooj.speedbreaker.model.detourDestinations
import dev.burooj.speedbreaker.model.localDayKey
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Process-local facade over the only persisted product data. */
internal class SpeedbreakerRepository internal constructor(
    private val dao: StateDao,
    /** The local day, for dropping day totals that have aged past a week. */
    private val today: () -> String = { localDayKey(System.currentTimeMillis(), ZoneId.systemDefault()) },
) {
    private constructor(context: Context) : this(
        Room.databaseBuilder(
            context.applicationContext,
            SpeedbreakerDatabase::class.java,
            "speedbreaker.db",
        ).build().stateDao(),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Covers loading, writes, and publication so readers never see reordered state. */
    private val stateMutex = Mutex()

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    private val _pauses = MutableStateFlow<Map<String, PauseState>>(emptyMap())
    val pauses: StateFlow<Map<String, PauseState>> = _pauses.asStateFlow()
    private val _stats = MutableStateFlow(BreathStats())
    /** Breathing totals are a keepsake, not enforcement state: they never raise [error]. */
    val stats: StateFlow<BreathStats> = _stats.asStateFlow()
    private val _choices = MutableStateFlow(BreathChoices())
    /** What followed the breaths. A keepsake like [stats]: it never raises [error] either. */
    val choices: StateFlow<BreathChoices> = _choices.asStateFlow()
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    /** A persistence failure means enforcement must remain inactive. */
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        scope.launch {
            try {
                stateMutex.withLock {
                    val loadedSettings = dao.read(SETTINGS_KEY)?.value
                        ?.let(SettingsJson::decodeSettings)
                        ?: Settings()
                    val loadedPauses = dao.read(PAUSES_KEY)?.value
                        ?.let(SettingsJson::decodePauses)
                        ?: emptyMap()
                    // Absent on installs from before the count, and unreadable means start again.
                    val loadedStats = runCatching {
                        dao.read(STATS_KEY)?.value?.let(SettingsJson::decodeStats)
                    }.getOrNull() ?: BreathStats()
                    // Absent until the first breath after the version that began counting choices.
                    val loadedChoices = runCatching {
                        dao.read(CHOICES_KEY)?.value?.let(SettingsJson::decodeChoices)
                    }.getOrNull() ?: BreathChoices()
                    _settings.value = loadedSettings
                    _stats.value = loadedStats
                    _choices.value = loadedChoices
                    _pauses.value = loadedPauses
                    _ready.value = true
                    // Nothing stays past its time: a day older than a week, or the count of an app
                    // whose removal as a detour was saved but whose count was not cleared then.
                    writeChoices(tidied(loadedChoices, loadedSettings))
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _error.value = "Speedbreaker’s local settings could not be opened."
            }
        }
    }

    suspend fun setSettings(settings: Settings) {
        updateSettings { settings }
    }

    suspend fun updateSettings(transform: (Settings) -> Settings) {
        stateMutex.withLock {
            if (!_ready.value || _error.value != null) return@withLock
            val checked = SettingsJson.validateSettings(transform(_settings.value))
            if (checked == _settings.value) return
            try {
                persistAndPublish(StateRecord(SETTINGS_KEY, SettingsJson.encodeSettings(checked))) {
                    _settings.value = checked
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _error.value = "Speedbreaker couldn’t save your settings."
                return
            }
            // An app that stops being a detour takes its count with it.
            writeChoices(tidied(_choices.value, checked))
        }
    }

    suspend fun savePauses(pauses: Map<String, PauseState>) {
        val checked = SettingsJson.validatePauses(pauses)
        stateMutex.withLock {
            if (checked == _pauses.value) return
            try {
                persistAndPublish(StateRecord(PAUSES_KEY, SettingsJson.encodePauses(checked))) {
                    _pauses.value = checked
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _error.value = "Speedbreaker couldn’t save active pauses."
            }
        }
    }

    /**
     * Adds one finished breath to the running totals, with how it ended and, for a detour, where
     * it went. Both records change under one lock, so a reset or a shutdown cannot fall between
     * them. A failed write is dropped silently.
     */
    suspend fun recordBreath(
        shownMs: Long,
        today: String,
        ending: BreathEnding = BreathEnding.Other,
        detour: String? = null,
    ) {
        stateMutex.withLock {
            if (!_ready.value) return
            writeStats(_stats.value.withBreath(shownMs, today))
            val chosen = _settings.value.detourDestinations()
            writeChoices(
                _choices.value.keepingDetours(chosen).withBreath(shownMs, today, ending, detour?.takeIf { it in chosen }),
            )
        }
    }

    /** Clears the total and everything counted beside it. */
    suspend fun resetStats() {
        stateMutex.withLock {
            if (!_ready.value) return
            writeStats(BreathStats())
            writeChoices(BreathChoices())
        }
    }

    /** Called with stateMutex held. Losing a count must never stop enforcement or surface an error. */
    private suspend fun writeStats(updated: BreathStats) {
        if (updated == _stats.value) return
        try {
            persistAndPublish(StateRecord(STATS_KEY, SettingsJson.encodeStats(updated))) {
                _stats.value = updated
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
        }
    }

    /** Called with stateMutex held. Like the total, a lost write never surfaces or stops enforcement. */
    private suspend fun writeChoices(updated: BreathChoices) {
        if (updated == _choices.value) return
        try {
            persistAndPublish(StateRecord(CHOICES_KEY, SettingsJson.encodeChoices(updated))) {
                _choices.value = updated
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
        }
    }

    /** Drops what must not be kept: counts of apps no longer a detour, and days older than a week. */
    private fun tidied(choices: BreathChoices, settings: Settings): BreathChoices = runCatching {
        choices.keepingDetours(settings.detourDestinations()).withinWeekOf(today())
    }.getOrDefault(choices)

    /** Notification fallback when the Accessibility service is disconnected. */
    suspend fun endPause(packageName: String, nowEpochMs: Long): Boolean = stateMutex.withLock {
        if (!_ready.value || _error.value != null) return@withLock false
        val current = _pauses.value[packageName] ?: return@withLock false
        if (current.untilEpochMs <= nowEpochMs) return@withLock false
        val updated = _pauses.value + (packageName to current.copy(untilEpochMs = 0))
        try {
            persistAndPublish(StateRecord(PAUSES_KEY, SettingsJson.encodePauses(updated))) {
                _pauses.value = updated
            }
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            _error.value = "Speedbreaker couldn’t end this pause."
            false
        }
    }

    /**
     * Called with stateMutex held. Cancellation before acceptance prevents a write; afterward,
     * finish both the synchronous commit and publication before releasing the mutex. The
     * dispatched return can still throw cancellation, but cannot leave a committed row unpublished.
     */
    private suspend fun persistAndPublish(record: StateRecord, publish: () -> Unit) {
        // Mutex.lock need not check cancellation when it acquires an unlocked mutex immediately.
        currentCoroutineContext().ensureActive()
        withContext(Dispatchers.IO + NonCancellable) {
            dao.write(record)
            publish()
        }
        // Returning to the same dispatcher has no prompt cancellation check of its own.
        currentCoroutineContext().ensureActive()
    }

    companion object {
        private const val SETTINGS_KEY = "settings"
        private const val PAUSES_KEY = "pauses"
        private const val STATS_KEY = "breath_stats"
        private const val CHOICES_KEY = "breath_choices"
        @Volatile private var instance: SpeedbreakerRepository? = null

        fun get(context: Context): SpeedbreakerRepository = instance ?: synchronized(this) {
            instance ?: SpeedbreakerRepository(context.applicationContext).also { instance = it }
        }
    }
}
