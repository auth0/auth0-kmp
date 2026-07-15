package com.auth0.kmp.core.dpop

import com.auth0.kmp.core.primitives.encodeBase64Url
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.Foundation.create
import platform.Foundation.numberWithInt
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecKeyCopyExternalRepresentation
import platform.Security.SecKeyCopyPublicKey
import platform.Security.SecKeyCreateRandomKey
import platform.Security.SecKeyCreateSignature
import platform.Security.SecKeyRef
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrApplicationTag
import platform.Security.kSecAttrIsPermanent
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeECSECPrimeRandom
import platform.Security.kSecAttrTokenID
import platform.Security.kSecAttrTokenIDSecureEnclave
import platform.Security.kSecClass
import platform.Security.kSecClassKey
import platform.Security.kSecKeyAlgorithmECDSASignatureMessageX962SHA256
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecPrivateKeyAttrs
import platform.Security.kSecReturnRef

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class IosDPoPKeyStore(private val keyTag: String) : DPoPKeyStore {

    override fun hasKey(): Boolean = autoreleasepool {
        val existing = copyPrivateKey()
        if (existing != null) {
            CFRelease(existing)
            true
        } else {
            false
        }
    }

    override fun publicJwk(): DPoPJwk = autoreleasepool {
        val privateKey = copyPrivateKey() ?: createPrivateKey()
        try {
            jwkFrom(privateKey)
        } finally {
            CFRelease(privateKey)
        }
    }

    override fun publicJwkOrNull(): DPoPJwk? = autoreleasepool {
        val privateKey = copyPrivateKey() ?: return@autoreleasepool null
        try {
            jwkFrom(privateKey)
        } finally {
            CFRelease(privateKey)
        }
    }

    /** Derives the public JWK from a retained private-key ref. Does not release [privateKey]. */
    private fun jwkFrom(privateKey: SecKeyRef): DPoPJwk {
        val publicKey = SecKeyCopyPublicKey(privateKey)
            ?: throw DPoPException(DPoPError.KeyStoreFailed())
        try {
            val external = memScoped {
                val error = alloc<CFErrorRefVar>()
                SecKeyCopyExternalRepresentation(publicKey, error.ptr)
                    ?: throw DPoPException(DPoPError.KeyStoreFailed(error.toThrowable()))
            }
            val point = (CFBridgingRelease(external) as NSData).toByteArray()
            return point.toJwk()
        } finally {
            CFRelease(publicKey)
        }
    }

    override fun sign(data: ByteArray): ByteArray = autoreleasepool {
        val privateKey = copyPrivateKey() ?: throw DPoPException(DPoPError.KeyNotFound)
        try {
            val cfData = CFBridgingRetain(data.toNSData())
            try {
                val signature = memScoped {
                    val error = alloc<CFErrorRefVar>()
                    SecKeyCreateSignature(
                        privateKey,
                        kSecKeyAlgorithmECDSASignatureMessageX962SHA256,
                        cfData?.reinterpret(),
                        error.ptr,
                    ) ?: throw DPoPException(DPoPError.SigningFailed(error.toThrowable()))
                }
                val der = (CFBridgingRelease(signature) as NSData).toByteArray()
                derToRawSignature(der)
            } finally {
                cfData?.let { CFRelease(it) }
            }
        } finally {
            CFRelease(privateKey)
        }
    }

    override fun clear() {
        autoreleasepool {
            withBaseKeyQuery { query ->
                val status = SecItemDelete(query)
                if (status != errSecSuccess && status != errSecItemNotFound) {
                    throw DPoPException(DPoPError.KeyStoreFailed(osStatusError(status)))
                }
            }
        }
    }

    /** Returns a retained `SecKeyRef` for the stored private key, or null if none exists. */
    private fun copyPrivateKey(): SecKeyRef? = withBaseKeyQuery { query ->
        CFDictionarySetValue(query, kSecReturnRef, kCFBooleanTrue)
        memScoped {
            val result = alloc<CFTypeRefVar>()
            @Suppress("UNCHECKED_CAST")
            when (val status = SecItemCopyMatching(query, result.ptr)) {
                errSecSuccess -> result.value as SecKeyRef?
                errSecItemNotFound -> null
                else -> throw DPoPException(DPoPError.KeyStoreFailed(osStatusError(status)))
            }
        }
    }

    /** Generates the P-256 keypair, preferring the Secure Enclave and falling back to a software key. */
    private fun createPrivateKey(): SecKeyRef {
        var lastError: Throwable? = null
        generate(useSecureEnclave = true) { lastError = it }?.let { return it }
        generate(useSecureEnclave = false) { lastError = it }?.let { return it }
        throw DPoPException(DPoPError.KeyGenerationFailed(lastError))
    }

    private fun generate(useSecureEnclave: Boolean, onError: (Throwable?) -> Unit): SecKeyRef? {
        val tag = CFBridgingRetain(keyTag.encodeToByteArray().toNSData())
        val keySize = CFBridgingRetain(NSNumber.numberWithInt(256))
        val privateAttrs = CFDictionaryCreateMutable(kCFAllocatorDefault, 3, null, null)
        val attributes = CFDictionaryCreateMutable(kCFAllocatorDefault, 4, null, null)
        try {
            CFDictionarySetValue(privateAttrs, kSecAttrIsPermanent, kCFBooleanTrue)
            CFDictionarySetValue(privateAttrs, kSecAttrApplicationTag, tag)

            CFDictionarySetValue(
                privateAttrs,
                kSecAttrAccessible,
                kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            )

            CFDictionarySetValue(attributes, kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
            CFDictionarySetValue(attributes, kSecAttrKeySizeInBits, keySize)
            if (useSecureEnclave) {
                CFDictionarySetValue(attributes, kSecAttrTokenID, kSecAttrTokenIDSecureEnclave)
            }
            CFDictionarySetValue(attributes, kSecPrivateKeyAttrs, privateAttrs)

            return memScoped {
                val error = alloc<CFErrorRefVar>()
                SecKeyCreateRandomKey(attributes, error.ptr).also {
                    if (it == null) onError(error.toThrowable())
                }
            }
        } finally {
            CFRelease(tag)
            CFRelease(keySize)
            CFRelease(privateAttrs)
            CFRelease(attributes)
        }
    }

    // Builds the identity query (class + tag + key type) shared by copyPrivateKey/clear, and centralises
    // CF memory management: the tag CFData (+1) must outlive the SecItem* call that consumes the dict, so
    // it is released only after block() returns — releasing it before (as a bare factory would) frees it
    // while SecItemCopyMatching/SecItemDelete still reads it.
    private fun <T> withBaseKeyQuery(block: (CFMutableDictionaryRef?) -> T): T {
        val tag = CFBridgingRetain(keyTag.encodeToByteArray().toNSData())
        val query = CFDictionaryCreateMutable(kCFAllocatorDefault, 4, null, null)
        CFDictionarySetValue(query, kSecClass, kSecClassKey)
        CFDictionarySetValue(query, kSecAttrApplicationTag, tag)
        CFDictionarySetValue(query, kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
        CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
        return try {
            block(query)
        } finally {
            CFRelease(tag)
            CFRelease(query)
        }
    }
}

/** Bridges the CFError left in an out-parameter by a Security call into a [Throwable], or null if unset. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun CFErrorRefVar.toThrowable(): Throwable? =
    (value?.let { CFBridgingRelease(it) } as? NSError)?.let {
        Throwable("${it.domain}(${it.code}): ${it.localizedDescription}")
    }

/** Wraps an OSStatus code from a keychain call (which carries no CFError) into a [Throwable]. */
private fun osStatusError(status: Int): Throwable = Throwable("OSStatus $status")

/** Splits a 65-byte X9.63 EC point (`0x04 ‖ X ‖ Y`) into a base64url JWK. */
private fun ByteArray.toJwk(): DPoPJwk {
    require(size == 65 && this[0] == 0x04.toByte()) { "Unexpected EC public key encoding" }
    val x = copyOfRange(1, 33)
    val y = copyOfRange(33, 65)
    return DPoPJwk(x = x.encodeBase64Url(), y = y.encodeBase64Url())
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) {
        NSData()
    } else {
        usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
    }

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val length = length.toInt()
    if (length == 0) return ByteArray(0)
    val out = ByteArray(length)
    out.usePinned { pinned ->
        platform.posix.memcpy(pinned.addressOf(0), bytes, length.toULong())
    }
    return out
}
