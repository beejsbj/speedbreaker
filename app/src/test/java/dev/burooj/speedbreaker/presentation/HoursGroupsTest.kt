package dev.burooj.speedbreaker.presentation

import dev.burooj.speedbreaker.model.TimeWindow
import dev.burooj.speedbreaker.model.WeeklySchedule
import org.junit.Assert.assertEquals
import org.junit.Test

class HoursGroupsTest {
    private val work = TimeWindow(9 * 60, 17 * 60)
    private val weekend = TimeWindow(10 * 60, 1440)
    private val week = WeeklySchedule((1..5).associateWith { work } + (6 to weekend) + (7 to weekend))

    @Test
    fun groupsDaysBySharedHours() {
        assertEquals(
            listOf(HoursGroup(work, (1..5).toSet()), HoursGroup(weekend, setOf(6, 7))),
            hoursGroups(week),
        )
    }

    @Test
    fun retimingAGroupChangesEveryDayInIt() {
        val later = TimeWindow(10 * 60, 18 * 60)
        val updated = week.retime(work, later)
        assertEquals((1..5).associateWith { later } + (6 to weekend) + (7 to weekend), updated.days)
    }

    @Test
    fun retimingIntoAnotherGroupsHoursMergesThem() {
        assertEquals(listOf(HoursGroup(weekend, (1..7).toSet())), hoursGroups(week.retime(work, weekend)))
    }

    @Test
    fun assigningADayMovesItBetweenGroupsOrTurnsItOff() {
        assertEquals(work, week.assign(6, work, include = true).days[6])
        assertEquals(null, week.assign(6, weekend, include = false).days[6])
        assertEquals(setOf(1, 2, 3, 4, 5), week.withoutGroup(weekend).days.keys)
    }

    @Test
    fun newGroupsOfferHoursNotAlreadyUsed() {
        assertEquals(TimeWindow(0, 1440), newGroupWindow(WeeklySchedule(mapOf(1 to work))))
        assertEquals(work, newGroupWindow(WeeklySchedule(mapOf(1 to TimeWindow(0, 1440)))))
    }
}
