package com.auth0.kmp.credentials

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Hands out a single shared [Mutex] per `(clientId, storeKey)` slot so that all
 * credential operations targeting the same storage slot are serialized.
 */
internal class MutexRegistry : LockProvider {

    private val locks = mutableMapOf<String, Mutex>()
    private val guard = Mutex()

    override suspend fun lockFor(clientId: String, storeKey: String): Mutex {
        val key = "$clientId|$storeKey"
        return guard.withLock {
            locks.getOrPut(key) { Mutex() }
        }
    }

    companion object {
        /** Process-wide registry shared by managers built from the same factory. */
        val Default: MutexRegistry = MutexRegistry()
    }
}
