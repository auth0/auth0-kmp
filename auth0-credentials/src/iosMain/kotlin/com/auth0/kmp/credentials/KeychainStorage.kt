package com.auth0.kmp.credentials

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

private const val KEYCHAIN_SERVICE = "auth0_credentials"

// The Keychain encrypts stored items natively, so this class holds no app-level crypto: any
// SecItem* failure surfaces as a plain error(), which the manager maps to StoreFailed. It never
// throws StorageCryptoException, so the manager's CryptoFailed self-heal path is unreachable on
// iOS by design (that path exists only for the Android Tink layer).
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class KeychainStorage(
    private val service: String = KEYCHAIN_SERVICE,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : Storage {

    // Keychain access is offloaded off the caller thread: the synchronous SecItem* calls can block
    // (first-unlock waits, securityd contention) and Swift callers invoke these from @MainActor, so
    // running them inline would jank the UI. autoreleasepool drains the autoreleased ObjC temporaries
    // the bridging calls create on that worker thread, which has no ambient pool of its own.
    override suspend fun retrieve(key: String): String? = withContext(coroutineDispatcher) {
        autoreleasepool {
            withBaseQuery(key) { query ->
                // Ask the Keychain to return the stored bytes (not just existence), and at most one item.
                CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
                CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
                memScoped {
                    // SecItemCopyMatching returns an OSStatus and writes the found item into this out-slot.
                    val result = alloc<CFTypeRefVar>()
                    when (val status = SecItemCopyMatching(query, result.ptr)) {
                        // No entry stored yet is a valid state, not an error: the caller maps null to NoCredentials.
                        errSecItemNotFound -> null
                        errSecSuccess -> {
                            // Copy rule: the returned object is owned by us; CFBridgingRelease consumes that +1.
                            val data = CFBridgingRelease(result.value) as? NSData
                            // Invalid UTF-8 decodes to null, i.e. unreadable data is treated as absent.
                            // `as String?` bridges NSString?->String? — required despite the "no cast needed" warning.
                            @Suppress("USELESS_CAST")
                            data?.let { NSString.create(it, NSUTF8StringEncoding) as String? }
                        }
                        // Any other status is a store failure (no crypto layer on iOS); a plain throw maps to StoreFailed.
                        else -> error("Keychain retrieve failed with status $status")
                    }
                }
            }
        }
    }

    override suspend fun store(key: String, value: String): Unit =
        withContext(coroutineDispatcher) {
            autoreleasepool {
                // UTF-8 encode into NSData via a pinned ByteArray (no fragile String->NSString cast); inverse of retrieve's decode.
                val bytes = value.encodeToByteArray()
                val data = bytes.usePinned {
                    NSData.create(
                        bytes = it.addressOf(0),
                        length = bytes.size.toULong()
                    )
                }
                val cfData = CFBridgingRetain(data)
                try {
                    withBaseQuery(key) { query ->
                        // The bytes to store, plus device-only protection: readable after first unlock, never synced to iCloud.
                        CFDictionarySetValue(query, kSecValueData, cfData)
                        CFDictionarySetValue(
                            query, kSecAttrAccessible,
                            kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
                        )
                        when (val addStatus = SecItemAdd(query, null)) {
                            errSecSuccess -> Unit
                            // Item already exists: update its data in place (identity attributes still match).
                            errSecDuplicateItem -> updateData(key, cfData)
                            else -> error("Keychain store failed with status $addStatus")
                        }
                    }
                } finally {
                    CFRelease(cfData)
                }
            }
        }

    override suspend fun remove(key: String): Unit = withContext(coroutineDispatcher) {
        autoreleasepool {
            withBaseQuery(key) { query ->
                // Delete the matching item. Not-found is treated as success so remove() is idempotent.
                when (val status = SecItemDelete(query)) {
                    errSecSuccess, errSecItemNotFound -> Unit
                    else -> error("Keychain remove failed with status $status")
                }
            }
        }
    }

    // Builds the identity query (class + service + account) shared by every operation, and centralises
    // CF memory management: each CFBridgingRetain (+1) is balanced by a CFRelease once block() returns.
    private fun <T> withBaseQuery(key: String, block: (CFMutableDictionaryRef?) -> T): T {
        val query = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, null, null)
        val cfService = CFBridgingRetain(service)
        val cfAccount = CFBridgingRetain(key)
        CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionarySetValue(query, kSecAttrService, cfService)
        CFDictionarySetValue(query, kSecAttrAccount, cfAccount)
        return try {
            block(query)
        } finally {
            CFRelease(cfService)
            CFRelease(cfAccount)
            CFRelease(query)
        }
    }

    // Updates the stored bytes for an existing entry, matched by the base identity query.
    private fun updateData(key: String, cfData: CFTypeRef?) {
        withBaseQuery(key) { query ->
            val attributes = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, null, null)
            CFDictionarySetValue(attributes, kSecValueData, cfData)
            try {
                val status = SecItemUpdate(query as CFDictionaryRef, attributes as CFDictionaryRef)
                if (status != errSecSuccess) error("Keychain update failed with status $status")
            } finally {
                CFRelease(attributes)
            }
        }
    }
}
