package dev.burooj.speedbreaker.presentation

import dev.burooj.speedbreaker.model.TimeWindow
import dev.burooj.speedbreaker.model.WeeklySchedule
import org.junit.Assert.assertEquals
import org.junit.Test

class SummariesTest {
    @Test
    fun namesCommonDaySetsAndRuns() {
        assertEquals("Every day", daysSummary((1..7).toSet()))
        assertEquals("Weekdays", daysSummary((1..5).toSet()))
        assertEquals("Weekends", daysSummary(setOf(6, 7)))
        assertEquals("Mon–Sat", daysSummary((1..6).toSet()))
        assertEquals("Mon, Wed, Fri", daysSummary(setOf(1, 3, 5)))
        assertEquals("Mon, Tue, Thu–Sun", daysSummary(setOf(1, 2, 4, 5, 6, 7)))
    }

    @Test
    fun lowercasesOnlySentenceWords() {
        assertEquals("weekdays · 09:00–17:00", "Weekdays · 09:00–17:00".inSentence())
        assertEquals("always on", "Always on".inSentence())
        assertEquals("Mon–Sat · varied hours", "Mon–Sat · varied hours".inSentence())
    }

    @Test
    fun summarizesSchedules() {
        assertEquals("Always on", scheduleSummary(null))
        assertEquals("Always on", scheduleSummary(allDaySchedule()))
        assertEquals("No active days", scheduleSummary(WeeklySchedule()))
        assertEquals("Weekdays · 09:00–17:00", scheduleSummary(startingSchedule()))
        val overnight = WeeklySchedule(mapOf(6 to TimeWindow(22 * 60, 1440)))
        assertEquals("Sat · 22:00–24:00", scheduleSummary(overnight))
    }
}
