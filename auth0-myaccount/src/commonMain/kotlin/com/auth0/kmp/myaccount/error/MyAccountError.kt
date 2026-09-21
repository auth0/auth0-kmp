package com.auth0.kmp.myaccount.error

import com.auth0.kmp.core.error.Auth0Error
import com.auth0.kmp.core.error.TransportError

/**
 * Failures surfaced by My Account API operation.
 */
public sealed interface MyAccountError : Auth0Error {

    /**
     * Auth0 received the request and rejected it with an error payload.
     *
     * @param type a URI identifying the problem type.
     * @param title a short, human-readable summary of the problem.
     * @param detail a human-readable explanation specific to this occurrence.
     * @param status the HTTP status code that carried the error.
     * @param validationErrors the per-field validation failures, empty when none.
     */
    public data class ApiError(
        val type: String,
        val title: String,
        val detail: String,
        val status: Int,
        val validationErrors: List<ValidationError> = emptyList(),
    ) : MyAccountError

    /**
     * A local validation check failed and the request was never sent.
     *
     * @param message a description of which input was invalid.
     */
    public data class InvalidInput(val message: String) : MyAccountError

    /**
     * The request did not complete because of a connectivity or timeout failure;
     * retrying once the connection is restored may succeed.
     *
     * @param cause the underlying transport failure.
     */
    public data class Network(val cause: TransportError) : MyAccountError

    /**
     * The request succeeded but the response was missing data the SDK required to
     * continue the operation — for enrollment, the identifier of the newly created
     * authentication method.
     *
     * @param message a description of what the response was missing.
     */
    public data class MalformedResponse(val message: String) : MyAccountError

    /**
     * The request failed in a way that could not be interpreted as any other
     * case; retrying is unlikely to help on its own.
     *
     * @param cause the underlying transport failure.
     */
    public data class Unknown(val cause: TransportError) : MyAccountError
}
