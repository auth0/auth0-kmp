package com.auth0.kmp.webauth.error

import com.auth0.kmp.core.dpop.DPoPError
import com.auth0.kmp.core.error.Auth0Error
import com.auth0.kmp.core.error.TransportError
import com.auth0.kmp.core.error.parseAuth0ErrorBody
import com.auth0.kmp.core.validation.IdTokenValidationError

/**
 * Failures surfaced by a Web Auth (browser-based) login operation.
 */
public sealed interface WebAuthError : Auth0Error {

    /** The user dismissed the browser, or the flow was cancelled programmatically. */
    public data object UserCancelled : WebAuthError

    /** A Web Auth operation is already in progress; only one runs at a time. */
    public data object TransactionActiveAlready : WebAuthError

    /** The `state` returned by the browser did not match the pending transaction. */
    public data object InvalidState : WebAuthError

    /**
     * The browser could not be launched or failed before returning a result.
     *
     * @param message a description of the browser failure, when available.
     */
    public data class BrowserError(val message: String?) : WebAuthError

    /**
     * Auth0 returned an error on the redirect, before any token request was made.
     *
     * @param code the Auth0 error code, for example `access_denied`.
     * @param errorDescription a human-readable explanation of the failure.
     */
    public data class AuthorizationError(
        val code: String,
        val errorDescription: String,
    ) : WebAuthError

    /**
     * Auth0 rejected the token request at `/oauth/token` with an error payload.
     *
     * @param code the Auth0 error code, for example `invalid_grant`.
     * @param errorDescription a human-readable explanation of the failure.
     * @param statusCode the HTTP status code that carried the error.
     */
    public data class ApiError(
        val code: String,
        val errorDescription: String,
        val statusCode: Int,
    ) : WebAuthError

    /**
     * The request did not complete because of a connectivity or timeout failure;
     * retrying once the connection is restored may succeed.
     *
     * @param cause the underlying transport failure.
     */
    public data class Network(val cause: TransportError) : WebAuthError

    /**
     * The request failed in a way that could not be interpreted as any other
     * case; retrying is unlikely to help on its own.
     *
     * @param cause the underlying transport failure.
     */
    public data class Unknown(val cause: TransportError) : WebAuthError

    /**
     * Auth0 returned credentials, but the ID token failed signature or claims
     * validation.
     *
     * @param cause the validation check that failed.
     */
    public data class IdTokenValidation(val cause: IdTokenValidationError) : WebAuthError

    /**
     * The login could not be bound to the account's DPoP keypair, so no
     * authorization request was made.
     *
     * @param cause the underlying DPoP failure.
     */
    public data class DPoP(val cause: DPoPError) : WebAuthError
}


internal fun TransportError.toWebAuthError(): WebAuthError = when (this) {
    TransportError.NoInternet,
    TransportError.Timeout -> WebAuthError.Network(this)

    is TransportError.Server -> parseAuth0ErrorBody(body)
        ?.let { WebAuthError.ApiError(it.error, it.errorDescription ?: it.error, status) }
        ?: WebAuthError.Unknown(this)

    is TransportError.Serialization,
    is TransportError.Unknown -> WebAuthError.Unknown(this)
}

internal fun DPoPError.toWebAuthError(): WebAuthError = WebAuthError.DPoP(this)
