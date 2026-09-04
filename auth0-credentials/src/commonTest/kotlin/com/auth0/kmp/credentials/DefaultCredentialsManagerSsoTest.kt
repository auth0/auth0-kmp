package com.auth0.kmp.credentials

import com.auth0.kmp.core.Auth0Account
import com.auth0.kmp.core.credentials.CredentialsManagerError
import com.auth0.kmp.core.error.TransportError
import com.auth0.kmp.core.model.Credentials
import com.auth0.kmp.core.model.SsoCredentials
import com.auth0.kmp.core.result.Result
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Instant

private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content

class DefaultCredentialsManagerSsoTest {

    private val clientId = "client-1"
    private val domain = "test.auth0.com"
    private val storeKey = "credentials_client-1"
    private val now = Instant.fromEpochSeconds(1_000_000)

    private fun manager(
        storage: Storage,
        tokenClient: FakeTokenClient = FakeTokenClient(),
        clock: Clock = MutableClock(now),
    ) = DefaultCredentialsManager(
        Auth0Account(clientId, domain), tokenClient, storage, storeKey, clock, MutexRegistry(),
    )

    private fun storageWith(credentials: Credentials): FakeStorage =
        FakeStorage(mutableMapOf(storeKey to CredentialsSerializer.encode(credentials)))

    private suspend fun FakeStorage.storedCredentials(): Credentials =
        CredentialsSerializer.decode(retrieve(storeKey)!!).credentials

    @Test
    fun getSso_returns_credentials_and_builds_correct_grant() = runTest {
        val storage = storageWith(credentials(refreshToken = "stored-rt"))
        val tokenClient = FakeTokenClient(ssoOutcome = Result.Success(ssoCredentials()))

        val result = manager(storage, tokenClient).getSsoCredentials(
            parameters = mapOf("foo" to "bar"),
            headers = mapOf("h" to "v"),
        )

        assertIs<Result.Success<SsoCredentials>>(result)
        assertEquals(ssoCredentials(), result.data)
        assertEquals(1, tokenClient.ssoCallCount)
        assertEquals("refresh_token", tokenClient.lastGrantParameters?.str("grant_type"))
        assertEquals("stored-rt", tokenClient.lastGrantParameters?.str("refresh_token"))
        assertEquals(clientId, tokenClient.lastGrantParameters?.str("client_id"))
        assertEquals("urn:test.auth0.com:session_transfer", tokenClient.lastGrantParameters?.str("audience"))
        assertEquals("bar", tokenClient.lastGrantParameters?.str("foo"))
        assertEquals(mapOf("h" to "v"), tokenClient.lastHeaders)
    }

    @Test
    fun getSso_returns_NoCredentials_when_absent() = runTest {
        val tokenClient = FakeTokenClient()

        val result = manager(FakeStorage(), tokenClient).getSsoCredentials()

        assertIs<Result.Failure<CredentialsManagerError>>(result)
        assertIs<CredentialsManagerError.NoCredentials>(result.error)
        assertEquals(0, tokenClient.ssoCallCount)
    }

    @Test
    fun getSso_returns_NoRefreshToken_when_rt_null() = runTest {
        val storage = storageWith(credentials(refreshToken = null))
        val tokenClient = FakeTokenClient()

        val result = manager(storage, tokenClient).getSsoCredentials()

        assertIs<Result.Failure<CredentialsManagerError>>(result)
        assertIs<CredentialsManagerError.NoRefreshToken>(result.error)
        assertEquals(0, tokenClient.ssoCallCount)
    }

    @Test
    fun getSso_returns_NoRefreshToken_when_rt_blank() = runTest {
        val storage = storageWith(credentials(refreshToken = ""))
        val tokenClient = FakeTokenClient()

        val result = manager(storage, tokenClient).getSsoCredentials()

        assertIs<Result.Failure<CredentialsManagerError>>(result)
        assertIs<CredentialsManagerError.NoRefreshToken>(result.error)
        assertEquals(0, tokenClient.ssoCallCount)
    }

    @Test
    fun getSso_persists_new_idToken_and_rotated_rt() = runTest {
        val storage = storageWith(credentials(accessToken = "at", refreshToken = "stored-rt"))
        val tokenClient = FakeTokenClient(
            ssoOutcome = Result.Success(ssoCredentials(idToken = "new-it", refreshToken = "rotated-rt")),
        )

        val result = manager(storage, tokenClient).getSsoCredentials()

        assertIs<Result.Success<SsoCredentials>>(result)
        val persisted = storage.storedCredentials()
        assertEquals("new-it", persisted.idToken)
        assertEquals("rotated-rt", persisted.refreshToken)
        assertEquals("at", persisted.accessToken) // access token untouched by an SSO exchange
    }

    @Test
    fun getSso_carries_rt_forward_when_sso_rt_null() = runTest {
        val storage = storageWith(credentials(refreshToken = "stored-rt"))
        val tokenClient = FakeTokenClient(
            ssoOutcome = Result.Success(ssoCredentials(idToken = "new-it", refreshToken = null)),
        )

        val result = manager(storage, tokenClient).getSsoCredentials()

        assertIs<Result.Success<SsoCredentials>>(result)
        val persisted = storage.storedCredentials()
        assertEquals("stored-rt", persisted.refreshToken)
        assertEquals("new-it", persisted.idToken)
    }

    @Test
    fun getSso_carries_rt_forward_when_sso_rt_blank() = runTest {
        val storage = storageWith(credentials(refreshToken = "stored-rt"))
        val tokenClient = FakeTokenClient(
            ssoOutcome = Result.Success(ssoCredentials(refreshToken = "")),
        )

        val result = manager(storage, tokenClient).getSsoCredentials()

        assertIs<Result.Success<SsoCredentials>>(result)
        assertEquals("stored-rt", storage.storedCredentials().refreshToken)
    }

    @Test
    fun getSso_surfaces_network_failure_and_leaves_stored_untouched() = runTest {
        val originalBlob = CredentialsSerializer.encode(credentials(refreshToken = "stored-rt"))
        val storage = FakeStorage(mutableMapOf(storeKey to originalBlob))
        val tokenClient = FakeTokenClient(ssoOutcome = Result.Failure(TransportError.NoInternet))

        val result = manager(storage, tokenClient).getSsoCredentials()

        assertIs<Result.Failure<CredentialsManagerError>>(result)
        assertIs<CredentialsManagerError.Network>(result.error)
        assertEquals(originalBlob, storage.retrieve(storeKey))
    }

    @Test
    fun getSso_maps_store_throw_after_exchange_to_StoreFailed() = runTest {
        val storage = storageWith(credentials(refreshToken = "stored-rt")).apply { failOnStore = true }
        val tokenClient = FakeTokenClient(ssoOutcome = Result.Success(ssoCredentials()))

        val result = manager(storage, tokenClient).getSsoCredentials()

        assertIs<Result.Failure<CredentialsManagerError>>(result)
        assertIs<CredentialsManagerError.StoreFailed>(result.error)
        assertEquals(1, tokenClient.ssoCallCount) // the exchange happened; only the write-back failed
    }
}
