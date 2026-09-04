package com.auth0.kmp.credentials

import com.auth0.kmp.core.error.TransportError
import com.auth0.kmp.core.model.Credentials
import com.auth0.kmp.core.model.SsoCredentials
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.core.token.TokenClient
import com.auth0.kmp.core.token.TokenGrant
import com.auth0.kmp.networking.retry.RetryPolicy
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock
import kotlin.time.Instant

internal class FakeStorage(
    private val map: MutableMap<String, String> = mutableMapOf(),
) : Storage {
    var failOnStore = false
    var failOnRemove = false
    var failRemoveKey: String? = null
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
        if (failOnRemove || key == failRemoveKey) throw RuntimeException("remove failed")
        map.remove(key)
    }
}

internal class FakeTokenClient(
    private val outcome: Result<Credentials, TransportError> = Result.Success(credentials()),
    private val delayGate: Mutex? = null,
    private val ssoOutcome: Result<SsoCredentials, TransportError> = Result.Success(ssoCredentials()),
) : TokenClient {
    var callCount = 0
        private set
    var ssoCallCount = 0
        private set
    var lastGrantParameters: JsonObject? = null
        private set
    var lastHeaders: Map<String, String>? = null
        private set
    var lastRetryPolicy: RetryPolicy? = null
        private set

    override suspend fun fetchToken(
        grant: TokenGrant,
        headers: Map<String, String>,
        retryPolicy: RetryPolicy,
    ): Result<Credentials, TransportError> {
        delayGate?.withLock { }
        callCount++
        lastGrantParameters = grant.parameters
        lastHeaders = headers
        lastRetryPolicy = retryPolicy
        return outcome
    }

    override suspend fun fetchSsoCredentials(
        grant: TokenGrant,
        headers: Map<String, String>,
        retryPolicy: RetryPolicy,
    ): Result<SsoCredentials, TransportError> {
        delayGate?.withLock { }
        ssoCallCount++
        lastGrantParameters = grant.parameters
        lastHeaders = headers
        lastRetryPolicy = retryPolicy
        return ssoOutcome
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

internal fun ssoCredentials(
    sessionTransferToken: String = "stt",
    issuedTokenType: String = "urn:ietf:params:oauth:token-type:session_transfer",
    expiresAt: Instant = Instant.fromEpochSeconds(10_000),
    idToken: String = "new-it",
    refreshToken: String? = null,
): SsoCredentials = SsoCredentials(
    sessionTransferToken = sessionTransferToken,
    issuedTokenType = issuedTokenType,
    expiresAt = expiresAt,
    idToken = idToken,
    refreshToken = refreshToken,
)
