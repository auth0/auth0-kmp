package com.auth0.kmp.core.primitives

import com.auth0.kmp.core.annotation.InternalAuth0Api
import java.math.BigInteger
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.RSAPublicKeySpec

@InternalAuth0Api
actual fun ByteArray.sha256(): ByteArray = MessageDigest.getInstance("SHA-256").digest(this)


@InternalAuth0Api
actual fun generateSecureRandomBytes(size: Int): ByteArray =
    ByteArray(size).also { SecureRandom().nextBytes(it) }


@InternalAuth0Api
actual fun verifyRs256(
    signedData: ByteArray, signature: ByteArray, modulus: ByteArray, exponent: ByteArray
): Boolean = try {
    val publicKey = KeyFactory.getInstance("RSA")
        .generatePublic(RSAPublicKeySpec(BigInteger(1, modulus),
            BigInteger(1, exponent)))

    Signature.getInstance("SHA256withRSA").apply {
        initVerify(publicKey)
        update(signedData)
    }.verify(signature)
} catch (e: GeneralSecurityException) {
    false
}
