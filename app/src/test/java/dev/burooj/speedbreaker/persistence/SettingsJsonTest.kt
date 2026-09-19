package dev.burooj.speedbreaker.persistence

import dev.burooj.speedbreaker.model.AppPolicy
import dev.burooj.speedbreaker.model.Settings
import dev.burooj.speedbreaker.model.TimeWindow
import dev.burooj.speedbreaker.model.WeeklySchedule
import org.junit.Assert.assertEquals
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
}
