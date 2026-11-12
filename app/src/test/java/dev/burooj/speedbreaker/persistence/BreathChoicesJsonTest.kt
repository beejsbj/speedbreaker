package dev.burooj.speedbreaker.persistence

import dev.burooj.speedbreaker.model.BreathChoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BreathChoicesJsonTest {
    private val sample = BreathChoices(
        since = "2026-11-12",
        left = 41,
        continued = 38,
        detoured = 12,
        timeOff = 3,
        other = 2,
        detours = mapOf("com.amazon.kindle" to 6L, "com.duolingo" to 2L),
        days = mapOf("2026-11-11" to 96_000L, "2026-11-12" to 120_000L),
    )

    @Test
    fun roundTripsEveryField() {
        assertEquals(sample, SettingsJson.decodeChoices(SettingsJson.encodeChoices(sample)))
        assertEquals(BreathChoices(), SettingsJson.decodeChoices(SettingsJson.encodeChoices(BreathChoices())))
    }

    @Test
    fun storesNothingBeyondCountsDayTotalsAndDetourPackages() {
        val fields = buildSet { org.json.JSONObject(SettingsJson.encodeChoices(sample)).keys().forEach(::add) }
        assertEquals(setOf("since", "left", "continued", "detoured", "timeOff", "other", "detours", "days"), fields)
    }

    @Test
    fun rejectsNegativeCountsBadDaysAndUnknownFields() {
        val good = SettingsJson.encodeChoices(sample)
        listOf(
            good.replace("\"left\":41", "\"left\":-1"),
            good.replace("2026-11-11", "yesterday"),
            good.replace("\"since\":\"2026-11-12\"", "\"since\":\"12 Nov\""),
            good.replace("\"com.duolingo\":2", "\"com.duolingo\":-2"),
            good.replace("\"other\":2", "\"other\":2,\"apps\":{}"),
        ).forEach { bad ->
            assertThrows(Exception::class.java) { SettingsJson.decodeChoices(bad) }
        }
    }
}
