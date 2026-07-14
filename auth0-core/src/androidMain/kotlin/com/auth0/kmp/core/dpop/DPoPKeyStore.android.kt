package com.auth0.kmp.core.dpop

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.context.ApplicationContextHolder
import com.auth0.kmp.core.primitives.encodeBase64Url
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.ProviderException
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

@OptIn(InternalAuth0Api::class)
internal class AndroidDPoPKeyStore(private val keyAlias: String) : DPoPKeyStore {

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    override fun hasKey(): Boolean =
        runCatching { keyStore.containsAlias(keyAlias) }
            .getOrElse { throw DPoPException(DPoPError.KeyStoreFailed(it)) }

    override fun publicJwk(): DPoPJwk {
        if (!hasKey()) {
            generateKeyPair(useStrongBox = true)
        }
        val publicKey = loadPublicKey()
        return publicKey.toJwk()
    }

    override fun sign(data: ByteArray): ByteArray {
        val privateKey = loadPrivateKey()
        return try {
            val der = Signature.getInstance("SHA256withECDSA").run {
                initSign(privateKey)
                update(data)
                sign()
            }
            derToRawSignature(der)
        } catch (e: Exception) {
            throw DPoPException(DPoPError.SigningFailed(e))
        }
    }

    override fun clear() {
        runCatching { keyStore.deleteEntry(keyAlias) }
            .getOrElse { throw DPoPException(DPoPError.KeyStoreFailed(it)) }
    }

    private fun generateKeyPair(useStrongBox: Boolean) {
        try {
            val generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                ANDROID_KEYSTORE,
            )
            val spec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
            ).apply {
                setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                setDigests(KeyProperties.DIGEST_SHA256)
                if (useStrongBox && isStrongBoxAvailable()) {
                    setIsStrongBoxBacked(true)
                }
            }.build()
            generator.initialize(spec)
            generator.generateKeyPair()
        } catch (e: ProviderException) {
            if (useStrongBox) {
                generateKeyPair(useStrongBox = false)
            } else {
                throw DPoPException(DPoPError.KeyGenerationFailed(e))
            }
        } catch (e: Exception) {
            throw DPoPException(DPoPError.KeyGenerationFailed(e))
        }
    }

    private fun loadPublicKey(): ECPublicKey {
        val key = runCatching { keyStore.getCertificate(keyAlias)?.publicKey }
            .getOrElse { throw DPoPException(DPoPError.KeyStoreFailed(it)) }
            ?: throw DPoPException(DPoPError.KeyNotFound)
        return key as? ECPublicKey ?: throw DPoPException(DPoPError.KeyStoreFailed())
    }

    private fun loadPrivateKey(): PrivateKey =
        runCatching { keyStore.getKey(keyAlias, null) as? PrivateKey }
            .getOrElse { throw DPoPException(DPoPError.KeyStoreFailed(it)) }
            ?: throw DPoPException(DPoPError.KeyNotFound)

    private fun ECPublicKey.toJwk(): DPoPJwk {
        val x = w.affineX.toUnsigned32()
        val y = w.affineY.toUnsigned32()
        return DPoPJwk(x = x.encodeBase64Url(), y = y.encodeBase64Url())
    }

    private fun isStrongBoxAvailable(): Boolean {
        val context: Context = ApplicationContextHolder.context
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
    }

    private companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}

/** Left-pads (or trims a sign byte) a coordinate to exactly 32 bytes. */
private fun BigInteger.toUnsigned32(): ByteArray {
    var bytes = toByteArray()
    if (bytes.size > 1 && bytes[0] == 0x00.toByte()) {
        bytes = bytes.copyOfRange(1, bytes.size)
    }
    if (bytes.size == 32) return bytes
    val padded = ByteArray(32)
    bytes.copyInto(padded, destinationOffset = 32 - bytes.size)
    return padded
}
