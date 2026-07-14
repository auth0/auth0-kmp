package com.auth0.kmp.core.dpop

/**
 * Creates the platform-backed [DPoPKeyStore] for the current target.
 *
 * @param keyTag the platform key identifier the keypair is stored under; scope it per
 *   account so each account's keypair has an independent lifecycle.
 */
internal expect fun createDPoPKeyStore(keyTag: String): DPoPKeyStore
