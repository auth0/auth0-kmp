package com.auth0.kmp.credentials

import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.credentials.CredentialsManager
import com.auth0.kmp.core.credentials.CredentialsManagerError
import com.auth0.kmp.core.dpop.DPoPProofGenerator
import com.auth0.kmp.core.logging.Auth0Log
import com.auth0.kmp.core.model.Credentials
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.core.result.map
import com.auth0.kmp.core.token.RefreshTokenGrant
import com.auth0.kmp.core.token.TokenClient
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

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

    override suspend fun clearCredentials(): Result<Unit, CredentialsManagerError> =
        storageCall {
            storage.remove(storeKey)
        }

    override suspend fun hasValidCredentials(minTtl: Int): Boolean {
        val blob = runCatching { storage.retrieve(storeKey) }.getOrNull() ?: return false
        val stored = runCatching { CredentialsSerializer.decode(blob) }.getOrNull() ?: return false
        return !hasExpired(stored.credentials) && !willExpire(stored.credentials, minTtl)
    }

    override suspend fun getCredentials(
        scope: String?,
        minTtl: Int,
        parameters: Map<String, String>,
        headers: Map<String, String>,
        forceRefresh: Boolean,
    ): Result<Credentials, CredentialsManagerError> = withAccountLock {
        val blob = when (val read = storageCall { storage.retrieve(storeKey) }) {
            is Result.Success ->
                read.data ?: return@withAccountLock Result.Failure(CredentialsManagerError.NoCredentials)
            is Result.Failure -> {
                // The blob can no longer be decrypted (e.g. the device key was
                // invalidated); drop it so the next login starts clean.
                if (read.error is CredentialsManagerError.CryptoFailed) {
                    Auth0Log.e(TAG, "Stored credentials could not be decrypted; clearing them")
                    storageCall { storage.remove(storeKey) }
                }
                return@withAccountLock read
            }
        }

        val stored = runCatching { CredentialsSerializer.decode(blob) }.getOrElse {
            return@withAccountLock Result.Failure(CredentialsManagerError.DeserializationFailed(it))
        }
        val credentials = stored.credentials

        val scopeChanged = hasScopeChanged(credentials.scope, scope)
        val needsRenewal = forceRefresh ||
                hasExpired(credentials) ||
                willExpire(credentials, minTtl) ||
                scopeChanged

        if (!needsRenewal) return@withAccountLock Result.Success(credentials)

        Auth0Log.d(
            TAG,
            "Renewing credentials (forceRefresh=$forceRefresh, expired=${hasExpired(credentials)}, " +
                "willExpire=${willExpire(credentials, minTtl)}, scopeChanged=$scopeChanged)",
        )

        val refreshToken = credentials.refreshToken
        if (refreshToken.isNullOrBlank()) {
            Auth0Log.e(TAG, "Credentials need renewal but no refresh token is available")
            return@withAccountLock Result.Failure(CredentialsManagerError.NoRefreshToken)
        }

        val thumbprint = when (
            val result = validateDPoPState(credentials.tokenType, stored.dpopThumbprint)
        ) {
            is Result.Success -> result.data
            is Result.Failure -> return@withAccountLock result
        }

        val grant = RefreshTokenGrant(
            refreshToken,
            clientId,
            scope,
            extraParameters = parameters,
        )
        val renewed = when (val result = tokenClient.fetchToken(grant, headers)) {
            is Result.Failure -> return@withAccountLock Result.Failure(result.error.toCredentialsManagerError())
            is Result.Success -> result.data
        }

        val merged = renewed.copy(
            refreshToken = renewed.refreshToken?.takeIf { it.isNotBlank() } ?: credentials.refreshToken,
        )

        if (willExpire(merged, minTtl)) {
            val lifetime = (merged.expiresAt - clock.now()).inWholeSeconds.toInt()
            return@withAccountLock Result.Failure(
                CredentialsManagerError.LargeMinTtl(
                    minTtl,
                    lifetime
                )
            )
        }

        storageCall { storage.store(storeKey, CredentialsSerializer.encode(merged, thumbprint)) }
            .map { merged }
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

    private fun hasExpired(credentials: Credentials): Boolean =
        credentials.expiresAt <= clock.now()

    private fun willExpire(credentials: Credentials, minTtl: Int): Boolean =
        credentials.expiresAt <= clock.now() + minTtl.seconds

    private companion object {
        private const val DPOP_TOKEN_TYPE = "DPoP"
        private const val TAG = "Auth0.Credentials"
    }
}
