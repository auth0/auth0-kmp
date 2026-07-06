package com.auth0.kmp.credentials

import kotlinx.coroutines.sync.Mutex

/**
 * Supplies the [Mutex] used to serialize credential operations for a given
 * `(clientId, storeKey)` slot.
 */
internal interface LockProvider {
    suspend fun lockFor(clientId: String, storeKey: String): Mutex
}
