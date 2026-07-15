package com.auth0.kmp.credentials

import com.auth0.kmp.core.error.TransportError
import com.auth0.kmp.core.model.Credentials
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.core.token.TokenClient
import com.auth0.kmp.core.token.TokenGrant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Instant

internal class FakeStorage(
    private val map: MutableMap<String, String> = mutableMapOf(),
) : Storage {
    var failOnStore = false
    var failOnRemove = false
    var failRetrieveWith: Throwable? = null
    var failStoreWith: Throwable? = null
    var removeCount = 0
        private set

    override suspend fun retrieve(key: String): String? {
        failRetrieveWith?.let { throw it }
        return map[key]
    }

    override suspend fun store(key: String, value: String) {
        failStoreWith?.let { throw it }
        if (failOnStore) throw RuntimeException("store failed")
        map[key] = value
    }

    override suspend fun remove(key: String) {
        removeCount++
        if (failOnRemove) throw RuntimeException("remove failed")
        map.remove(key)
    }
}

internal class FakeTokenClient(
    private val outcome: Result<Credentials, TransportError>,
    private val delayGate: Mutex? = null,
) : TokenClient {
    var callCount = 0
        private set
    var lastGrantParameters: Map<String, String>? = null
        private set
    var lastHeaders: Map<String, String>? = null
        private set

    override suspend fun fetchToken(
        grant: TokenGrant,
        headers: Map<String, String>,
    ): Result<Credentials, TransportError> {
        delayGate?.withLock { }
        callCount++
        lastGrantParameters = grant.parameters
        lastHeaders = headers
        return outcome
    }
}

internal class MutableClock(var instant: Instant) : Clock {
    override fun now(): Instant = instant
}

internal fun credentials(
    accessToken: String = "at",
    expiresAt: Instant = Instant.fromEpochSeconds(10_000),
    refreshToken: String? = "rt",
    scope: String? = "openid",
): Credentials = Credentials(
    accessToken = accessToken,
    idToken = "it",
    tokenType = "Bearer",
    expiresAt = expiresAt,
    refreshToken = refreshToken,
    scope = scope,
)
