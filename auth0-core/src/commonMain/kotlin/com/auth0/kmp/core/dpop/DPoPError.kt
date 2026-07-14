package com.auth0.kmp.core.dpop

import com.auth0.kmp.core.error.Auth0Error

/**
 * Failures surfaced by DPoP key management and proof generation.
 */
public sealed interface DPoPError : Auth0Error {

    /**
     * A DPoP keypair could not be created in the platform key store.
     *
     * @param cause the underlying key-generation failure, if any.
     */
    public data class KeyGenerationFailed(val cause: Throwable? = null) : DPoPError

    /**
     * Reading from or writing to the platform key store failed.
     *
     * @param cause the underlying key-store failure, if any.
     */
    public data class KeyStoreFailed(val cause: Throwable? = null) : DPoPError

    /** A proof was required but no DPoP keypair is present in the key store. */
    public data object KeyNotFound : DPoPError

    /**
     * Signing the proof with the DPoP private key failed.
     *
     * @param cause the underlying signing failure, if any.
     */
    public data class SigningFailed(val cause: Throwable? = null) : DPoPError

    /** The request URL could not be parsed into a DPoP `htu` claim. */
    public data object MalformedUrl : DPoPError

    /**
     * A DPoP operation failed in a way that could not be interpreted as any other case.
     *
     * @param cause the underlying failure, if any.
     */
    public data class Unknown(val cause: Throwable? = null) : DPoPError
}
