package com.auth0.kmp.authentication

import com.auth0.kmp.authentication.error.AuthenticationError
import com.auth0.kmp.core.RequestOptions
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.networking.NetworkClient
import com.auth0.kmp.networking.request.HttpMethod
import com.auth0.kmp.networking.request.NetworkRequest
import com.auth0.kmp.networking.transport.json
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Serializes a JSON request body, seeding it with any extra [RequestOptions.parameters]
 * before applying [build]. Shared by the authentication clients so their request
 * construction stays consistent.
 */
internal inline fun jsonBody(
    options: RequestOptions,
    build: JsonObjectBuilder.() -> Unit,
): String = json.encodeToString(
    buildJsonObject {
        options.parameters.forEach { (key, value) -> put(key, value) }
        build()
    },
)

/**
 * Sends a `POST` to [path] with [body] and maps the transport outcome into an
 * [AuthenticationError] result. Shared by the authentication clients.
 */
internal suspend fun <T> NetworkClient.post(
    path: String,
    body: String,
    options: RequestOptions,
    deserialize: (String) -> T,
): Result<T, AuthenticationError> {
    val request = NetworkRequest(
        method = HttpMethod.POST,
        path = path,
        headers = options.headers,
        body = body,
    )
    return request(request, options.retryPolicy, deserialize).toAuthResult()
}
