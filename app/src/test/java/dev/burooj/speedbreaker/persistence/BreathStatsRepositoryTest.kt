package dev.burooj.speedbreaker.persistence

import dev.burooj.speedbreaker.model.AppPolicy
import dev.burooj.speedbreaker.model.BreathChoices
import dev.burooj.speedbreaker.model.BreathEnding
import dev.burooj.speedbreaker.model.BreathStats
import dev.burooj.speedbreaker.model.Settings
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BreathStatsRepositoryTest {
    private class MapDao : StateDao {
        val rows = ConcurrentHashMap<String, StateRecord>()
        @Volatile var failWrites = false
        override fun read(key: String): StateRecord? = rows[key]
        override fun write(record: StateRecord) {
            if (failWrites) throw IllegalStateException("Simulated disk write failure")
            rows[record.key] = record
        }
    }

    /** Loaded as if opened on [today], so the day totals these tests write stay within its week. */
    private suspend fun loaded(dao: StateDao, today: String = "2026-11-12") =
        SpeedbreakerRepository(dao, today = { today }).also {
            withTimeout(5_000) { it.ready.first { ready -> ready } }
        }

    @Test(timeout = 15_000)
    fun existingInstallsWithoutAStatsRow_startAtZero() = runBlocking {
        val repository = loaded(MapDao())
        assertEquals(BreathStats(), repository.stats.value)
        assertNull(repository.error.value)
    }

    @Test(timeout = 15_000)
    fun recordedBreaths_surviveReloadAndRollOverTheDay() = runBlocking {
        val dao = MapDao()
        val repository = loaded(dao)
        repository.recordBreath(8_000, "2026-10-16")
        repository.recordBreath(12_000, "2026-10-16")
        assertEquals(BreathStats(20_000, 2, "2026-10-16", 20_000), loaded(dao).stats.value)

        repository.recordBreath(5_000, "2026-10-17")
        assertEquals(BreathStats(25_000, 3, "2026-10-17", 5_000), loaded(dao).stats.value)
    }

    @Test(timeout = 15_000)
    fun reset_zeroesEveryField() = runBlocking {
        val dao = MapDao()
        val repository = loaded(dao)
        repository.recordBreath(8_000, "2026-10-16")
        repository.resetStats()
        assertEquals(BreathStats(), repository.stats.value)
        assertEquals(BreathStats(), loaded(dao).stats.value)
    }

    @Test(timeout = 15_000)
    fun failedStatsWrite_isIgnoredAndNeverRaisesAnError() = runBlocking {
        val dao = MapDao()
        val repository = loaded(dao)
        repository.recordBreath(8_000, "2026-10-16")
        dao.failWrites = true
        repository.recordBreath(9_000, "2026-10-16")

        assertEquals(BreathStats(8_000, 1, "2026-10-16", 8_000), repository.stats.value)
        assertNull(repository.error.value)
    }

    @Test(timeout = 15_000)
    fun unreadableStatsRow_startsOverWithoutBlockingSettings() = runBlocking {
        val dao = MapDao()
        dao.rows["breath_stats"] = StateRecord("breath_stats", "{\"totalMs\":-5}")
        val repository = loaded(dao)
        assertEquals(BreathStats(), repository.stats.value)
        assertNull(repository.error.value)
    }

    @Test(timeout = 15_000)
    fun anInstallFromBeforeChoicesWereCounted_keepsItsTotalAndStartsChoicesAtZero() = runBlocking {
        val dao = MapDao()
        dao.rows["breath_stats"] = StateRecord(
            "breath_stats",
            "{\"totalMs\":1080000,\"totalBreaths\":94,\"todayKey\":\"2026-11-11\",\"todayMs\":120000}",
        )
        val repository = loaded(dao)
        assertEquals(BreathStats(1_080_000, 94, "2026-11-11", 120_000), repository.stats.value)
        assertEquals(BreathChoices(), repository.choices.value)

        repository.recordBreath(12_000, "2026-11-12", BreathEnding.Left)
        assertEquals(95L, repository.stats.value.totalBreaths)
        assertEquals(1L, repository.choices.value.total)
        assertEquals("2026-11-12", repository.choices.value.since)
    }

    @Test(timeout = 15_000)
    fun howABreathEnded_survivesReload() = runBlocking {
        val dao = MapDao()
        val repository = loaded(dao)
        repository.setSettings(Settings(consentAccepted = true, redirects = listOf("kindle", "notes")))
        repository.recordBreath(8_000, "2026-11-12", BreathEnding.Left)
        repository.recordBreath(8_000, "2026-11-12", BreathEnding.Continued)
        repository.recordBreath(8_000, "2026-11-12", BreathEnding.Detoured, "kindle")
        repository.recordBreath(8_000, "2026-11-12", BreathEnding.TimeOff)
        repository.recordBreath(8_000, "2026-11-12")

        val expected = BreathChoices(
            since = "2026-11-12",
            left = 1,
            continued = 1,
            detoured = 1,
            timeOff = 1,
            other = 1,
            detours = mapOf("kindle" to 1L),
            days = mapOf("2026-11-12" to 40_000L),
        )
        assertEquals(expected, repository.choices.value)
        assertEquals(expected, loaded(dao).choices.value)
        assertEquals(repository.stats.value.totalBreaths, repository.choices.value.total)
    }

    @Test(timeout = 15_000)
    fun aDetourThatIsNoLongerChosen_isCountedButNotByName() = runBlocking {
        val repository = loaded(MapDao())
        repository.setSettings(Settings(consentAccepted = true, redirects = listOf("kindle")))
        repository.recordBreath(8_000, "2026-11-12", BreathEnding.Detoured, "maps")
        assertEquals(1L, repository.choices.value.detoured)
        assertEquals(emptyMap<String, Long>(), repository.choices.value.detours)
    }

    @Test(timeout = 15_000)
    fun removingADetourEverywhere_dropsItsCountAndKeepingItSomewhereDoesNot() = runBlocking {
        val dao = MapDao()
        val repository = loaded(dao)
        val both = Settings(
            consentAccepted = true,
            apps = mapOf("target.a" to AppPolicy(redirects = listOf("notes"))),
            redirects = listOf("kindle", "notes"),
        )
        repository.setSettings(both)
        repository.recordBreath(8_000, "2026-11-12", BreathEnding.Detoured, "kindle")
        repository.recordBreath(8_000, "2026-11-12", BreathEnding.Detoured, "notes")

        // Notes leaves the default set but is still target.a's own detour.
        repository.setSettings(both.copy(redirects = listOf("kindle")))
        assertEquals(mapOf("kindle" to 1L, "notes" to 1L), repository.choices.value.detours)

        repository.setSettings(both.copy(redirects = emptyList()))
        assertEquals(mapOf("notes" to 1L), repository.choices.value.detours)
        assertEquals(mapOf("notes" to 1L), loaded(dao).choices.value.detours)
        assertEquals(2L, repository.choices.value.detoured)
    }

    @Test(timeout = 15_000)
    fun reset_clearsTheChoicesToo() = runBlocking {
        val dao = MapDao()
        val repository = loaded(dao)
        repository.recordBreath(8_000, "2026-11-12", BreathEnding.Left)
        repository.resetStats()
        assertEquals(BreathChoices(), repository.choices.value)
        assertEquals(BreathChoices(), loaded(dao).choices.value)
        assertEquals(BreathStats(), loaded(dao).stats.value)
    }

    @Test(timeout = 15_000)
    fun unreadableChoicesRow_startsOverWithoutTouchingTheTotalOrSettings() = runBlocking {
        val dao = MapDao()
        val repository = loaded(dao)
        repository.recordBreath(8_000, "2026-11-12", BreathEnding.Left)
        dao.rows["breath_choices"] = StateRecord("breath_choices", "{\"left\":-1}")
        val reloaded = loaded(dao)
        assertEquals(BreathChoices(), reloaded.choices.value)
        assertEquals(1L, reloaded.stats.value.totalBreaths)
        assertNull(reloaded.error.value)
    }

    @Test(timeout = 15_000)
    fun aFailedChoicesWrite_isIgnoredAndNeverRaisesAnError() = runBlocking {
        val dao = MapDao()
        val repository = loaded(dao)
        repository.recordBreath(8_000, "2026-11-12", BreathEnding.Left)
        dao.failWrites = true
        repository.recordBreath(8_000, "2026-11-12", BreathEnding.Continued)
        assertEquals(1L, repository.choices.value.total)
        assertNull(repository.error.value)
    }

    @Test(timeout = 15_000)
    fun openingTheAppMoreThanAWeekLater_dropsTheOldDayTotalsFromDisk() = runBlocking {
        val dao = MapDao()
        val repository = loaded(dao)
        repository.recordBreath(8_000, "2026-11-10", BreathEnding.Left)
        repository.recordBreath(8_000, "2026-11-12", BreathEnding.Left)

        val later = loaded(dao, today = "2026-11-18")
        assertEquals(mapOf("2026-11-12" to 8_000L), later.choices.value.days)
        assertEquals(
            mapOf("2026-11-12" to 8_000L),
            SettingsJson.decodeChoices(dao.rows.getValue("breath_choices").value).days,
        )
        // The counts are not history by day, and stay.
        assertEquals(2L, later.choices.value.left)
        assertEquals(emptyMap<String, Long>(), loaded(dao, today = "2026-12-25").choices.value.days)
    }

    @Test(timeout = 15_000)
    fun aDetourCountLeftBehindByAFailedWrite_isClearedOnTheNextOpenOrBreath() = runBlocking {
        val dao = MapDao()
        val repository = loaded(dao)
        repository.setSettings(Settings(consentAccepted = true, redirects = listOf("kindle", "notes")))
        repository.recordBreath(8_000, "2026-11-12", BreathEnding.Detoured, "kindle")
        // Settings saved without Kindle, but the count's own row still names it.
        dao.rows["settings"] = StateRecord(
            "settings",
            SettingsJson.encodeSettings(Settings(consentAccepted = true, redirects = listOf("notes"))),
        )

        val reopened = loaded(dao)
        assertEquals(emptyMap<String, Long>(), reopened.choices.value.detours)
        assertEquals(
            emptyMap<String, Long>(),
            SettingsJson.decodeChoices(dao.rows.getValue("breath_choices").value).detours,
        )
        assertEquals(1L, reopened.choices.value.detoured)
    }

    @Test(timeout = 15_000)
    fun aResetBetweenBreaths_neverLeavesTheTotalAndTheEndingsApart() = runBlocking {
        val repository = loaded(MapDao())
        repository.recordBreath(8_000, "2026-11-12", BreathEnding.Left)
        repository.resetStats()
        repository.recordBreath(8_000, "2026-11-12", BreathEnding.Continued)
        assertEquals(repository.stats.value.totalBreaths, repository.choices.value.total)
        assertEquals(1L, repository.choices.value.continued)
    }
}
