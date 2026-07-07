package com.auth0.kmp.credentials

import com.auth0.kmp.core.credentials.CredentialsManager
import com.auth0.kmp.core.credentials.CredentialsManagerError
import com.auth0.kmp.core.model.Credentials
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.core.result.map
import com.auth0.kmp.core.token.TokenClient
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

internal class DefaultCredentialsManager(
    private val clientId: String,
    private val tokenClient: TokenClient,
    private val storage: Storage,
    private val storeKey: String,
    private val clock: Clock,
    private val lockProvider: LockProvider = MutexRegistry.Default,
) : CredentialsManager {

    override suspend fun saveCredentials(
        credentials: Credentials,
    ): Result<Unit, CredentialsManagerError> =
        storageResult { storage.store(storeKey, CredentialsSerializer.encode(credentials)) }

    override suspend fun clearCredentials(): Result<Unit, CredentialsManagerError> =
        storageResult { storage.remove(storeKey) }

    override suspend fun hasValidCredentials(minTtl: Long): Boolean {
        val stored = decodeStored() ?: return false
        return !hasExpired(stored) && !willExpire(stored, minTtl)
    }

    override suspend fun getCredentials(
        scope: String?,
        minTtl: Int,
        parameters: Map<String, String>,
        headers: Map<String, String>,
        forceRefresh: Boolean,
    ): Result<Credentials, CredentialsManagerError> = withAccountLock {
        val blob = storage.retrieve(storeKey)
            ?: return@withAccountLock Result.Failure(CredentialsManagerError.NoCredentials)

        val stored = runCatching { CredentialsSerializer.decode(blob) }.getOrElse {
            return@withAccountLock Result.Failure(CredentialsManagerError.DeserializationFailed(it))
        }

        val scopeChanged = hasScopeChanged(stored.scope, scope)
        val needsRenewal = forceRefresh ||
                hasExpired(stored) ||
                willExpire(stored, minTtl.toLong()) ||
                scopeChanged

        if (!needsRenewal) return@withAccountLock Result.Success(stored)

        val refreshToken = stored.refreshToken
        if (refreshToken.isNullOrBlank()) {
            return@withAccountLock Result.Failure(CredentialsManagerError.NoRefreshToken)
        }

        val grant = RefreshTokenGrant(
            refreshToken,
            clientId,
            scope,
            extraParams = parameters
        )
        val renewed = when (val result = tokenClient.fetchToken(grant, headers)) {
            is Result.Failure -> return@withAccountLock Result.Failure(result.error.toCredentialsManagerError())
            is Result.Success -> result.data
        }

        val merged = renewed.copy(
            refreshToken = renewed.refreshToken?.takeIf { it.isNotBlank() } ?: stored.refreshToken,
        )

        if (willExpire(merged, minTtl.toLong())) {
            val lifetime = (merged.expiresAt - clock.now()).inWholeSeconds
            return@withAccountLock Result.Failure(
                CredentialsManagerError.LargeMinTtl(
                    minTtl,
                    lifetime
                )
            )
        }

        storageResult { storage.store(storeKey, CredentialsSerializer.encode(merged)) }
            .map { merged }
    }

    private suspend fun <T> withAccountLock(block: suspend () -> T): T =
        lockProvider.lockFor(clientId, storeKey).withLock { block() }

    private inline fun <T> storageResult(block: () -> T): Result<T, CredentialsManagerError> =
        runCatching(block).fold(
            onSuccess = { Result.Success(it) },
            onFailure = { Result.Failure(CredentialsManagerError.StoreFailed(it)) },
        )

    private suspend fun decodeStored(): Credentials? {
        val blob = storage.retrieve(storeKey) ?: return null
        return runCatching { CredentialsSerializer.decode(blob) }.getOrNull()
    }

    private fun hasScopeChanged(storedScope: String?, requiredScope: String?): Boolean {
        if (requiredScope == null) return false
        val storedScopes = storedScope.orEmpty().split(" ").filter { it.isNotEmpty() }.toSet()
        val requiredScopes = requiredScope.split(" ").filter { it.isNotEmpty() }.toSet()
        return storedScopes != requiredScopes
    }

    private fun hasExpired(credentials: Credentials): Boolean =
        credentials.expiresAt <= clock.now()

    private fun willExpire(credentials: Credentials, minTtl: Long): Boolean =
        credentials.expiresAt <= clock.now() + minTtl.seconds
}
