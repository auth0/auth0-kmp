package com.auth0.kmp.credentials

import com.auth0.kmp.core.credentials.CredentialsManagerError
import com.auth0.kmp.core.error.TransportError
import com.auth0.kmp.core.model.Credentials
import com.auth0.kmp.core.result.Result
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class DefaultCredentialsManagerTest {

    private val clientId = "client-1"
    private val storeKey = "credentials_client-1"
    private val now = Instant.fromEpochSeconds(1_000_000)

    private fun manager(
        storage: Storage,
        tokenClient: FakeTokenClient = FakeTokenClient(Result.Success(credentials())),
        clock: Clock = MutableClock(now),
        clientId: String = this.clientId,
        storeKey: String = this.storeKey,
    ) = DefaultCredentialsManager(clientId, tokenClient, storage, storeKey, clock)

    private fun storageWith(credentials: Credentials): FakeStorage =
        FakeStorage(mutableMapOf(storeKey to CredentialsSerializer.encode(credentials)))


    @Test
    fun save_persists_serialized_credentials() = runTest {
        val storage = FakeStorage()
        val creds = credentials()

        val result = manager(storage).saveCredentials(creds)

        assertIs<Result.Success<Unit>>(result)
        assertEquals(creds, CredentialsSerializer.decode(storage.retrieve(storeKey)!!))
    }

    @Test
    fun save_maps_storage_throw_to_StoreFailed() = runTest {
        val storage = FakeStorage().apply { failOnStore = true }

        val result = manager(storage).saveCredentials(credentials())

        assertIs<Result.Failure<CredentialsManagerError>>(result)
        assertIs<CredentialsManagerError.StoreFailed>(result.error)
    }


    @Test
    fun clear_removes_stored_key() = runTest {
        val storage = storageWith(credentials())

        val result = manager(storage).clearCredentials()

        assertIs<Result.Success<Unit>>(result)
        assertNull(storage.retrieve(storeKey))
    }

    @Test
    fun clear_maps_storage_throw_to_StoreFailed() = runTest {
        val storage = storageWith(credentials()).apply { failOnRemove = true }

        val result = manager(storage).clearCredentials()

        assertIs<Result.Failure<CredentialsManagerError>>(result)
        assertIs<CredentialsManagerError.StoreFailed>(result.error)
    }


    @Test
    fun hasValid_false_when_absent() = runTest {
        assertEquals(false, manager(FakeStorage()).hasValidCredentials())
    }

    @Test
    fun hasValid_false_when_unparseable() = runTest {
        val storage = FakeStorage(mutableMapOf(storeKey to "not-json"))
        assertEquals(false, manager(storage).hasValidCredentials())
    }

    @Test
    fun hasValid_true_when_not_expiring() = runTest {
        val storage = storageWith(credentials(expiresAt = now + 3600.seconds))
        assertEquals(true, manager(storage).hasValidCredentials(minTtl = 60))
    }

    @Test
    fun hasValid_false_when_within_minTtl() = runTest {
        val storage = storageWith(credentials(expiresAt = now + 30.seconds))
        assertEquals(false, manager(storage).hasValidCredentials(minTtl = 60))
    }


    @Test
    fun get_returns_NoCredentials_when_absent() = runTest {
        val result = manager(FakeStorage()).getCredentials()

        assertIs<Result.Failure<CredentialsManagerError>>(result)
        assertIs<CredentialsManagerError.NoCredentials>(result.error)
    }

    @Test
    fun get_returns_DeserializationFailed_on_bad_blob() = runTest {
        val storage = FakeStorage(mutableMapOf(storeKey to "not-json"))

        val result = manager(storage).getCredentials()

        assertIs<Result.Failure<CredentialsManagerError>>(result)
        assertIs<CredentialsManagerError.DeserializationFailed>(result.error)
    }

    @Test
    fun get_returns_stored_when_valid_and_no_renewal() = runTest {
        val stored = credentials(expiresAt = now + 3600.seconds)
        val storage = storageWith(stored)
        val tokenClient = FakeTokenClient(Result.Success(credentials()))

        val result = manager(storage, tokenClient).getCredentials()

        assertIs<Result.Success<Credentials>>(result)
        assertEquals(stored, result.data)
        assertEquals(0, tokenClient.callCount)
    }

    @Test
    fun get_renews_when_expired_and_persists() = runTest {
        val stored = credentials(expiresAt = now - 10.seconds, refreshToken = "stored-rt")
        val renewed = credentials(accessToken = "new-at", expiresAt = now + 3600.seconds, refreshToken = "new-rt")
        val storage = storageWith(stored)
        val tokenClient = FakeTokenClient(Result.Success(renewed))

        val result = manager(storage, tokenClient).getCredentials(parameters = mapOf("audience" to "api"))

        assertIs<Result.Success<Credentials>>(result)
        assertEquals(renewed, result.data)
        assertEquals(1, tokenClient.callCount)
        assertEquals("refresh_token", tokenClient.lastGrantParameters?.get("grant_type"))
        assertEquals("stored-rt", tokenClient.lastGrantParameters?.get("refresh_token"))
        assertEquals("api", tokenClient.lastGrantParameters?.get("audience"))
        assertEquals(renewed, CredentialsSerializer.decode(storage.retrieve(storeKey)!!))
    }

    @Test
    fun get_returns_NoRefreshToken_when_renewal_needed_but_no_refresh_token() = runTest {
        val stored = credentials(expiresAt = now - 10.seconds, refreshToken = null)
        val storage = storageWith(stored)
        val tokenClient = FakeTokenClient(Result.Success(credentials()))

        val result = manager(storage, tokenClient).getCredentials()

        assertIs<Result.Failure<CredentialsManagerError>>(result)
        assertIs<CredentialsManagerError.NoRefreshToken>(result.error)
        assertEquals(0, tokenClient.callCount)
    }

    @Test
    fun get_carries_refresh_token_forward_when_renewed_blank() = runTest {
        val stored = credentials(expiresAt = now - 10.seconds, refreshToken = "stored-rt")
        val renewed = credentials(accessToken = "new-at", expiresAt = now + 3600.seconds, refreshToken = null)
        val storage = storageWith(stored)
        val tokenClient = FakeTokenClient(Result.Success(renewed))

        val result = manager(storage, tokenClient).getCredentials()

        assertIs<Result.Success<Credentials>>(result)
        assertEquals("stored-rt", result.data.refreshToken)
    }

    @Test
    fun get_returns_LargeMinTtl_when_renewed_still_short() = runTest {
        val stored = credentials(expiresAt = now - 10.seconds, refreshToken = "stored-rt")
        val renewed = credentials(accessToken = "new-at", expiresAt = now + 30.seconds)
        val storage = storageWith(stored)
        val tokenClient = FakeTokenClient(Result.Success(renewed))

        val result = manager(storage, tokenClient).getCredentials(minTtl = 60)

        assertIs<Result.Failure<CredentialsManagerError>>(result)
        val error = result.error
        assertIs<CredentialsManagerError.LargeMinTtl>(error)
        assertEquals(60, error.minTtl)
        assertEquals(30, error.lifetime)
    }

    @Test
    fun get_renews_when_scope_reordered_is_treated_unchanged() = runTest {
        val stored = credentials(expiresAt = now + 3600.seconds, scope = "openid profile")
        val storage = storageWith(stored)
        val tokenClient = FakeTokenClient(Result.Success(credentials()))

        val result = manager(storage, tokenClient).getCredentials(scope = "profile openid")

        assertIs<Result.Success<Credentials>>(result)
        assertEquals(stored, result.data)
        assertEquals(0, tokenClient.callCount)
    }

    @Test
    fun get_renews_when_forceRefresh_even_if_valid() = runTest {
        val stored = credentials(expiresAt = now + 3600.seconds, refreshToken = "stored-rt")
        val renewed = credentials(accessToken = "new-at", expiresAt = now + 3600.seconds)
        val storage = storageWith(stored)
        val tokenClient = FakeTokenClient(Result.Success(renewed))

        val result = manager(storage, tokenClient).getCredentials(forceRefresh = true)

        assertIs<Result.Success<Credentials>>(result)
        assertEquals(renewed, result.data)
        assertEquals(1, tokenClient.callCount)
    }


    @Test
    fun concurrent_getCredentials_renews_only_once() = runTest {
        val stored = credentials(expiresAt = now - 10.seconds, refreshToken = "stored-rt")
        val renewed = credentials(accessToken = "new-at", expiresAt = now + 3600.seconds)
        val storage = storageWith(stored)
        val gate = Mutex(locked = true)
        val tokenClient = FakeTokenClient(Result.Success(renewed), delayGate = gate)
        val manager = manager(storage, tokenClient)

        val a = async { manager.getCredentials() }
        val b = async { manager.getCredentials() }
        gate.unlock()
        val results = awaitAll(a, b)

        assertEquals(1, tokenClient.callCount)
        results.forEach {
            assertIs<Result.Success<Credentials>>(it)
            assertEquals(renewed, it.data)
        }
    }

    // ---- renewal failure ----

    @Test
    fun get_surfaces_renewal_failure_and_leaves_stored_untouched() = runTest {
        val stored = credentials(expiresAt = now - 10.seconds, refreshToken = "stored-rt")
        val originalBlob = CredentialsSerializer.encode(stored)
        val storage = FakeStorage(mutableMapOf(storeKey to originalBlob))
        val tokenClient = FakeTokenClient(Result.Failure(TransportError.NoInternet))

        val result = manager(storage, tokenClient).getCredentials()

        assertIs<Result.Failure<CredentialsManagerError>>(result)
        assertIs<CredentialsManagerError.Network>(result.error)
        assertEquals(originalBlob, storage.retrieve(storeKey))
    }

    @Test
    fun get_maps_store_throw_after_successful_renewal_to_StoreFailed() = runTest {
        val stored = credentials(expiresAt = now - 10.seconds, refreshToken = "stored-rt")
        val renewed = credentials(accessToken = "new-at", expiresAt = now + 3600.seconds)
        val storage = storageWith(stored).apply { failOnStore = true }
        val tokenClient = FakeTokenClient(Result.Success(renewed))

        val result = manager(storage, tokenClient).getCredentials()

        assertIs<Result.Failure<CredentialsManagerError>>(result)
        assertIs<CredentialsManagerError.StoreFailed>(result.error)
        assertEquals(tokenClient.callCount, 1)
    }
}
