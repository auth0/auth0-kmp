package com.auth0.kmp.core.primitives

import com.auth0.kmp.core.annotation.InternalAuth0Api
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFAllocatorDefault
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecKeyCreateWithData
import platform.Security.SecKeyRef
import platform.Security.SecKeyVerifySignature
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPublic
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeRSA
import platform.Security.kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256
import platform.Security.kSecRandomDefault

@OptIn(ExperimentalForeignApi::class)
@InternalAuth0Api
actual fun ByteArray.sha256(): ByteArray {
    val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
    digest.usePinned { output ->
        if (isEmpty()) {
            CC_SHA256(null, 0u, output.addressOf(0).reinterpret<UByteVar>())
        } else {
            usePinned { input ->
                CC_SHA256(
                    input.addressOf(0), size.toUInt(), output.addressOf(0).reinterpret<UByteVar>()
                )
            }
        }
    }
    return digest
}


@OptIn(ExperimentalForeignApi::class)
@InternalAuth0Api
actual fun generateSecureRandomBytes(size: Int): ByteArray {
    if (size == 0) return ByteArray(0)
    val bytes = ByteArray(size)
    bytes.usePinned { pinned ->
        val result = SecRandomCopyBytes(kSecRandomDefault, size.toULong(), pinned.addressOf(0))
        check(result == errSecSuccess) { "SecRandomCopyBytes failed with code $result" }
    }
    return bytes
}

@OptIn(ExperimentalForeignApi::class)
@InternalAuth0Api
actual fun verifyRs256(
    signedData: ByteArray,
    signature: ByteArray,
    modulus: ByteArray,
    exponent: ByteArray
): Boolean {
    val key = createRsaPublicKey(pkcs1PublicKey(modulus, exponent)) ?: return false

    val signedCf = CFBridgingRetain(signedData.toNSData())
    val signatureCf = CFBridgingRetain(signature.toNSData())

    val valid = SecKeyVerifySignature(
        key,
        kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256,
        signedCf as CFDataRef,
        signatureCf as CFDataRef,
        null
    )
    CFRelease(signedCf)
    CFRelease(signatureCf)
    CFRelease(key)
    return valid
}

// Encodes a DER length field. Short form (< 0x80) is a single byte; long form is
// 0x80-or'd with the count of big-endian length bytes that follow. Every length
// byte is kept, including zeros (e.g. 256 -> 0x82 0x01 0x00).
private fun derLength(length: Int): ByteArray =
    if (length < 0x80) {
        byteArrayOf(length.toByte())
    } else {
        val lengthBytes = mutableListOf<Byte>()
        var remaining = length
        while (remaining > 0) {
            lengthBytes.add(0, (remaining and 0xFF).toByte())
            remaining = remaining shr 8
        }
        byteArrayOf((0x80 or lengthBytes.size).toByte()) + lengthBytes
    }

// Wraps raw bytes as a DER INTEGER (tag 0x02). A leading 0x00 is prepended when
// the high bit of the first byte is set, so the value is read as positive.
private fun derInteger(content: ByteArray): ByteArray {
    val body = if (content.isNotEmpty() && content[0].toInt() and 0x80 != 0) {
        byteArrayOf(0x00) + content
    } else {
        content
    }
    return byteArrayOf(0x02) + derLength(body.size) + body
}

// Assembles a PKCS#1 RSA public key: SEQUENCE (0x30) { INTEGER modulus, INTEGER exponent }.
private fun pkcs1PublicKey(modulus: ByteArray, exponent: ByteArray): ByteArray {
    val sequenceBody = derInteger(modulus) + derInteger(exponent)
    return byteArrayOf(0x30) + derLength(sequenceBody.size) + sequenceBody
}

// Builds a SecKey from PKCS#1 DER bytes. Returns null when the bytes are not a
// valid RSA public key, so callers can fail closed instead of throwing.
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun createRsaPublicKey(derBytes: ByteArray): SecKeyRef? {
    // Pin the bytes so the GC can't move them while NSData reads the address.
    val data = derBytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = derBytes.size.toULong())
    }
    // Declare the bytes as an RSA public key for SecKeyCreateWithData.
    val attributes = CFDictionaryCreateMutable(kCFAllocatorDefault, 2, null, null)
    CFDictionarySetValue(attributes, kSecAttrKeyType, kSecAttrKeyTypeRSA)
    CFDictionarySetValue(attributes, kSecAttrKeyClass, kSecAttrKeyClassPublic)

    // CFBridgingRetain hands ownership to CF (+1); release it once the key is built.
    val cfData = CFBridgingRetain(data)
    val key = SecKeyCreateWithData(cfData as CFDataRef, attributes, null)

    CFRelease(cfData)
    CFRelease(attributes)
    return key
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData =
    usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

