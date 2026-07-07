package com.auth0.kmp.webauth.jwks

import com.auth0.kmp.core.result.Result
import com.auth0.kmp.networking.NetworkClient
import com.auth0.kmp.networking.request.HttpMethod
import com.auth0.kmp.networking.request.NetworkRequest
import com.auth0.kmp.networking.transport.json

internal class DefaultJwksProvider(
    private val networkClient: NetworkClient,
) : JwksProvider {

    // A JwksProvider is created per WebAuthClient, and login() is
    // single-flight (rejects a second concurrent call with TransactionActiveAlready), so the cache
    // is only ever touched by one coroutine at a time.
    private val cache = mutableMapOf<String, Jwk>()

    override suspend fun fetch(kid: String): Jwk? {
        cache[kid]?.let { return it }
        // Cache miss: refetch the whole JWKS so a rotated-in key is picked up.
        refresh()
        return cache[kid]
    }

    private suspend fun refresh() {
        val request = NetworkRequest(
            method = HttpMethod.GET,
            path = "/.well-known/jwks.json",
        )
        val result = networkClient.request(request) {
            json.decodeFromString<JwksResponse>(it)
        }
        if (result is Result.Success) {
            result.data.keys.forEach { key ->
                key.toJwkOrNull()?.let { cache[it.kid] = it }
            }
        }
    }
}
