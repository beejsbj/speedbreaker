package dev.burooj.speedbreaker.persistence

import dev.burooj.speedbreaker.model.AppPolicy
import dev.burooj.speedbreaker.model.PauseState
import dev.burooj.speedbreaker.model.Settings
import dev.burooj.speedbreaker.model.TimeWindow
import dev.burooj.speedbreaker.model.WeeklySchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsJsonTest {
    @Test
    fun validation_keepsOnlyFourDistinctNonTargetRedirects() {
        val settings = Settings(
            apps = mapOf("target" to AppPolicy()),
            redirects = listOf("one", "two", "three", "target"),
        )

        assertTrue(SettingsJson.validateSettings(settings).redirects.isEmpty())
    }

    @Test
    fun validation_allowsExplicitEndOfDayButRejectsInvalidWindows() {
        val schedule = WeeklySchedule(
            mapOf(
                1 to TimeWindow(0, 1440),
                2 to TimeWindow(1440, 10),
                3 to TimeWindow(20, 20),
            ),
        )

        val result = SettingsJson.validateSettings(Settings(schedule = schedule)).schedule

        assertEquals(mapOf(1 to TimeWindow(0, 1440)), result?.days)
    }

    @Test
    fun settingsCodec_roundTripsCompleteValidatedState() {
        val settings = Settings(
            consentAccepted = true,
            breathSeconds = 60,
            apps = mapOf(
                "target.one" to AppPolicy(
                    continuousSeconds = null,
                    schedule = WeeklySchedule(mapOf(1 to TimeWindow(22 * 60, 2 * 60))),
                    redirects = listOf("alt.one", "alt.two", "alt.three", "alt.four"),
                ),
                "target.two" to AppPolicy(
                    continuousSeconds = 60,
                    schedule = WeeklySchedule(mapOf(7 to TimeWindow(0, 1440))),
                    redirects = emptyList(),
                ),
            ),
            redirects = listOf("alt.five", "alt.six", "alt.seven", "alt.eight"),
            schedule = WeeklySchedule(mapOf(2 to TimeWindow(9 * 60, 17 * 60))),
        )

        assertEquals(settings, SettingsJson.decodeSettings(SettingsJson.encodeSettings(settings)))
    }

    @Test
    fun pauseCodec_roundTripsStrictCurrentDayState() {
        val pauses = mapOf(
            "target.one" to PauseState("2026-09-19", used = 2, untilEpochMs = 1_800_000_000_000),
            "target.two" to PauseState("2026-09-19", used = 0),
        )

        assertEquals(pauses, SettingsJson.decodePauses(SettingsJson.encodePauses(pauses)))
    }

    @Test
    fun settingsDecoder_rejectsWrongTypesAndOutOfRangeNestedValues() {
        val malformed = listOf(
            validSettingsJson().replace("\"consent\":true", "\"consent\":\"true\""),
            validSettingsJson().replace("\"breath\":12", "\"breath\":7"),
            validSettingsJson().replace("\"continuous\":600", "\"continuous\":\"600\""),
            validSettingsJson().replace("\"1\":{", "\"01\":{"),
            validSettingsJson().replace("\"start\":540", "\"start\":1440"),
            validSettingsJson().replace("\"end\":1020", "\"end\":540"),
            validSettingsJson().replace("\"end\":1020", "\"unexpected\":1"),
            validSettingsJson().replace("[\"alt.one\",\"alt.two\",\"alt.three\",\"alt.four\"]", "[1,\"alt.two\",\"alt.three\",\"alt.four\"]"),
        )

        malformed.forEach { json ->
            assertThrows(IllegalArgumentException::class.java) {
                SettingsJson.decodeSettings(json)
            }
        }
    }

    @Test
    fun pauseDecoder_rejectsIncompleteWrongTypeAndOutOfRangeState() {
        val malformed = listOf(
            """{"target":{"day":"not-a-date","used":1,"until":0}}""",
            """{"target":{"day":"2026-09-19","used":"1","until":0}}""",
            """{"target":{"day":"2026-09-19","used":3,"until":0}}""",
            """{"target":{"day":"2026-09-19","used":1,"until":-1}}""",
            """{"target":{"day":"2026-09-19","used":1}}""",
        )

        malformed.forEach { json ->
            assertThrows(IllegalArgumentException::class.java) {
                SettingsJson.decodePauses(json)
            }
        }
    }

    private fun validSettingsJson(): String = """
        {
          "consent": true,
          "breath": 12,
          "redirects": ["alt.one", "alt.two", "alt.three", "alt.four"],
          "schedule": {"1": {"start": 540, "end": 1020}},
          "apps": {
            "target": {
              "continuous": 600,
              "schedule": null,
              "redirects": null
            }
          }
        }
    """.trimIndent().replace(" ", "").replace("\n", "")
}
