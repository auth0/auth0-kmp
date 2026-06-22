package com.auth0.kmp.authentication.response

import com.auth0.kmp.networking.transport.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The error payload returned by Auth0 OAuth endpoints.
 *
 * @param error the Auth0 error code, for example `invalid_grant`.
 * @param errorDescription a human-readable explanation, when provided.
 */
@Serializable
internal data class ErrorResponse(
    val error: String,
    @SerialName("error_description")
    val errorDescription: String? = null,
)


/**
 * Decodes [body] into an [ErrorResponse], or returns `null` when the body is
 * absent or is not a recognizable Auth0 error payload.
 */
internal fun parseAuth0ErrorBody(body: String?): ErrorResponse? {
    if (body.isNullOrBlank()) return null
    return runCatching { json.decodeFromString<ErrorResponse>(body) }.getOrNull()
}