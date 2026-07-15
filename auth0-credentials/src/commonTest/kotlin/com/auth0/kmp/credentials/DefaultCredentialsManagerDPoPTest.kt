package com.auth0.kmp.credentials

import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.credentials.CredentialsManagerError
import com.auth0.kmp.core.dpop.DPoPJwk
import com.auth0.kmp.core.dpop.DPoPKeyStore
import com.auth0.kmp.core.dpop.DPoPProofGenerator
import com.auth0.kmp.core.model.Credentials
import com.auth0.kmp.core.result.Result
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(InternalAuth0Api::class)
class DefaultCredentialsManagerDPoPTest {

    private val clientId = "client-1"
    private val storeKey = "credentials_client-1"
    private val now = Instant.fromEpochSeconds(1_000_000)

    private val jwk = DPoPJwk(x = "x-coord", y = "y-coord")
    private val jkt = jwk.thumbprint()

    private class FakeDPoPKeyStore(
        var keyPresent: Boolean = true,
        private val jwk: DPoPJwk = DPoPJwk(x = "x-coord", y = "y-coord"),
        private val failHasKey: Boolean = false,
        private val failPublicJwk: Boolean = false,
    ) : DPoPKeyStore {
        override fun hasKey(): Boolean =
            if (failHasKey) throw RuntimeException("keystore unavailable") else keyPresent

        override fun publicJwk(): DPoPJwk =
            if (failPublicJwk) throw RuntimeException("keystore unavailable") else jwk

        override fun publicJwkOrNull(): DPoPJwk? {
            if (failHasKey) throw RuntimeException("keystore unavailable")
            if (!keyPresent) return null
            if (failPublicJwk) throw RuntimeException("keystore unavailable")
            return jwk
        }

        override fun sign(data: ByteArray): ByteArray = ByteArray(0)
        override fun clear() {
            keyPresent = false
        }
    }

    private fun manager(
        storage: Storage,
        tokenClient: FakeTokenClient = FakeTokenClient(
            Result.Success(credentials(expiresAt = now + 3600.seconds)),
        ),
        keyStore: DPoPKeyStore? = FakeDPoPKeyStore(),
        useDPoP: Boolean = false,
        clock: Clock = MutableClock(now),
    ) = DefaultCredentialsManager(
        clientId = clientId,
        tokenClient = tokenClient,
        storage = storage,
        storeKey = storeKey,
        clock = clock,
        lockProvider = MutexRegistry(),
        proofGenerator = keyStore?.let { DPoPProofGenerator(it) },
        useDPoP = useDPoP,
    )

    private fun expiredBearer(): Credentials =
        credentials(expiresAt = now - 10.seconds, refreshToken = "rt")

    private fun expiredDPoP(): Credentials = Credentials(
        accessToken = "at",
        idToken = "it",
        tokenType = "DPoP",
        expiresAt = now - 10.seconds,
        refreshToken = "rt",
        scope = "openid",
    )

    private fun storageWith(vararg entries: Pair<String, String>): FakeStorage =
        FakeStorage(mutableMapOf(*entries))

    private fun blob(credentials: Credentials, thumbprint: String? = null): Pair<String, String> =
        storeKey to CredentialsSerializer.encode(credentials, thumbprint)

    /** The DPoP thumbprint embedded in the blob currently stored under [storeKey]. */
    private suspend fun FakeStorage.storedThumbprint(): String? =
        retrieve(storeKey)?.let { CredentialsSerializer.decode(it).dpopThumbprint }

    // --- validateDPoPState (renewal path) ---

    @Test
    fun get_proceeds_when_not_dpop_bound() = runTest {
        val storage = storageWith(blob(expiredBearer()))
        val tokenClient = FakeTokenClient(Result.Success(credentials(expiresAt = now + 3600.seconds)))

        val result = manager(storage, tokenClient).getCredentials()

        assertIs<Result.Success<Credentials>>(result)
        assertEquals(1, tokenClient.callCount)
    }

    @Test
    fun get_fails_and_clears_when_key_missing() = runTest {
        val storage = storageWith(blob(expiredBearer(), thumbprint = jkt))
        val tokenClient = FakeTokenClient(Result.Success(credentials(expiresAt = now + 3600.seconds)))

        val result = manager(
            storage, tokenClient, keyStore = FakeDPoPKeyStore(keyPresent = false), useDPoP = true,
        ).getCredentials()

        assertIs<Result.Failure<CredentialsManagerError>>(result)
        assertIs<CredentialsManagerError.DPoPKeyMissing>(result.error)
        assertNull(storage.retrieve(storeKey))
        assertEquals(0, tokenClient.callCount)
    }

    @Test
    fun get_fails_without_clearing_when_dpop_not_configured() = runTest {
        val storage = storageWith(blob(expiredBearer(), thumbprint = jkt))
        val tokenClient = FakeTokenClient(Result.Success(credentials(expiresAt = now + 3600.seconds)))

        val result = manager(
            storage, tokenClient, keyStore = FakeDPoPKeyStore(), useDPoP = false,
        ).getCredentials()

        assertIs<Result.Failure<CredentialsManagerError>>(result)
        assertIs<CredentialsManagerError.DPoPNotConfigured>(result.error)
        assertNotNull(storage.retrieve(storeKey))
        assertEquals(0, tokenClient.callCount)
    }

    @Test
    fun get_fails_and_clears_when_thumbprint_mismatches() = runTest {
        val storage = storageWith(blob(expiredBearer(), thumbprint = "different-thumbprint"))
        val tokenClient = FakeTokenClient(Result.Success(credentials(expiresAt = now + 3600.seconds)))

        val result = manager(
            storage, tokenClient, keyStore = FakeDPoPKeyStore(), useDPoP = true,
        ).getCredentials()

        assertIs<Result.Failure<CredentialsManagerError>>(result)
        assertIs<CredentialsManagerError.DPoPKeyMismatch>(result.error)
        assertNull(storage.retrieve(storeKey))
        assertEquals(0, tokenClient.callCount)
    }

    @Test
    fun get_renews_when_thumbprint_matches() = runTest {
        val storage = storageWith(blob(expiredBearer(), thumbprint = jkt))
        val tokenClient = FakeTokenClient(Result.Success(credentials(expiresAt = now + 3600.seconds)))

        val result = manager(
            storage, tokenClient, keyStore = FakeDPoPKeyStore(), useDPoP = true,
        ).getCredentials()

        assertIs<Result.Success<Credentials>>(result)
        assertEquals(1, tokenClient.callCount)
    }

    @Test
    fun get_backfills_thumbprint_when_bound_by_token_type() = runTest {
        val storage = storageWith(blob(expiredDPoP()))
        val tokenClient = FakeTokenClient(Result.Success(credentials(expiresAt = now + 3600.seconds)))

        val result = manager(
            storage, tokenClient, keyStore = FakeDPoPKeyStore(), useDPoP = true,
        ).getCredentials()

        assertIs<Result.Success<Credentials>>(result)
        assertEquals(1, tokenClient.callCount)
        assertEquals(jkt, storage.storedThumbprint())
    }

    @Test
    fun get_ignores_dpop_state_when_no_generator() = runTest {
        val storage = storageWith(blob(expiredBearer(), thumbprint = "stored"))
        val tokenClient = FakeTokenClient(Result.Success(credentials(expiresAt = now + 3600.seconds)))

        val result = manager(storage, tokenClient, keyStore = null).getCredentials()

        assertIs<Result.Success<Credentials>>(result)
        assertEquals(1, tokenClient.callCount)
    }

    @Test
    fun get_denies_without_clearing_when_hasKey_fails() = runTest {
        val storage = storageWith(blob(expiredBearer(), thumbprint = jkt))
        val tokenClient = FakeTokenClient(Result.Success(credentials(expiresAt = now + 3600.seconds)))

        val result = manager(
            storage, tokenClient, keyStore = FakeDPoPKeyStore(failHasKey = true), useDPoP = true,
        ).getCredentials()

        assertIs<Result.Failure<CredentialsManagerError>>(result)
        assertIs<CredentialsManagerError.DPoPKeyUnavailable>(result.error)
        assertNotNull(storage.retrieve(storeKey))
        assertEquals(jkt, storage.storedThumbprint())
        assertEquals(0, tokenClient.callCount)
    }

    @Test
    fun get_denies_without_clearing_when_jkt_fails() = runTest {
        val storage = storageWith(blob(expiredBearer(), thumbprint = jkt))
        val tokenClient = FakeTokenClient(Result.Success(credentials(expiresAt = now + 3600.seconds)))

        val result = manager(
            storage, tokenClient, keyStore = FakeDPoPKeyStore(failPublicJwk = true), useDPoP = true,
        ).getCredentials()

        assertIs<Result.Failure<CredentialsManagerError>>(result)
        assertIs<CredentialsManagerError.DPoPKeyUnavailable>(result.error)
        assertNotNull(storage.retrieve(storeKey))
        assertEquals(jkt, storage.storedThumbprint())
        assertEquals(0, tokenClient.callCount)
    }

    // --- dpopThumbprintForSave (via saveCredentials) ---

    @Test
    fun save_stores_thumbprint_when_dpop_used_and_key_present() = runTest {
        val storage = FakeStorage()
        val creds = credentials()

        val result = manager(storage, keyStore = FakeDPoPKeyStore(), useDPoP = true)
            .saveCredentials(creds)

        assertIs<Result.Success<Unit>>(result)
        assertEquals(creds, CredentialsSerializer.decode(storage.retrieve(storeKey)!!).credentials)
        assertEquals(jkt, storage.storedThumbprint())
    }

    @Test
    fun save_removes_stale_thumbprint_when_not_dpop() = runTest {
        val storage = storageWith(blob(credentials(), thumbprint = "stale"))

        val result = manager(storage, keyStore = FakeDPoPKeyStore(), useDPoP = false)
            .saveCredentials(credentials())

        assertIs<Result.Success<Unit>>(result)
        assertNotNull(storage.retrieve(storeKey))
        assertNull(storage.storedThumbprint())
    }

    @Test
    fun save_omits_thumbprint_when_dpop_used_but_key_absent() = runTest {
        val storage = FakeStorage()

        val result = manager(
            storage, keyStore = FakeDPoPKeyStore(keyPresent = false), useDPoP = true,
        ).saveCredentials(credentials())

        assertIs<Result.Success<Unit>>(result)
        assertNotNull(storage.retrieve(storeKey))
        assertNull(storage.storedThumbprint())
    }

    @Test
    fun save_stores_null_thumbprint_when_no_generator() = runTest {
        val storage = FakeStorage()

        val result = manager(storage, keyStore = null, useDPoP = true).saveCredentials(credentials())

        assertIs<Result.Success<Unit>>(result)
        assertNotNull(storage.retrieve(storeKey))
        assertNull(storage.storedThumbprint())
    }

    @Test
    fun save_stores_thumbprint_when_credential_is_dpop_typed_even_if_useDPoP_false() = runTest {
        val storage = FakeStorage()
        val dpopCreds = credentials().copy(tokenType = "DPoP")

        val result = manager(storage, keyStore = FakeDPoPKeyStore(), useDPoP = false)
            .saveCredentials(dpopCreds)

        assertIs<Result.Success<Unit>>(result)
        assertEquals(jkt, storage.storedThumbprint())
    }

    @Test
    fun save_fails_when_keystore_fails() = runTest {
        val storage = FakeStorage()

        val result = manager(
            storage, keyStore = FakeDPoPKeyStore(failHasKey = true), useDPoP = true,
        ).saveCredentials(credentials())

        assertIs<Result.Failure<CredentialsManagerError>>(result)
        assertIs<CredentialsManagerError.DPoPKeyUnavailable>(result.error)
        assertNull(storage.retrieve(storeKey))
    }

    // --- clearCredentials ---

    @Test
    fun clear_removes_credentials_and_thumbprint() = runTest {
        val storage = storageWith(blob(credentials(), thumbprint = jkt))

        val result = manager(storage, keyStore = FakeDPoPKeyStore(), useDPoP = true).clearCredentials()

        assertIs<Result.Success<Unit>>(result)
        assertNull(storage.retrieve(storeKey))
    }
}
