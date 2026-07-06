package com.auth0.kmp.credentials

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Hands out a single shared [Mutex] per `(clientId, storeKey)` slot so that all
 * credential operations targeting the same storage slot are serialized.
 */
internal object MutexRegistry {


    private val locks = mutableMapOf<String, Mutex>()
    private val guard = Mutex()


    /**
     * Returns the [Mutex] shared by every caller for the given [clientId] and
     * [storeKey], creating it on first use.
     */
    suspend fun lockFor(clientId: String, storeKey: String): Mutex {
        val key = "$clientId|$storeKey"
        return guard.withLock {
            locks.getOrPut(key) { Mutex() }
        }
    }
}