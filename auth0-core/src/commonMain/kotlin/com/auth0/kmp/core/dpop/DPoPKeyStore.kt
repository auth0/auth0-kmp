package com.auth0.kmp.core.dpop

/**
 * Platform-backed store for the DPoP keypair.
 *
 * The private key is generated in, and never leaves, the platform's hardware-backed
 * secure store (Android Keystore, iOS Secure Enclave / Keychain). Implementations are
 * expected to throw a platform exception on failure; callers translate those into a
 * [DPoPError].
 */
public interface DPoPKeyStore {

    /** Whether a DPoP keypair is already present in the store. */
    public fun hasKey(): Boolean

    /** The public JWK for the DPoP keypair, generating the keypair first if none exists. */
    public fun publicJwk(): DPoPJwk

    /**
     * Signs [data] with the DPoP private key.
     *
     * @return the raw `ES256` signature as concatenated R‖S (64 bytes), ready for a JOSE proof.
     */
    public fun sign(data: ByteArray): ByteArray

    /** Removes the DPoP keypair from the store, if present. */
    public fun clear()
}
