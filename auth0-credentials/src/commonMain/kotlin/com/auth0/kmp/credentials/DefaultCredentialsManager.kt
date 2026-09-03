package com.auth0.kmp.credentials

import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.credentials.CredentialsManager
import com.auth0.kmp.core.credentials.CredentialsManagerError
import com.auth0.kmp.core.dpop.DPoPProofGenerator
import com.auth0.kmp.core.logging.Auth0Log
import com.auth0.kmp.core.model.APICredentials
import com.auth0.kmp.core.model.Credentials
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.core.result.map
import com.auth0.kmp.core.token.RefreshTokenGrant
import com.auth0.kmp.core.token.TokenClient
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(InternalAuth0Api::class)
internal class DefaultCredentialsManager(
    private val clientId: String,
    private val tokenClient: TokenClient,
    private val storage: Storage,
    private val storeKey: String,
    private val clock: Clock,
    private val lockProvider: LockProvider = MutexRegistry.Default,
    private val proofGenerator: DPoPProofGenerator? = null,
    private val useDPoP: Boolean = false,
) : CredentialsManager {

    override suspend fun saveCredentials(
        credentials: Credentials,
    ): Result<Unit, CredentialsManagerError> {
        val thumbprint = when (val result = dpopThumbprintForSave(credentials)) {
            is Result.Success -> result.data
            is Result.Failure -> return result
        }
        return storageCall {
            storage.store(storeKey, CredentialsSerializer.encode(credentials, thumbprint))
        }
    }

    override suspend fun clearCredentials(): Result<Unit, CredentialsManagerError> {
        val result = storageCall { storage.remove(storeKey) }
        storageCall { storage.remove(apiStoreKey) }
        proofGenerator?.clearKeypair()?.let { keypairResult ->
            if (keypairResult is Result.Failure) {
                Auth0Log.e(TAG, "Failed to clear DPoP keypair on logout: ${keypairResult.error}")
            }
        }
        return result
    }

    override suspend fun hasValidCredentials(minTtl: Int): Boolean {
        val blob = runCatching { storage.retrieve(storeKey) }.getOrNull() ?: return false
        val stored = runCatching { CredentialsSerializer.decode(blob) }.getOrNull() ?: return false
        return !hasExpired(stored.credentials.expiresAt) && !willExpire(stored.credentials.expiresAt, minTtl)
    }

    override suspend fun getCredentials(
        scope: String?,
        minTtl: Int,
        parameters: Map<String, String>,
        headers: Map<String, String>,
        forceRefresh: Boolean,
    ): Result<Credentials, CredentialsManagerError> = withAccountLock {
        val stored = when (val read = loadStoredCredentials()) {
            is Result.Success -> read.data
            is Result.Failure -> return@withAccountLock read
        }
        val credentials = stored.credentials

        val scopeChanged = hasScopeChanged(credentials.scope, scope)
        val needsRenewal = forceRefresh ||
                hasExpired(credentials.expiresAt) ||
                willExpire(credentials.expiresAt, minTtl) ||
                scopeChanged

        if (!needsRenewal) return@withAccountLock Result.Success(credentials)

        Auth0Log.d(
            TAG,
            "Renewing credentials (forceRefresh=$forceRefresh, expired=${hasExpired(credentials.expiresAt)}, " +
                "willExpire=${willExpire(credentials.expiresAt, minTtl)}, scopeChanged=$scopeChanged)",
        )

        val exchange = when (
            val result = exchangeRefreshToken(stored, scope, audience = null, parameters, headers)
        ) {
            is Result.Success -> result.data
            is Result.Failure -> return@withAccountLock result
        }

        val merged = exchange.credentials.copy(
            refreshToken = exchange.credentials.refreshToken?.takeIf { it.isNotBlank() }
                ?: credentials.refreshToken,
        )

        val write = storageCall {
            storage.store(storeKey, CredentialsSerializer.encode(merged, exchange.thumbprint))
        }
        if (write is Result.Failure) return@withAccountLock write

        if (willExpire(merged.expiresAt, minTtl)) {
            val lifetime = (merged.expiresAt - clock.now()).inWholeSeconds.toInt()
            return@withAccountLock Result.Failure(CredentialsManagerError.LargeMinTtl(minTtl, lifetime))
        }

        Result.Success(merged)
    }

    override suspend fun getApiCredentials(
        audience: String,
        scope: String?,
        minTtl: Int,
        parameters: Map<String, String>,
        headers: Map<String, String>,
        forceRefresh: Boolean,
    ): Result<APICredentials, CredentialsManagerError> = withAccountLock {
        val entryKey = apiCredentialsKey(audience, scope)

        val cachedBlob = when (val read = readApiCredentials()) {
            is Result.Success -> read.data
            is Result.Failure -> return@withAccountLock read
        }

        val cached = cachedBlob[entryKey]
        if (!forceRefresh && cached != null &&
            !hasExpired(cached.expiresAt) && !willExpire(cached.expiresAt, minTtl)
        ) {
            return@withAccountLock Result.Success(cached)
        }

        val stored = when (val read = loadStoredCredentials()) {
            is Result.Success -> read.data
            is Result.Failure -> return@withAccountLock read
        }

        val exchange = when (
            val result = exchangeRefreshToken(stored, scope, audience, parameters, headers)
        ) {
            is Result.Success -> result.data
            is Result.Failure -> return@withAccountLock result
        }
        val exchanged = exchange.credentials

        val apiCredentials = APICredentials(
            accessToken = exchanged.accessToken,
            tokenType = exchanged.tokenType,
            expiresAt = exchanged.expiresAt,
            scope = exchanged.scope,
        )

        val rotatedRefreshToken = exchanged.refreshToken?.takeIf { it.isNotBlank() }
        if (rotatedRefreshToken != null && rotatedRefreshToken != stored.credentials.refreshToken) {
            val updatedMain = stored.credentials.copy(refreshToken = rotatedRefreshToken)
            val write = storageCall {
                storage.store(storeKey, CredentialsSerializer.encode(updatedMain, exchange.thumbprint))
            }
            if (write is Result.Failure) return@withAccountLock write
        }

        if (willExpire(apiCredentials.expiresAt, minTtl)) {
            val lifetime = (apiCredentials.expiresAt - clock.now()).inWholeSeconds.toInt()
            return@withAccountLock Result.Failure(CredentialsManagerError.LargeMinTtl(minTtl, lifetime))
        }

        storageCall {
            storage.store(apiStoreKey, ApiCredentialsSerializer.encode(cachedBlob + (entryKey to apiCredentials)))
        }.map { apiCredentials }
    }

    override suspend fun clearApiCredentials(
        audience: String,
        scope: String?,
    ): Result<Unit, CredentialsManagerError> = withAccountLock {
        val blob = when (val read = readApiCredentials()) {
            is Result.Success -> read.data
            is Result.Failure -> return@withAccountLock read
        }
        val entryKey = apiCredentialsKey(audience, scope)
        if (entryKey !in blob) return@withAccountLock Result.Success(Unit)

        val updated = blob - entryKey
        if (updated.isEmpty()) storageCall { storage.remove(apiStoreKey) }
        else storageCall { storage.store(apiStoreKey, ApiCredentialsSerializer.encode(updated)) }
    }

    override suspend fun hasValidApiCredentials(
        audience: String,
        scope: String?,
        minTtl: Int,
    ): Boolean {
        val blob = runCatching { storage.retrieve(apiStoreKey) }.getOrNull() ?: return false
        val map = runCatching { ApiCredentialsSerializer.decode(blob) }.getOrNull() ?: return false
        val entry = map[apiCredentialsKey(audience, scope)] ?: return false
        return !hasExpired(entry.expiresAt) && !willExpire(entry.expiresAt, minTtl)
    }

    /**
     * Reads and decodes the stored main credentials. Returns [CredentialsManagerError.NoCredentials]
     * when nothing is stored. On a crypto failure the blob is dropped (device key invalidated) and
     * the failure returned; a decode failure surfaces as [CredentialsManagerError.DeserializationFailed].
     */
    private suspend fun loadStoredCredentials(): Result<StoredCredentials, CredentialsManagerError> {
        val blob = when (val read = storageCall { storage.retrieve(storeKey) }) {
            is Result.Success -> read.data ?: return Result.Failure(CredentialsManagerError.NoCredentials)
            is Result.Failure -> {
                if (read.error is CredentialsManagerError.CryptoFailed) {
                    Auth0Log.e(TAG, "Stored credentials could not be decrypted; clearing them")
                    storageCall { storage.remove(storeKey) }
                }
                return read
            }
        }
        return runCatching { CredentialsSerializer.decode(blob) }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { Result.Failure(CredentialsManagerError.DeserializationFailed(it)) },
        )
    }

    /**
     * Exchanges the refresh token in [stored] at `/oauth/token`, optionally scoping the result to
     * [audience] and [scope]. Runs the DPoP consistency gate first. Returns the freshly exchanged
     * credentials together with the DPoP fingerprint to persist with them (`null` when not
     * DPoP-bound). Fails with [CredentialsManagerError.NoRefreshToken] when no refresh token is
     * available, or propagates a DPoP-state or transport failure.
     */
    private suspend fun exchangeRefreshToken(
        stored: StoredCredentials,
        scope: String?,
        audience: String?,
        parameters: Map<String, String>,
        headers: Map<String, String>,
    ): Result<TokenExchange, CredentialsManagerError> {
        val credentials = stored.credentials
        val refreshToken = credentials.refreshToken
        if (refreshToken.isNullOrBlank()) {
            Auth0Log.e(TAG, "A token exchange is required but no refresh token is available")
            return Result.Failure(CredentialsManagerError.NoRefreshToken)
        }

        val thumbprint = when (
            val result = validateDPoPState(credentials.tokenType, stored.dpopThumbprint)
        ) {
            is Result.Success -> result.data
            is Result.Failure -> return result
        }

        val grant = RefreshTokenGrant(refreshToken, clientId, scope, audience, extraParameters = parameters)
        return when (val result = tokenClient.fetchToken(grant, headers)) {
            is Result.Failure -> Result.Failure(result.error.toCredentialsManagerError())
            is Result.Success -> Result.Success(TokenExchange(result.data, thumbprint))
        }
    }

    /**
     * Reads and decodes the API-credentials blob. Returns an empty map when nothing is stored.
     * On a crypto failure the blob is dropped (device key invalidated); a decode failure is
     * treated as an empty cache so the next call re-exchanges rather than failing permanently.
     */
    private suspend fun readApiCredentials(): Result<Map<String, APICredentials>, CredentialsManagerError> =
        when (val read = storageCall { storage.retrieve(apiStoreKey) }) {
            is Result.Success -> {
                val blob = read.data ?: return Result.Success(emptyMap())
                Result.Success(
                    runCatching { ApiCredentialsSerializer.decode(blob) }.getOrElse {
                        Auth0Log.e(TAG, "Stored API credentials could not be decoded; dropping them")
                        storageCall { storage.remove(apiStoreKey) }
                        emptyMap()
                    },
                )
            }
            is Result.Failure -> {
                if (read.error is CredentialsManagerError.CryptoFailed) {
                    Auth0Log.e(TAG, "Stored API credentials could not be decrypted; clearing them")
                    storageCall { storage.remove(apiStoreKey) }
                }
                read
            }
        }

    /**
     * The DPoP key fingerprint to embed alongside credentials being saved, so a later read
     * can detect if the credentials and the keypair have drifted apart. Returns `null` when
     * the credentials are not DPoP-bound or no keypair exists. Fails when the key store is
     * unavailable, so credentials are never persisted without their binding.
     */
    private fun dpopThumbprintForSave(
        credentials: Credentials,
    ): Result<String?, CredentialsManagerError> {
        val generator = proofGenerator ?: return Result.Success(null)
        val isNewCredentialDPoPBound =
            credentials.tokenType.equals(DPOP_TOKEN_TYPE, ignoreCase = true) || useDPoP
        if (!isNewCredentialDPoPBound) return Result.Success(null)
        return when (val result = generator.jktIfPresent()) {
            is Result.Success -> Result.Success(result.data)
            is Result.Failure -> Result.Failure(CredentialsManagerError.DPoPKeyUnavailable(result.error))
        }
    }

    /**
     * Verifies the stored credentials are still consistent with the DPoP keypair on the
     * device before a renewal. Clears the credentials and fails when the keypair is
     * definitively gone or mismatched; fails without clearing when the key store is only
     * transiently unavailable or DPoP is no longer configured. On success, returns the
     * fingerprint to persist with the renewed credentials (`null` when not DPoP-bound).
     */
    private suspend fun validateDPoPState(
        tokenType: String,
        storedThumbprint: String?,
    ): Result<String?, CredentialsManagerError> {
        val generator = proofGenerator ?: return Result.Success(null)
        val isStoredCredentialDPoPBound =
            tokenType.equals(DPOP_TOKEN_TYPE, ignoreCase = true) || storedThumbprint != null
        if (!isStoredCredentialDPoPBound) return Result.Success(null)

        val currentThumbprint = when (val result = generator.jktIfPresent()) {
            is Result.Success -> result.data
            is Result.Failure -> return Result.Failure(CredentialsManagerError.DPoPKeyUnavailable(result.error))
        }
        if (currentThumbprint == null) {
            Auth0Log.e(TAG, "DPoP keypair is missing for stored credentials; clearing them")
            clearCredentials()
            return Result.Failure(CredentialsManagerError.DPoPKeyMissing)
        }
        if (!useDPoP) return Result.Failure(CredentialsManagerError.DPoPNotConfigured)

        if (storedThumbprint != null && currentThumbprint != storedThumbprint) {
            Auth0Log.e(TAG, "DPoP key fingerprint no longer matches stored credentials; clearing them")
            clearCredentials()
            return Result.Failure(CredentialsManagerError.DPoPKeyMismatch)
        }
        return Result.Success(currentThumbprint)
    }

    private suspend fun <T> withAccountLock(block: suspend () -> T): T =
        lockProvider.lockFor(clientId, storeKey).withLock { block() }

    private suspend fun <T> storageCall(
        block: suspend () -> T,
    ): Result<T, CredentialsManagerError> =
        runCatching { block() }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = {
                Result.Failure(
                    if (it is StorageCryptoException) CredentialsManagerError.CryptoFailed(it)
                    else CredentialsManagerError.StoreFailed(it),
                )
            },
        )

    private fun hasScopeChanged(storedScope: String?, requiredScope: String?): Boolean {
        if (requiredScope == null) return false
        val storedScopes = storedScope.orEmpty().split(" ").filter { it.isNotEmpty() }.toSet()
        val requiredScopes = requiredScope.split(" ").filter { it.isNotEmpty() }.toSet()
        return storedScopes != requiredScopes
    }

    private fun hasExpired(expiresAt: Instant): Boolean =
        expiresAt <= clock.now()

    private fun willExpire(expiresAt: Instant, minTtl: Int): Boolean =
        expiresAt <= clock.now() + minTtl.seconds

    /** The storage key under which this client's API-credentials blob is persisted. */
    private val apiStoreKey: String get() = "$storeKey::api"

    private companion object {
        private const val DPOP_TOKEN_TYPE = "DPoP"
        private const val TAG = "Auth0.Credentials"
    }
}

/**
 * The outcome of a refresh-token exchange: the freshly issued [credentials] and the DPoP key
 * fingerprint they should be persisted with (`null` when not DPoP-bound).
 */
private data class TokenExchange(
    val credentials: Credentials,
    val thumbprint: String?,
)
