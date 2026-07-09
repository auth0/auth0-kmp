package com.auth0.kmp.core.credentials

import com.auth0.kmp.core.error.Auth0Error
import com.auth0.kmp.core.error.TransportError

/**
 * Failures surfaced by a credentials-manager operation.
 */
public sealed interface CredentialsManagerError : Auth0Error {

    /** No credentials are stored. */
    public data object NoCredentials : CredentialsManagerError

    /** A renewal was required but no refresh token is available. */
    public data object NoRefreshToken : CredentialsManagerError

    /**
     * The renewed token's own lifetime is still shorter than the requested TTL.
     *
     * @param minTtl the requested minimum lifetime, in seconds.
     * @param lifetime the renewed token's actual lifetime, in seconds.
     */
    public data class LargeMinTtl(val minTtl: Int, val lifetime: Long) : CredentialsManagerError

    /**
     * Auth0 rejected the renewal with an error payload.
     *
     * @param code the Auth0 error code, for example `invalid_grant`.
     * @param description a human-readable explanation of the failure.
     * @param statusCode the HTTP status code that carried the error.
     */
    public data class ApiError(
        val code: String,
        val description: String,
        val statusCode: Int,
    ) : CredentialsManagerError

    /**
     * The renewal did not complete because of a connectivity or timeout failure.
     *
     * @param cause the underlying transport failure.
     */
    public data class Network(val cause: TransportError) : CredentialsManagerError

    /**
     * The renewal failed in a way that could not be interpreted as any other case.
     *
     * @param cause the underlying transport failure.
     */
    public data class Unknown(val cause: TransportError) : CredentialsManagerError

    /**
     * Reading from or writing to secure storage failed.
     *
     * @param cause the underlying storage failure, if any.
     */
    public data class StoreFailed(val cause: Throwable? = null) : CredentialsManagerError

    /**
     * Stored credentials could not be decoded.
     *
     * @param cause the underlying deserialization failure, if any.
     */
    public data class DeserializationFailed(val cause: Throwable? = null) : CredentialsManagerError

    /**
     * A stored value could not be encrypted or decrypted, for example because the
     * device key protecting it has been invalidated.
     *
     * @param cause the underlying cryptographic failure, if any.
     */
    public data class CryptoFailed(val cause: Throwable? = null) : CredentialsManagerError
}
