package dev.burooj.speedbreaker.persistence

import dev.burooj.speedbreaker.model.BreathStats
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

    private suspend fun loaded(dao: StateDao) = SpeedbreakerRepository(dao).also {
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
}
