package com.auth0.kmp.credentials

import com.google.crypto.tink.Aead
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import java.security.ProviderException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
internal class EncryptedStorage(
    private val delegate: Storage,
    private val cryptoDispatcher: CoroutineDispatcher = Dispatchers.Default,
    aeadProvider: () -> Aead,
) : Storage {

    private val aead: Aead by lazy(aeadProvider)

    override suspend fun retrieve(key: String): String? {
        val cipherB64 = delegate.retrieve(key) ?: return null
        return withContext(cryptoDispatcher) {
            try {
                aead.decrypt(Base64.decode(cipherB64), key.encodeToByteArray()).decodeToString()
            } catch (e: GeneralSecurityException) {
                throw StorageCryptoException("Cannot decrypt stored value", e)
            } catch (e: ProviderException) {
                throw StorageCryptoException("Keystore provider failure", e)
            } catch (e: IllegalArgumentException) {
                throw StorageCryptoException("Stored value is not valid base64", e)
            }
        }
    }

    override suspend fun store(key: String, value: String) {
        val cipherB64 = withContext(cryptoDispatcher) {
            try {
                Base64.encode(aead.encrypt(value.encodeToByteArray(), key.encodeToByteArray()))
            } catch (e: GeneralSecurityException) {
                throw StorageCryptoException("Cannot encrypt value", e)
            } catch (e: ProviderException) {
                throw StorageCryptoException("Keystore provider failure", e)
            }
        }
        delegate.store(key, cipherB64)
    }

    override suspend fun remove(key: String) {
        delegate.remove(key)
    }
}
