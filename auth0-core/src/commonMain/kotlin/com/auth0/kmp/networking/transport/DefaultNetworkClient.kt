package com.auth0.kmp.networking.transport

import com.auth0.kmp.core.error.TransportError
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.networking.NetworkClient
import com.auth0.kmp.networking.request.NetworkRequest
import com.auth0.kmp.networking.retry.RetryPolicy
import com.auth0.kmp.networking.retry.withRetry
import io.ktor.client.HttpClient

internal class DefaultNetworkClient(
    private val client: HttpClient,
    private val resolver: EndpointResolver,
) : NetworkClient {

    override suspend fun <T> request(
        request: NetworkRequest,
        retryPolicy: RetryPolicy,
        deserialize: (String) -> T,
    ): Result<T, TransportError> {
        val url = resolver.resolve(request.path)
        return withRetry(retryPolicy) { safeCall(client, url, request, deserialize) }
    }

    override fun close() {
        client.close()
    }
}
