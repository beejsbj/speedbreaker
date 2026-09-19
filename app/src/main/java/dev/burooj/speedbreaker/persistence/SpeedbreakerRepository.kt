package dev.burooj.speedbreaker.persistence

import android.content.Context
import androidx.room.Room
import dev.burooj.speedbreaker.model.AppPolicy
import dev.burooj.speedbreaker.model.PauseState
import dev.burooj.speedbreaker.model.Settings
import dev.burooj.speedbreaker.model.TimeWindow
import dev.burooj.speedbreaker.model.WeeklySchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Process-local facade over the only persisted product data. */
internal class SpeedbreakerRepository private constructor(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database = Room.databaseBuilder(
        context.applicationContext,
        SpeedbreakerDatabase::class.java,
        "speedbreaker.db",
    ).build()
    /** Covers loading, writes, and publication so readers never see reordered state. */
    private val stateMutex = Mutex()

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    private val _pauses = MutableStateFlow<Map<String, PauseState>>(emptyMap())
    val pauses: StateFlow<Map<String, PauseState>> = _pauses.asStateFlow()
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    /** A persistence failure means enforcement must remain inactive. */
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        scope.launch {
            try {
                stateMutex.withLock {
                    val dao = database.stateDao()
                    val loadedSettings = dao.read(SETTINGS_KEY)?.value
                        ?.let(SettingsJson::decodeSettings)
                        ?: Settings()
                    val loadedPauses = dao.read(PAUSES_KEY)?.value
                        ?.let(SettingsJson::decodePauses)
                        ?: emptyMap()
                    _settings.value = loadedSettings
                    _pauses.value = loadedPauses
                    _ready.value = true
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _error.value = "Speedbreaker’s local settings could not be opened."
            }
        }
    }

    suspend fun setSettings(settings: Settings) {
        val checked = SettingsJson.validateSettings(settings)
        stateMutex.withLock {
            if (checked == _settings.value) return
            try {
                withContext(Dispatchers.IO) {
                    database.stateDao().write(StateRecord(SETTINGS_KEY, SettingsJson.encodeSettings(checked)))
                }
                _settings.value = checked
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _error.value = "Speedbreaker couldn’t save your settings."
            }
        }
    }

    suspend fun savePauses(pauses: Map<String, PauseState>) {
        val checked = SettingsJson.validatePauses(pauses)
        stateMutex.withLock {
            if (checked == _pauses.value) return
            try {
                withContext(Dispatchers.IO) {
                    database.stateDao().write(StateRecord(PAUSES_KEY, SettingsJson.encodePauses(checked)))
                }
                _pauses.value = checked
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _error.value = "Speedbreaker couldn’t save active pauses."
            }
        }
    }

    companion object {
        private const val SETTINGS_KEY = "settings"
        private const val PAUSES_KEY = "pauses"
        @Volatile private var instance: SpeedbreakerRepository? = null

        fun get(context: Context): SpeedbreakerRepository = instance ?: synchronized(this) {
            instance ?: SpeedbreakerRepository(context.applicationContext).also { instance = it }
        }
    }
}

internal object SettingsJson {
    fun validateSettings(input: Settings): Settings {
        val targets = input.apps.keys.filter { it.isNotBlank() }.toSet()
        val apps = input.apps.filterKeys { it in targets }.mapValues { (_, policy) ->
            policy.copy(
                continuousSeconds = policy.continuousSeconds?.coerceIn(60, 24 * 60 * 60),
                schedule = policy.schedule?.let(::validateSchedule),
                redirects = policy.redirects?.let { validateRedirects(it, targets) },
            )
        }
        return input.copy(
            breathSeconds = input.breathSeconds.coerceIn(8, 60),
            apps = apps,
            redirects = validateRedirects(input.redirects, targets),
            schedule = input.schedule?.let(::validateSchedule),
        )
    }

    fun validatePauses(input: Map<String, PauseState>): Map<String, PauseState> = input
        .filterKeys { it.isNotBlank() }
        .mapValues { (_, pause) -> pause.copy(used = pause.used.coerceIn(0, 2), untilEpochMs = pause.untilEpochMs.coerceAtLeast(0)) }

    private fun validateRedirects(values: List<String>, targets: Set<String>): List<String> {
        val distinct = values.filter { it.isNotBlank() && it !in targets }.distinct()
        return if (distinct.size == 4) distinct else emptyList()
    }

    private fun validateSchedule(schedule: WeeklySchedule): WeeklySchedule = WeeklySchedule(
        schedule.days.filter { (day, window) ->
            day in 1..7 && window.startMinute in 0..1439 && window.endMinute in 0..1440 &&
                window.startMinute != window.endMinute
        },
    )

    fun encodeSettings(settings: Settings): String = JSONObject().apply {
        put("consent", settings.consentAccepted)
        put("breath", settings.breathSeconds)
        put("redirects", array(settings.redirects))
        put("schedule", schedule(settings.schedule))
        put("apps", JSONObject().apply {
            settings.apps.forEach { (packageName, policy) -> put(packageName, JSONObject().apply {
                put("continuous", policy.continuousSeconds ?: JSONObject.NULL)
                put("schedule", schedule(policy.schedule))
                put("redirects", policy.redirects?.let(::array) ?: JSONObject.NULL)
            }) }
        })
    }.toString()

    fun encodePauses(pauses: Map<String, PauseState>): String = JSONObject().apply {
        pauses.forEach { (packageName, pause) -> put(packageName, JSONObject().apply {
            put("day", pause.day); put("used", pause.used); put("until", pause.untilEpochMs)
        }) }
    }.toString()

    fun decodeSettings(json: String): Settings {
        val root = JSONObject(json)
        requireFields(root, "consent", "breath", "redirects", "schedule", "apps")
        val appsJson = root.getJSONObject("apps")
        val apps = buildMap {
            appsJson.keys().forEach { packageName ->
                val item = appsJson.getJSONObject(packageName)
                requireFields(item, "continuous", "schedule", "redirects")
                put(packageName, AppPolicy(
                    continuousSeconds = if (item.isNull("continuous")) null else item.optInt("continuous", 600),
                    schedule = schedule(item.opt("schedule")),
                    redirects = if (item.isNull("redirects")) null else strings(item.optJSONArray("redirects")),
                ))
            }
        }
        return validateSettings(Settings(
            consentAccepted = root.optBoolean("consent", false),
            breathSeconds = root.optInt("breath", 12), apps = apps,
            redirects = strings(root.optJSONArray("redirects")), schedule = schedule(root.opt("schedule")),
        ))
    }

    fun decodePauses(json: String): Map<String, PauseState> {
        val root = JSONObject(json)
        return validatePauses(buildMap {
            root.keys().forEach { packageName ->
                val item = root.getJSONObject(packageName)
                requireFields(item, "day", "used", "until")
                put(packageName, PauseState(item.optString("day"), item.optInt("used"), item.optLong("until")))
            }
        })
    }

    private fun array(values: List<String>) = JSONArray().apply { values.forEach(::put) }

    private fun requireFields(objectValue: JSONObject, vararg names: String) {
        require(names.all(objectValue::has)) { "Incomplete persisted Speedbreaker state." }
    }
    private fun strings(values: JSONArray?): List<String> = buildList {
        values?.let { array -> for (index in 0 until array.length()) array.optString(index).takeIf(String::isNotBlank)?.let(::add) }
    }
    private fun schedule(value: WeeklySchedule?): Any = value?.let { schedule -> JSONObject().apply {
        schedule.days.forEach { (day, window) -> put(day.toString(), JSONObject().apply {
            put("start", window.startMinute); put("end", window.endMinute)
        }) }
    } } ?: JSONObject.NULL
    private fun schedule(value: Any?): WeeklySchedule? {
        val objectValue = value as? JSONObject ?: return null
        return WeeklySchedule(buildMap {
            objectValue.keys().forEach { key ->
                val day = key.toIntOrNull() ?: return@forEach
                val item = objectValue.optJSONObject(key) ?: return@forEach
                put(day, TimeWindow(item.optInt("start", -1), item.optInt("end", -1)))
            }
        }).let(::validateSchedule)
    }
}
