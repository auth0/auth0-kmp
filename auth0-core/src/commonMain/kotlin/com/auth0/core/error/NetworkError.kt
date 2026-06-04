package com.auth0.core.error

/**
 * Transport-level failures shared across every networked feature of the SDK.
 */
sealed interface NetworkError : Auth0Error {

    /** No connectivity or the host could not be reached. */
    data object NoInternet : NetworkError

    /** The request or socket timed out before a response arrived. */
    data object Timeout : NetworkError

    /** HTTP 401 — credentials are missing, invalid, or expired. */
    data object Unauthorized : NetworkError

    /** HTTP 403 — authenticated, but not permitted to perform the request. */
    data object Forbidden : NetworkError

    /**
     * Any non-2xx response other than 401/403.
     *
     * @param status the HTTP status code.
     * @param body the raw response body, if any, for inspection or logging.
     */
    data class Server(val status: Int, val body: String?) : NetworkError

    /**
     * A response was received but could not be decoded into the expected type.
     *
     * @param message a description of what failed to deserialize.
     */
    data class Serialization(val message: String) : NetworkError

    /**
     * Catch-all so the networking layer can always map a failure into a typed
     * error instead of leaking a raw exception.
     *
     * @param message an optional description of the underlying failure.
     */
    data class Unknown(val message: String?) : NetworkError
}
