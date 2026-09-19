package dev.burooj.speedbreaker.observation

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Orders process-local pause transactions that originate outside the engine loop. */
internal object PausePersistenceCoordinator {
    private val mutex = Mutex()

    suspend fun <T> withLock(action: suspend () -> T): T = mutex.withLock { action() }
}
