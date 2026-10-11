package dev.burooj.speedbreaker.persistence

import dev.burooj.speedbreaker.model.AppPolicy
import dev.burooj.speedbreaker.model.PauseState
import dev.burooj.speedbreaker.model.Settings
import dev.burooj.speedbreaker.model.TimeWindow
import dev.burooj.speedbreaker.model.WeeklySchedule
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

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
        .mapValues { (_, pause) ->
            pause.copy(
                used = pause.used.coerceIn(0, 2),
                untilEpochMs = pause.untilEpochMs.coerceAtLeast(0),
            )
        }

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
            settings.apps.forEach { (packageName, policy) ->
                put(packageName, JSONObject().apply {
                    put("continuous", policy.continuousSeconds ?: JSONObject.NULL)
                    put("schedule", schedule(policy.schedule))
                    put("redirects", policy.redirects?.let(::array) ?: JSONObject.NULL)
                })
            }
        })
    }.toString()

    fun encodePauses(pauses: Map<String, PauseState>): String = JSONObject().apply {
        pauses.forEach { (packageName, pause) ->
            put(packageName, JSONObject().apply {
                put("day", pause.day)
                put("used", pause.used)
                put("until", pause.untilEpochMs)
            })
        }
    }.toString()

    fun decodeSettings(json: String): Settings {
        val root = JSONObject(json)
        requireExactFields(root, "consent", "breath", "redirects", "schedule", "apps")
        val appsJson = objectField(root, "apps")
        val apps = buildMap {
            appsJson.keys().forEach { packageName ->
                require(packageName.isNotBlank()) { "Target package name must not be blank." }
                val item = objectField(appsJson, packageName)
                requireExactFields(item, "continuous", "schedule", "redirects")
                put(
                    packageName,
                    AppPolicy(
                        continuousSeconds = nullableIntField(item, "continuous", 60..24 * 60 * 60),
                        schedule = schedule(field(item, "schedule")),
                        redirects = nullableStringsField(item, "redirects"),
                    ),
                )
            }
        }
        val settings = Settings(
            consentAccepted = booleanField(root, "consent"),
            breathSeconds = intField(root, "breath", 8..60),
            apps = apps,
            redirects = strings(arrayField(root, "redirects")),
            schedule = schedule(field(root, "schedule")),
        )
        require(validateSettings(settings) == settings) {
            "Persisted settings violate product constraints."
        }
        return settings
    }

    fun decodePauses(json: String): Map<String, PauseState> {
        val root = JSONObject(json)
        return buildMap {
            root.keys().forEach { packageName ->
                require(packageName.isNotBlank()) { "Pause package name must not be blank." }
                val item = objectField(root, packageName)
                requireExactFields(item, "day", "used", "until")
                val day = stringField(item, "day")
                require(
                    day.isNotBlank() &&
                        runCatching { LocalDate.parse(day).toString() == day }.getOrDefault(false),
                ) { "Pause day must be an ISO local date." }
                put(
                    packageName,
                    PauseState(
                        day = day,
                        used = intField(item, "used", 0..2),
                        untilEpochMs = longField(item, "until", minimum = 0),
                    ),
                )
            }
        }
    }

    private fun array(values: List<String>) = JSONArray().apply { values.forEach(::put) }

    private fun requireExactFields(objectValue: JSONObject, vararg names: String) {
        val actual = buildSet { objectValue.keys().forEach(::add) }
        require(actual == names.toSet()) { "Persisted Speedbreaker state has unexpected fields." }
    }

    private fun field(objectValue: JSONObject, name: String): Any = objectValue.get(name)

    private fun objectField(objectValue: JSONObject, name: String): JSONObject =
        field(objectValue, name) as? JSONObject
            ?: throw IllegalArgumentException("$name must be an object.")

    private fun arrayField(objectValue: JSONObject, name: String): JSONArray =
        field(objectValue, name) as? JSONArray
            ?: throw IllegalArgumentException("$name must be an array.")

    private fun booleanField(objectValue: JSONObject, name: String): Boolean =
        field(objectValue, name) as? Boolean
            ?: throw IllegalArgumentException("$name must be a boolean.")

    private fun stringField(objectValue: JSONObject, name: String): String =
        field(objectValue, name) as? String
            ?: throw IllegalArgumentException("$name must be a string.")

    private fun intField(objectValue: JSONObject, name: String, range: IntRange): Int {
        val value = when (val raw = field(objectValue, name)) {
            is Int -> raw
            is Long -> raw.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
            else -> null
        } ?: throw IllegalArgumentException("$name must be an integer.")
        require(value in range) { "$name is outside its allowed range." }
        return value
    }

    private fun nullableIntField(objectValue: JSONObject, name: String, range: IntRange): Int? =
        if (objectValue.isNull(name)) null else intField(objectValue, name, range)

    private fun longField(objectValue: JSONObject, name: String, minimum: Long): Long {
        val value = when (val raw = field(objectValue, name)) {
            is Int -> raw.toLong()
            is Long -> raw
            else -> null
        } ?: throw IllegalArgumentException("$name must be an integer.")
        require(value >= minimum) { "$name is outside its allowed range." }
        return value
    }

    private fun nullableStringsField(objectValue: JSONObject, name: String): List<String>? =
        if (objectValue.isNull(name)) null else strings(arrayField(objectValue, name))

    private fun strings(values: JSONArray): List<String> = buildList {
        for (index in 0 until values.length()) {
            val value = values.get(index) as? String
                ?: throw IllegalArgumentException("Redirect packages must be strings.")
            require(value.isNotBlank()) { "Redirect package name must not be blank." }
            add(value)
        }
    }

    private fun schedule(value: WeeklySchedule?): Any = value?.let { schedule ->
        JSONObject().apply {
            schedule.days.forEach { (day, window) ->
                put(day.toString(), JSONObject().apply {
                    put("start", window.startMinute)
                    put("end", window.endMinute)
                })
            }
        }
    } ?: JSONObject.NULL

    private fun schedule(value: Any?): WeeklySchedule? {
        if (value == JSONObject.NULL) return null
        val objectValue = value as? JSONObject
            ?: throw IllegalArgumentException("Schedule must be an object or null.")
        return WeeklySchedule(buildMap {
            objectValue.keys().forEach { key ->
                val day = key.toIntOrNull()
                    ?: throw IllegalArgumentException("Schedule day must be numeric.")
                require(day in 1..7) { "Schedule day is outside its allowed range." }
                require(key == day.toString()) { "Schedule day must use its canonical number." }
                val item = objectField(objectValue, key)
                requireExactFields(item, "start", "end")
                val start = intField(item, "start", 0..1439)
                val end = intField(item, "end", 0..1440)
                require(start != end) { "Schedule start and end must differ." }
                put(day, TimeWindow(start, end))
            }
        })
    }
}
