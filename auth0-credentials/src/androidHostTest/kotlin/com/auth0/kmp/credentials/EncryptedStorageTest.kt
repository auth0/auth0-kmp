package com.auth0.kmp.credentials

import com.google.crypto.tink.Aead
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.security.GeneralSecurityException
import java.security.ProviderException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class, ExperimentalCoroutinesApi::class)
class EncryptedStorageTest {

    // Reversible fake: frames ciphertext as [aadLen][aad][plaintext] and verifies
    // the associated data on decrypt, mirroring how a real AEAD binds the key slot.
    private class FakeAead(
        private val failEncryptWith: Throwable? = null,
        private val failDecryptWith: Throwable? = null,
    ) : Aead {
        override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
            failEncryptWith?.let { throw it }
            return byteArrayOf(associatedData.size.toByte()) + associatedData + plaintext
        }

        override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
            failDecryptWith?.let { throw it }
            val aadLen = ciphertext[0].toInt()
            val aad = ciphertext.copyOfRange(1, 1 + aadLen)
            if (!aad.contentEquals(associatedData)) throw GeneralSecurityException("AAD mismatch")
            return ciphertext.copyOfRange(1 + aadLen, ciphertext.size)
        }
    }

    private class InMemoryStorage(
        val map: MutableMap<String, String> = mutableMapOf(),
    ) : Storage {
        override suspend fun retrieve(key: String): String? = map[key]
        override suspend fun store(key: String, value: String) { map[key] = value }
        override suspend fun remove(key: String) { map.remove(key) }
    }

    private fun storage(delegate: Storage = InMemoryStorage()) =
        EncryptedStorage(delegate, UnconfinedTestDispatcher()) { FakeAead() }

    @Test
    fun store_then_retrieve_returns_original_value() = runTest {
        val storage = storage()
        storage.store("k", "hello")
        assertEquals("hello", storage.retrieve("k"))
    }

    @Test
    fun retrieve_returns_null_for_missing_key() = runTest {
        assertNull(storage().retrieve("absent"))
    }

    @Test
    fun remove_deletes_a_stored_value() = runTest {
        val storage = storage()
        storage.store("k", "v")
        storage.remove("k")
        assertNull(storage.retrieve("k"))
    }

    @Test
    fun delegate_holds_base64_ciphertext_not_plaintext() = runTest {
        val delegate = InMemoryStorage()
        storage(delegate).store("k", "secret")

        val raw = delegate.map["k"]!!
        assertTrue(raw != "secret")
        Base64.decode(raw) // valid base64 (throws if not)
    }

    @Test
    fun value_stored_under_one_key_cannot_be_decrypted_as_another() = runTest {
        // Proves the storage key is threaded as AEAD associated data: copying the
        // ciphertext to a different slot must fail to decrypt.
        val delegate = InMemoryStorage()
        val storage = storage(delegate)
        storage.store("keyA", "v")
        delegate.map["keyB"] = delegate.map.remove("keyA")!!

        assertFailsWith<StorageCryptoException> { storage.retrieve("keyB") }
    }

    @Test
    fun retrieve_of_non_base64_ciphertext_throws_StorageCryptoException() = runTest {
        val delegate = InMemoryStorage(mutableMapOf("k" to "not base64!!"))
        assertFailsWith<StorageCryptoException> { storage(delegate).retrieve("k") }
    }

    @Test
    fun store_maps_encrypt_failure_to_StorageCryptoException() = runTest {
        val storage = EncryptedStorage(InMemoryStorage(), UnconfinedTestDispatcher()) {
            FakeAead(failEncryptWith = GeneralSecurityException("encrypt failed"))
        }
        assertFailsWith<StorageCryptoException> { storage.store("k", "v") }
    }

    @Test
    fun retrieve_maps_provider_exception_to_StorageCryptoException() = runTest {
        val delegate = InMemoryStorage()
        storage(delegate).store("k", "v")
        val storage = EncryptedStorage(delegate, UnconfinedTestDispatcher()) {
            FakeAead(failDecryptWith = ProviderException("keystore unavailable"))
        }
        assertFailsWith<StorageCryptoException> { storage.retrieve("k") }
    }

    @Test
    fun store_maps_provider_exception_to_StorageCryptoException() = runTest {
        val storage = EncryptedStorage(InMemoryStorage(), UnconfinedTestDispatcher()) {
            FakeAead(failEncryptWith = ProviderException("keystore unavailable"))
        }
        assertFailsWith<StorageCryptoException> { storage.store("k", "v") }
    }
}
