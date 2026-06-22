package com.auth0.kmp.core.error

/**
 * Transport-level failures shared across every networked feature of the SDK.
 */
public sealed interface TransportError : Auth0Error {

    /** No connectivity or the host could not be reached. */
    public data object NoInternet : TransportError

    /** The request or socket timed out before a response arrived. */
    public data object Timeout : TransportError

    /**
     * A non-2xx HTTP response. The body is always retained so callers can parse
     * the server's error payload.
     *
     * @param status the HTTP status code.
     * @param body the raw response body, if any.
     */
    public data class Server(val status: Int, val body: String?) : TransportError

    /**
     * A response was received but could not be decoded into the expected type.
     *
     * @param message a description of what failed to deserialize.
     */
    public data class Serialization(val message: String) : TransportError

    /**
     * Catch-all so the transport layer can always map a failure into a typed
     * error instead of leaking a raw exception.
     *
     * @param message an optional description of the underlying failure.
     */
    public data class Unknown(val message: String?) : TransportError
}

/** True when the failure is an HTTP 401 response. */
public val TransportError.isUnauthorized: Boolean
    get() = this is TransportError.Server && status == 401

/** True when the failure is an HTTP 403 response. */
public val TransportError.isForbidden: Boolean
    get() = this is TransportError.Server && status == 403
