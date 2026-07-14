package com.auth0.kmp.core.dpop

import com.auth0.kmp.core.annotation.InternalAuth0Api
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Holds the most recent DPoP nonce for a single account.
 *
 * A DPoP nonce is a short-lived, server-issued value that must be echoed in the next
 * proof. The server rotates it via the `DPoP-Nonce` response header, so the store simply
 * keeps the latest value seen. One instance is scoped per account, matching the account
 * scope of the keypair and transport.
 */
@OptIn(ExperimentalAtomicApi::class)
@InternalAuth0Api
public class DPoPNonceStore {

    private val nonce = AtomicReference<String?>(null)

    /** The most recently stored nonce, or `null` if none has been seen. */
    public fun current(): String? = nonce.load()


    /** Records the latest nonce issued by the server. */
    public fun update(value: String) {
        nonce.store(value)
    }

    /** Clears the stored nonce. */
    public fun clear() {
        nonce.store(null)
    }
}
