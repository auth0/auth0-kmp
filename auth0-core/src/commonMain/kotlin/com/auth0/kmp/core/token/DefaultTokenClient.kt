package com.auth0.kmp.core.token

import com.auth0.kmp.core.error.TransportError
import com.auth0.kmp.core.model.Credentials
import com.auth0.kmp.core.model.SsoCredentials
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.core.result.map
import com.auth0.kmp.networking.NetworkClient
import com.auth0.kmp.networking.request.HttpMethod
import com.auth0.kmp.networking.request.NetworkRequest
import com.auth0.kmp.networking.retry.RetryPolicy
import com.auth0.kmp.networking.transport.json
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

internal class DefaultTokenClient(
    private val networkClient: NetworkClient,
    private val clock: Clock
) : TokenClient {
    override suspend fun fetchToken(
        grant: TokenGrant,
        headers: Map<String, String>,
        retryPolicy: RetryPolicy
    ): Result<Credentials, TransportError> {
        val request = createTokenRequest(grant.parameters, headers)

        return networkClient.request(request, retryPolicy) {
            json.decodeFromString<TokenResponse>(it)
        }.map { it.toCredentials(clock) }
    }

    override suspend fun fetchSsoCredentials(
        grant: TokenGrant,
        headers: Map<String, String>,
        retryPolicy: RetryPolicy
    ): Result<SsoCredentials, TransportError> {
        val request = createTokenRequest(grant.parameters, headers)

        return networkClient.request(request, retryPolicy) {
            json.decodeFromString<TokenResponse>(it)
        }.map { it.toSsoCredentials(clock) }
    }

    private fun createTokenRequest(
        parameters: JsonObject,
        headers: Map<String, String>
    ): NetworkRequest {
        val body = json.encodeToString(parameters)

        return NetworkRequest(
            method = HttpMethod.POST,
            path = "/oauth/token",
            headers = headers,
            body = body
        )
    }
}