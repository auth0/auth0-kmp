package com.auth0.kmp.core.token

import com.auth0.kmp.core.error.TransportError
import com.auth0.kmp.core.model.Credentials
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.core.result.map
import com.auth0.kmp.networking.NetworkClient
import com.auth0.kmp.networking.request.HttpMethod
import com.auth0.kmp.networking.request.NetworkRequest
import com.auth0.kmp.networking.transport.json
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.time.Clock

internal class DefaultTokenClient(
    private val networkClient: NetworkClient,
    private val clock: Clock
) : TokenClient {
    override suspend fun fetchToken(
        grant: TokenGrant,
        headers: Map<String, String>
    ): Result<Credentials, TransportError> {
        val body = json.encodeToString(
            MapSerializer(String.serializer(), String.serializer()),
            grant.parameters
        )

        val request = NetworkRequest(
            method = HttpMethod.POST,
            path = "/oauth/token",
            headers = headers,
            body = body
        )

        return networkClient.request(request) {
            json.decodeFromString<TokenResponse>(it)
        }.map { it.toCredentials(clock) }
    }

    override fun close() {
        networkClient.close()
    }
}