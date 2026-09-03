package com.auth0.kmp.credentials

import com.auth0.kmp.core.credentials.CredentialsManagerError
import com.auth0.kmp.core.error.TransportError
import com.auth0.kmp.core.model.APICredentials
import com.auth0.kmp.core.model.Credentials
import com.auth0.kmp.core.result.Result
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content

class DefaultCredentialsManagerApiCredentialsTest {

    private val clientId = "client-1"
    private val storeKey = "credentials_client-1"
    private val apiStoreKey = "credentials_client-1::api"
    private val now = Instant.fromEpochSeconds(1_000_000)

    private fun manager(
        storage: Storage,
        tokenClient: FakeTokenClient = FakeTokenClient(Result.Success(credentials())),
        clock: Clock = MutableClock(now),
    ) = DefaultCredentialsManager(clientId, tokenClient, storage, storeKey, clock, MutexRegistry())

    private fun apiCred(
        accessToken: String = "api-at",
        tokenType: String = "Bearer",
        expiresAt: Instant,
        scope: String? = null,
    ) = APICredentials(accessToken, tokenType, expiresAt, scope)

    private fun seed(main: Credentials, api: Map<String, APICredentials> = emptyMap()): FakeStorage {
        val entries = mutableMapOf(storeKey to CredentialsSerializer.encode(main))
        if (api.isNotEmpty()) entries[apiStoreKey] = ApiCredentialsSerializer.encode(api)
        return FakeStorage(entries)
    }

    // ---- getApiCredentials: cache ----

    @Test
    fun getApi_returns_cached_when_valid() = runTest {
        val cached = apiCred(accessToken = "cached", expiresAt = now + 3600.seconds, scope = "read:things")
        val storage = seed(credentials(), mapOf(apiCredentialsKey("api", "read:things") to cached))
        val tokenClient = FakeTokenClient(Result.Success(credentials()))

        val result = manager(storage, tokenClient).getApiCredentials("api", "read:things")

        assertIs<Result.Success<APICredentials>>(result)
        assertEquals(cached, result.data)
        assertEquals(0, tokenClient.callCount)
    }

    @Test
    fun getApi_exchanges_and_caches_on_miss() = runTest {
        val exchanged = credentials(accessToken = "api-at", expiresAt = now + 3600.seconds, scope = "read:things")
        val storage = seed(credentials(refreshToken = "stored-rt"))
        val tokenClient = FakeTokenClient(Result.Success(exchanged))

        val result = manager(storage, tokenClient).getApiCredentials("api", "read:things")

        assertIs<Result.Success<APICredentials>>(result)
        assertEquals("api-at", result.data.accessToken)
        assertEquals(1, tokenClient.callCount)
        assertEquals("refresh_token", tokenClient.lastGrantParameters?.str("grant_type"))
        assertEquals("api", tokenClient.lastGrantParameters?.str("audience"))
        assertEquals("read:things", tokenClient.lastGrantParameters?.str("scope"))
        assertEquals("stored-rt", tokenClient.lastGrantParameters?.str("refresh_token"))
        val cached = ApiCredentialsSerializer.decode(storage.retrieve(apiStoreKey)!!)
        assertEquals("api-at", cached[apiCredentialsKey("api", "read:things")]?.accessToken)
    }

    @Test
    fun getApi_reexchanges_when_cached_expired() = runTest {
        val storage = seed(
            credentials(refreshToken = "rt"),
            mapOf(apiCredentialsKey("api", null) to apiCred(accessToken = "old", expiresAt = now - 10.seconds)),
        )
        val tokenClient = FakeTokenClient(Result.Success(credentials(accessToken = "new", expiresAt = now + 3600.seconds)))

        val result = manager(storage, tokenClient).getApiCredentials("api")

        assertIs<Result.Success<APICredentials>>(result)
        assertEquals("new", result.data.accessToken)
        assertEquals(1, tokenClient.callCount)
    }

    @Test
    fun getApi_exchanges_when_forceRefresh_even_if_cached_valid() = runTest {
        val storage = seed(
            credentials(refreshToken = "rt"),
            mapOf(apiCredentialsKey("api", null) to apiCred(accessToken = "cached", expiresAt = now + 3600.seconds)),
        )
        val tokenClient = FakeTokenClient(Result.Success(credentials(accessToken = "fresh", expiresAt = now + 3600.seconds)))

        val result = manager(storage, tokenClient).getApiCredentials("api", forceRefresh = true)

        assertIs<Result.Success<APICredentials>>(result)
        assertEquals("fresh", result.data.accessToken)
        assertEquals(1, tokenClient.callCount)
    }

    @Test
    fun getApi_reexchanges_when_cached_within_minTtl() = runTest {
        val storage = seed(
            credentials(refreshToken = "rt"),
            mapOf(apiCredentialsKey("api", null) to apiCred(accessToken = "cached", expiresAt = now + 30.seconds)),
        )
        val tokenClient = FakeTokenClient(Result.Success(credentials(accessToken = "fresh", expiresAt = now + 3600.seconds)))

        val result = manager(storage, tokenClient).getApiCredentials("api", minTtl = 60)

        assertIs<Result.Success<APICredentials>>(result)
        assertEquals("fresh", result.data.accessToken)
        assertEquals(1, tokenClient.callCount)
    }

    // ---- getApiCredentials: preconditions & failures ----

    @Test
    fun getApi_returns_NoCredentials_when_no_main_credentials() = runTest {
        val tokenClient = FakeTokenClient(Result.Success(credentials()))
        val result = manager(FakeStorage(), tokenClient).getApiCredentials("api")

        assertIs<Result.Failure<CredentialsManagerError>>(result)
        assertIs<CredentialsManagerError.NoCredentials>(result.error)
        assertEquals(0, tokenClient.callCount)
    }

    @Test
    fun getApi_returns_NoRefreshToken_when_main_has_no_refresh_token() = runTest {
        val storage = seed(credentials(refreshToken = null))
        val tokenClient = FakeTokenClient(Result.Success(credentials()))

        val result = manager(storage, tokenClient).getApiCredentials("api")

        assertIs<Result.Failure<CredentialsManagerError>>(result)
        assertIs<CredentialsManagerError.NoRefreshToken>(result.error)
        assertEquals(0, tokenClient.callCount)
    }

    @Test
    fun getApi_surfaces_exchange_failure_and_caches_nothing() = runTest {
        val storage = seed(credentials(refreshToken = "rt"))
        val tokenClient = FakeTokenClient(Result.Failure(TransportError.NoInternet))

        val result = manager(storage, tokenClient).getApiCredentials("api")

        assertIs<Result.Failure<CredentialsManagerError>>(result)
        assertIs<CredentialsManagerError.Network>(result.error)
        assertNull(storage.retrieve(apiStoreKey))
    }

    @Test
    fun getApi_returns_LargeMinTtl_when_exchanged_still_short() = runTest {
        val storage = seed(credentials(refreshToken = "rt"))
        val tokenClient = FakeTokenClient(Result.Success(credentials(accessToken = "api-at", expiresAt = now + 30.seconds)))

        val result = manager(storage, tokenClient).getApiCredentials("api", minTtl = 60)

        assertIs<Result.Failure<CredentialsManagerError>>(result)
        val error = result.error
        assertIs<CredentialsManagerError.LargeMinTtl>(error)
        assertEquals(60, error.minTtl)
        assertEquals(30, error.lifetime)
        assertNull(storage.retrieve(apiStoreKey))
    }

    @Test
    fun getApi_persists_rotated_refresh_token_even_when_LargeMinTtl() = runTest {
        val storage = seed(credentials(refreshToken = "old-rt"))
        // exchanged lifetime (30s) < minTtl (60) → LargeMinTtl, and the RT rotated
        val exchanged = credentials(accessToken = "api-at", expiresAt = now + 30.seconds, refreshToken = "rotated-rt")
        val tokenClient = FakeTokenClient(Result.Success(exchanged))

        val result = manager(storage, tokenClient).getApiCredentials("api", minTtl = 60)

        assertIs<Result.Failure<CredentialsManagerError>>(result)
        assertIs<CredentialsManagerError.LargeMinTtl>(result.error)
        // rotated RT written back to the main store despite the failure — no lockout
        assertEquals("rotated-rt", CredentialsSerializer.decode(storage.retrieve(storeKey)!!).credentials.refreshToken)
        // and the sub-minTtl API token was NOT cached
        assertNull(storage.retrieve(apiStoreKey))
    }

    // ---- getApiCredentials: write-back & isolation ----

    @Test
    fun getApi_writes_rotated_refresh_token_back_to_main() = runTest {
        val exchanged = credentials(accessToken = "api-at", expiresAt = now + 3600.seconds, refreshToken = "rotated-rt")
        val storage = seed(credentials(refreshToken = "old-rt"))
        val tokenClient = FakeTokenClient(Result.Success(exchanged))

        val result = manager(storage, tokenClient).getApiCredentials("api")

        assertIs<Result.Success<APICredentials>>(result)
        assertEquals("rotated-rt", CredentialsSerializer.decode(storage.retrieve(storeKey)!!).credentials.refreshToken)
        val cached = ApiCredentialsSerializer.decode(storage.retrieve(apiStoreKey)!!)
        assertEquals("api-at", cached[apiCredentialsKey("api", null)]?.accessToken)
    }

    @Test
    fun getApi_different_scope_is_a_distinct_cache_entry() = runTest {
        val storage = seed(
            credentials(refreshToken = "rt"),
            mapOf(apiCredentialsKey("api", "read") to apiCred(accessToken = "read-at", expiresAt = now + 3600.seconds, scope = "read")),
        )
        val tokenClient = FakeTokenClient(Result.Success(credentials(accessToken = "write-at", expiresAt = now + 3600.seconds, scope = "write")))

        val result = manager(storage, tokenClient).getApiCredentials("api", "write")

        assertIs<Result.Success<APICredentials>>(result)
        assertEquals("write-at", result.data.accessToken)
        assertEquals(1, tokenClient.callCount)
        val cached = ApiCredentialsSerializer.decode(storage.retrieve(apiStoreKey)!!)
        assertEquals("read-at", cached[apiCredentialsKey("api", "read")]?.accessToken)
        assertEquals("write-at", cached[apiCredentialsKey("api", "write")]?.accessToken)
    }

    @Test
    fun getApi_second_audience_does_not_clobber_first() = runTest {
        val storage = seed(
            credentials(refreshToken = "rt"),
            mapOf(apiCredentialsKey("aud-a", null) to apiCred(accessToken = "a-at", expiresAt = now + 3600.seconds)),
        )
        val tokenClient = FakeTokenClient(Result.Success(credentials(accessToken = "b-at", expiresAt = now + 3600.seconds)))

        manager(storage, tokenClient).getApiCredentials("aud-b")

        val cached = ApiCredentialsSerializer.decode(storage.retrieve(apiStoreKey)!!)
        assertEquals("a-at", cached[apiCredentialsKey("aud-a", null)]?.accessToken)
        assertEquals("b-at", cached[apiCredentialsKey("aud-b", null)]?.accessToken)
    }

    // ---- clearApiCredentials ----

    @Test
    fun clearApi_removes_only_the_targeted_entry() = runTest {
        val storage = seed(
            credentials(),
            mapOf(
                apiCredentialsKey("aud-a", null) to apiCred(accessToken = "a", expiresAt = now + 3600.seconds),
                apiCredentialsKey("aud-b", null) to apiCred(accessToken = "b", expiresAt = now + 3600.seconds),
            ),
        )

        val result = manager(storage).clearApiCredentials("aud-a")

        assertIs<Result.Success<Unit>>(result)
        val cached = ApiCredentialsSerializer.decode(storage.retrieve(apiStoreKey)!!)
        assertNull(cached[apiCredentialsKey("aud-a", null)])
        assertEquals("b", cached[apiCredentialsKey("aud-b", null)]?.accessToken)
    }

    @Test
    fun clearApi_removes_blob_when_last_entry_cleared() = runTest {
        val storage = seed(
            credentials(),
            mapOf(apiCredentialsKey("aud-a", null) to apiCred(accessToken = "a", expiresAt = now + 3600.seconds)),
        )

        val result = manager(storage).clearApiCredentials("aud-a")

        assertIs<Result.Success<Unit>>(result)
        assertNull(storage.retrieve(apiStoreKey))
    }

    @Test
    fun clearApi_is_noop_when_entry_absent() = runTest {
        val storage = seed(
            credentials(),
            mapOf(apiCredentialsKey("aud-a", null) to apiCred(accessToken = "a", expiresAt = now + 3600.seconds)),
        )

        val result = manager(storage).clearApiCredentials("aud-missing")

        assertIs<Result.Success<Unit>>(result)
        val cached = ApiCredentialsSerializer.decode(storage.retrieve(apiStoreKey)!!)
        assertEquals("a", cached[apiCredentialsKey("aud-a", null)]?.accessToken)
    }

    // ---- hasValidApiCredentials ----

    @Test
    fun hasValidApi_true_when_live() = runTest {
        val storage = seed(
            credentials(),
            mapOf(apiCredentialsKey("api", null) to apiCred(expiresAt = now + 3600.seconds)),
        )
        assertEquals(true, manager(storage).hasValidApiCredentials("api", minTtl = 60))
    }

    @Test
    fun hasValidApi_false_when_absent_expired_or_within_minTtl() = runTest {
        assertEquals(false, manager(FakeStorage()).hasValidApiCredentials("api"))

        val expired = seed(credentials(), mapOf(apiCredentialsKey("api", null) to apiCred(expiresAt = now - 10.seconds)))
        assertEquals(false, manager(expired).hasValidApiCredentials("api"))

        val soon = seed(credentials(), mapOf(apiCredentialsKey("api", null) to apiCred(expiresAt = now + 30.seconds)))
        assertEquals(false, manager(soon).hasValidApiCredentials("api", minTtl = 60))
    }

    @Test
    fun hasValidApi_false_when_blob_unparseable() = runTest {
        val storage = FakeStorage(mutableMapOf(apiStoreKey to "not-json"))
        assertEquals(false, manager(storage).hasValidApiCredentials("api"))
    }

    // ---- clearCredentials removes the API blob ----

    @Test
    fun clear_removes_api_blob_too() = runTest {
        val storage = seed(
            credentials(),
            mapOf(apiCredentialsKey("api", null) to apiCred(expiresAt = now + 3600.seconds)),
        )

        val result = manager(storage).clearCredentials()

        assertIs<Result.Success<Unit>>(result)
        assertNull(storage.retrieve(storeKey))
        assertNull(storage.retrieve(apiStoreKey))
    }
}
