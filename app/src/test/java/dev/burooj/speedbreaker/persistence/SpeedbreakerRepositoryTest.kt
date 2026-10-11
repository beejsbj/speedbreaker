package dev.burooj.speedbreaker.persistence

import dev.burooj.speedbreaker.model.AppPolicy
import dev.burooj.speedbreaker.model.PauseState
import dev.burooj.speedbreaker.model.Settings
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedbreakerRepositoryTest {
    @Test(timeout = 15_000)
    fun redirectEdits_surviveReloadAndKeepInheritanceSeparateFromExplicitEmpty() = runBlocking {
        val dao = BlockingDao(Settings(
            breathSeconds = 12,
            apps = mapOf("custom" to AppPolicy(continuousSeconds = 720), "inherited" to AppPolicy()),
        ))
        var repository = loadedRepository(dao)
        for (count in listOf(4, 3, 2, 1, 0)) {
            val destinations = (1..count).map { "alt.$it" }
            repository.updateSettings { current ->
                current.copy(
                    redirects = destinations,
                    apps = current.apps + ("custom" to current.apps.getValue("custom").copy(redirects = destinations)),
                )
            }
            repository = loadedRepository(dao)
            assertEquals(destinations, repository.settings.value.redirects)
            assertEquals(destinations, repository.settings.value.apps.getValue("custom").redirects)
            assertNull(repository.settings.value.apps.getValue("inherited").redirects)
            assertEquals(12, repository.settings.value.breathSeconds)
            assertEquals(720, repository.settings.value.apps.getValue("custom").continuousSeconds)
        }
        repository.updateSettings { current ->
            current.copy(apps = current.apps + ("custom" to current.apps.getValue("custom").copy(redirects = null)))
        }
        assertNull(loadedRepository(dao).settings.value.apps.getValue("custom").redirects)
    }

    @Test(timeout = 15_000)
    fun selectingARedirectAsATarget_removesItFromGlobalAndCustomSets() = runBlocking {
        val dao = BlockingDao(Settings(
            apps = mapOf("target" to AppPolicy(redirects = listOf("one", "two", "three"))),
            redirects = listOf("one", "two"),
        ))
        val repository = loadedRepository(dao)
        repository.updateSettings { it.copy(apps = it.apps + ("one" to AppPolicy())) }
        val reloaded = loadedRepository(dao).settings.value
        assertEquals(listOf("two"), reloaded.redirects)
        assertEquals(listOf("two", "three"), reloaded.apps.getValue("target").redirects)
    }

    @Test(timeout = 15_000)
    fun cancelledSettingsRemoval_staysPublishedAndSurvivesAnUnrelatedEdit() = runBlocking {
        val initial = Settings(apps = mapOf("target" to AppPolicy()))
        val dao = BlockingDao(initial)
        val repository = loadedRepository(dao)

        cancelAfterCommit(dao) {
            repository.updateSettings { it.copy(apps = emptyMap()) }
        }

        assertEquals(initial.copy(apps = emptyMap()), repository.settings.value)
        assertEquals(dao.settings(), repository.settings.value)
        assertNull(repository.error.value)

        repository.updateSettings { it.copy(breathSeconds = 30) }

        assertEquals(initial.copy(apps = emptyMap(), breathSeconds = 30), dao.settings())
        assertEquals(dao.settings(), repository.settings.value)
    }

    @Test(timeout = 15_000)
    fun cancelledIoCaller_observesCancellationAfterPublishingTheCommittedSettings() = runBlocking {
        val dao = BlockingDao()
        val repository = loadedRepository(dao)

        cancelAfterCommit(dao, Dispatchers.IO) {
            repository.updateSettings { it.copy(breathSeconds = 30) }
        }

        assertEquals(Settings(breathSeconds = 30), repository.settings.value)
        assertEquals(dao.settings(), repository.settings.value)
        assertNull(repository.error.value)
    }

    @Test(timeout = 15_000)
    fun cancelledPauseSave_staysPublishedAndSurvivesAnotherPauseEdit() = runBlocking {
        val dao = BlockingDao(pauses = mapOf("target" to activePause))
        val repository = loadedRepository(dao)

        cancelAfterCommit(dao) { repository.savePauses(emptyMap()) }

        assertTrue(repository.pauses.value.isEmpty())
        assertEquals(dao.pauses(), repository.pauses.value)
        assertNull(repository.error.value)

        repository.savePauses(repository.pauses.value + ("other" to activePause))

        assertEquals(mapOf("other" to activePause), dao.pauses())
        assertEquals(dao.pauses(), repository.pauses.value)
    }

    @Test(timeout = 15_000)
    fun cancelledEndPause_staysEndedWhenAnotherPauseIsEnded() = runBlocking {
        val initial = mapOf("target" to activePause, "other" to activePause)
        val dao = BlockingDao(pauses = initial)
        val repository = loadedRepository(dao)

        cancelAfterCommit(dao) { repository.endPause("target", nowEpochMs = 1_000) }

        assertEquals(initial + ("target" to activePause.copy(untilEpochMs = 0)), repository.pauses.value)
        assertEquals(dao.pauses(), repository.pauses.value)
        assertNull(repository.error.value)

        assertTrue(repository.endPause("other", nowEpochMs = 1_000))

        assertEquals(initial.mapValues { activePause.copy(untilEpochMs = 0) }, dao.pauses())
        assertEquals(dao.pauses(), repository.pauses.value)
        assertFalse(repository.endPause("target", nowEpochMs = 1_000))
    }

    @Test(timeout = 15_000)
    fun queuedSettingsEdit_readsTheCancelledWritesPublishedState() = runBlocking {
        val initial = Settings(apps = mapOf("target" to AppPolicy()))
        val dao = BlockingDao(initial)
        val repository = loadedRepository(dao)
        dao.blockNextWrite = true
        val removal = launch(Dispatchers.Default) {
            repository.updateSettings { it.copy(apps = emptyMap()) }
        }
        var nextEdit: Job? = null
        val transformed = AtomicBoolean(false)
        try {
            dao.awaitCommit()
            removal.cancel()
            // UNDISPATCHED reaches the held mutex before returning from launch.
            nextEdit = launch(start = CoroutineStart.UNDISPATCHED) {
                repository.updateSettings {
                    transformed.set(true)
                    assertTrue(it.apps.isEmpty())
                    it.copy(breathSeconds = 30)
                }
            }
            assertFalse(transformed.get())
            dao.releaseWrite.countDown()
            withTimeout(5_000) {
                removal.join()
                nextEdit.join()
            }

            assertTrue(transformed.get())
            assertEquals(initial.copy(apps = emptyMap(), breathSeconds = 30), dao.settings())
            assertEquals(dao.settings(), repository.settings.value)
            assertNull(repository.error.value)
        } finally {
            dao.releaseWrite.countDown()
            removal.cancelAndJoin()
            nextEdit?.cancelAndJoin()
        }
    }

    @Test(timeout = 15_000)
    fun cancelledMutationsWaitingForTheMutex_doNotWrite() = runBlocking {
        val initialPauses = mapOf("target" to activePause)
        val dao = BlockingDao(pauses = initialPauses)
        val repository = loadedRepository(dao)
        dao.blockNextWrite = true
        val accepted = launch(Dispatchers.Default) {
            repository.updateSettings { it.copy(breathSeconds = 30) }
        }
        val waiting = mutableListOf<Job>()
        try {
            dao.awaitCommit()
            waiting += launch(start = CoroutineStart.UNDISPATCHED) {
                repository.updateSettings { it.copy(breathSeconds = 60) }
            }
            waiting += launch(start = CoroutineStart.UNDISPATCHED) { repository.savePauses(emptyMap()) }
            waiting += launch(start = CoroutineStart.UNDISPATCHED) {
                repository.endPause("target", nowEpochMs = 1_000)
            }
            waiting.forEach { it.cancelAndJoin() }
            dao.releaseWrite.countDown()
            withTimeout(5_000) { accepted.join() }

            assertEquals(1, dao.writeCount)
            assertEquals(Settings(breathSeconds = 30), dao.settings())
            assertEquals(dao.settings(), repository.settings.value)
            assertEquals(initialPauses, repository.pauses.value)
            assertEquals(dao.pauses(), repository.pauses.value)
            assertNull(repository.error.value)
        } finally {
            dao.releaseWrite.countDown()
            accepted.cancelAndJoin()
            waiting.forEach { it.cancelAndJoin() }
        }
    }

    @Test(timeout = 15_000)
    @OptIn(DelicateCoroutinesApi::class)
    fun alreadyCancelledMutations_doNotWriteEvenWithAnUnlockedMutex() = runBlocking {
        val dao = BlockingDao(pauses = mapOf("target" to activePause))
        val repository = loadedRepository(dao)
        val cancelledCaller = Job().apply { cancel() }
        val caller = CoroutineScope(cancelledCaller + Dispatchers.Default)
        // ATOMIC enters the method despite cancellation, as an already executing caller could.
        val operations = listOf<suspend () -> Unit>(
            { repository.updateSettings { it.copy(breathSeconds = 30) } },
            { repository.savePauses(emptyMap()) },
            { repository.endPause("target", nowEpochMs = 1_000); Unit },
        )
        for (operation in operations) {
            caller.launch(start = CoroutineStart.ATOMIC) { operation() }.join()
        }

        assertEquals(0, dao.writeCount)
        assertEquals(dao.settings(), repository.settings.value)
        assertEquals(dao.pauses(), repository.pauses.value)
        assertNull(repository.error.value)
    }

    @Test(timeout = 15_000)
    fun failedWrites_keepPreviousPublishedStateAndReportPersistenceErrors() = runBlocking {
        val operations = listOf<Pair<String, suspend (SpeedbreakerRepository) -> Unit>>(
            "Speedbreaker couldn’t save your settings." to { repository ->
                repository.updateSettings { it.copy(breathSeconds = 30) }
            },
            "Speedbreaker couldn’t save active pauses." to { repository -> repository.savePauses(emptyMap()) },
            "Speedbreaker couldn’t end this pause." to { repository ->
                assertFalse(repository.endPause("target", nowEpochMs = 1_000))
            },
        )
        for ((expectedError, operation) in operations) {
            val dao = BlockingDao(pauses = mapOf("target" to activePause))
            val repository = loadedRepository(dao)
            dao.failWrites = true

            operation(repository)

            assertEquals(expectedError, repository.error.value)
            assertEquals(dao.settings(), repository.settings.value)
            assertEquals(dao.pauses(), repository.pauses.value)
        }
    }

    private suspend fun loadedRepository(dao: StateDao): SpeedbreakerRepository =
        SpeedbreakerRepository(dao).also { repository ->
            withTimeout(5_000) { repository.ready.first { it } }
        }

    private suspend fun CoroutineScope.cancelAfterCommit(
        dao: BlockingDao,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
        operation: suspend () -> Unit,
    ) {
        dao.blockNextWrite = true
        val returnedNormally = AtomicBoolean(false)
        val caller = launch(dispatcher) {
            operation()
            returnedNormally.set(true)
        }
        try {
            dao.awaitCommit()
            caller.cancel()
            dao.releaseWrite.countDown()
            withTimeout(5_000) { caller.join() }
            assertTrue(caller.isCancelled)
            assertFalse("The cancelled caller must still observe cancellation", returnedNormally.get())
        } finally {
            dao.releaseWrite.countDown()
            caller.cancelAndJoin()
        }
    }

    /** Models synchronous Room: persist the row, then block before returning to withContext. */
    private class BlockingDao(
        settings: Settings = Settings(),
        pauses: Map<String, PauseState> = emptyMap(),
    ) : StateDao {
        private val rows = ConcurrentHashMap<String, StateRecord>().apply {
            put("settings", StateRecord("settings", SettingsJson.encodeSettings(settings)))
            put("pauses", StateRecord("pauses", SettingsJson.encodePauses(pauses)))
        }
        val committed = CountDownLatch(1)
        val releaseWrite = CountDownLatch(1)
        @Volatile var blockNextWrite = false
        @Volatile var failWrites = false
        @Volatile var writeCount = 0

        override fun read(key: String): StateRecord? = rows[key]

        override fun write(record: StateRecord) {
            if (failWrites) throw IllegalStateException("Simulated disk write failure")
            rows[record.key] = record
            writeCount++
            if (blockNextWrite) {
                blockNextWrite = false
                committed.countDown()
                check(releaseWrite.await(5, TimeUnit.SECONDS)) { "Test did not release the committed write" }
            }
        }

        fun awaitCommit() {
            assertTrue("DAO did not commit", committed.await(5, TimeUnit.SECONDS))
        }

        fun settings(): Settings = SettingsJson.decodeSettings(rows.getValue("settings").value)
        fun pauses(): Map<String, PauseState> = SettingsJson.decodePauses(rows.getValue("pauses").value)
    }

    private companion object {
        val activePause = PauseState("2026-10-11", used = 1, untilEpochMs = 10_000)
    }
}
