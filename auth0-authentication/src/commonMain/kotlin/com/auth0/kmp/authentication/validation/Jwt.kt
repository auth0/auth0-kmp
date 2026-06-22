package com.auth0.kmp.authentication.validation

import com.auth0.kmp.networking.transport.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64

internal data class JwtClaims(
    val issuer: String?,
    val subject: String?,
    val audience: List<String>,
    val expiresAt: Long?,
    val issuedAt: Long?,
)

private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)

@Serializable
private data class JwtPayload(
    val iss: String? = null,
    val sub: String? = null,
    val aud: JsonElement? = null,
    val exp: Long? = null,
    val iat: Long? = null,
)

internal fun decodeJwtClaims(idToken: String): JwtClaims? {
    val segments = idToken.split(".")
    if (segments.size != 3) return null
    return runCatching {
        val bytes = base64Url.decode(segments[1])
        val payload = json.decodeFromString<JwtPayload>(bytes.decodeToString())
        JwtClaims(
            issuer = payload.iss,
            subject = payload.sub,
            audience = payload.aud.toAudienceList(),
            expiresAt = payload.exp,
            issuedAt = payload.iat,
        )
    }.getOrNull()
}

private fun JsonElement?.toAudienceList(): List<String> = when (this) {
    null -> emptyList()
    is JsonArray -> mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    else -> listOfNotNull(jsonPrimitive.contentOrNull)
}

private val JsonPrimitive.contentOrNull: String?
    get() = if (isString) content else null
