package com.auth0.kmp.networking

import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.error.NetworkError
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.networking.request.NetworkRequest
import com.auth0.kmp.networking.retry.RetryPolicy

/**
 * Executes Auth0 HTTP requests and maps each outcome to a [Result].
 */
@InternalAuth0Api
public interface NetworkClient : AutoCloseable {

    /**
     * Sends [request] and returns its outcome.
     *
     * @param request the transport-level description of the request to send.
     * @param retryPolicy how the request is retried on failure.
     * @param deserialize converts a successful response body into [T]. Invoked
     *   only for a successful response.
     * @return [Result.Success] with the deserialized body, or [Result.Failure]
     *   with the mapped [NetworkError].
     */
    public suspend fun <T> request(
        request: NetworkRequest,
        retryPolicy: RetryPolicy = RetryPolicy.None,
        deserialize: (String) -> T
    ): Result<T, NetworkError>

    /**
     * Releases the underlying HTTP resources (connection pool, threads, and the
     * client's coroutine scope). After calling this, the client must not be
     * reused. A long-lived, per-account client typically never needs this.
     */
    override fun close()
}