package com.auth0.kmp.authentication.error

import com.auth0.kmp.core.error.Auth0Error
import com.auth0.kmp.core.error.TransportError
import com.auth0.kmp.core.error.parseAuth0ErrorBody
import com.auth0.kmp.core.validation.IdTokenValidationError

/**
 * Failures surfaced by an authentication operation.
 */
public sealed interface AuthenticationError : Auth0Error {

    /**
     * Auth0 received the request and rejected it with an error payload.
     *
     * @param code the Auth0 error code, for example `invalid_grant`.
     * @param errorDescription a human-readable explanation of the failure.
     * @param statusCode the HTTP status code that carried the error.
     */
    public data class ApiError(
        val code: String,
        val errorDescription: String,
        val statusCode: Int,
    ) : AuthenticationError

    /**
     * A local validation check failed and the request was never sent.
     *
     * @param message a description of which input was invalid.
     */
    public data class InvalidInput(val message: String) : AuthenticationError

    /**
     * The request did not complete because of a connectivity or timeout failure;
     * retrying once the connection is restored may succeed.
     *
     * @param cause the underlying transport failure.
     */
    public data class Network(val cause: TransportError) : AuthenticationError

    /**
     * The request failed in a way that could not be interpreted as any other
     * case; retrying is unlikely to help on its own.
     *
     * @param cause the underlying transport failure.
     */
    public data class Unknown(val cause: TransportError) : AuthenticationError

    /**
     * Auth0 returned credentials, but the ID token failed validation.
     *
     * @param cause the validation check that failed.
     */
    public data class IdTokenValidation(val cause: IdTokenValidationError) : AuthenticationError
}


internal fun TransportError.toAuthenticationError(): AuthenticationError = when (this) {
    TransportError.NoInternet,
    TransportError.Timeout -> AuthenticationError.Network(this)

    is TransportError.Server -> parseAuth0ErrorBody(body)
        ?.let { AuthenticationError.ApiError(it.error, it.errorDescription ?: it.error, status) }
        ?: AuthenticationError.Unknown(this)

    is TransportError.Serialization,
    is TransportError.Unknown -> AuthenticationError.Unknown(this)
}