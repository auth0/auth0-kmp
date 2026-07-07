package com.auth0.kmp.core.validation

import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.primitives.decodeBase64Url
import com.auth0.kmp.core.validation.model.JwtClaims
import com.auth0.kmp.core.validation.model.JwtHeader
import com.auth0.kmp.networking.transport.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

@Serializable
private data class JwtPayload(
    val iss: String? = null,
    val sub: String? = null,
    val aud: JsonElement? = null,
    val exp: Long? = null,
    val iat: Long? = null,
    val nonce: String? = null,
    val azp: String? = null,
    val auth_time: Long? = null,
    val org_id: String? = null,
    val org_name: String? = null,
)

@Serializable
private data class JwtHeaderPayload(
    val alg: String? = null,
    val kid: String? = null,
)

@InternalAuth0Api
public fun decodeJwtClaims(idToken: String): JwtClaims? {
    val segments = idToken.split(".")
    if (segments.size != 3) return null
    return runCatching {
        val bytes = segments[1].decodeBase64Url()
        val payload = json.decodeFromString<JwtPayload>(bytes.decodeToString())
        JwtClaims(
            issuer = payload.iss,
            subject = payload.sub,
            audience = payload.aud.toAudienceList(),
            expiresAt = payload.exp,
            issuedAt = payload.iat,
            nonce = payload.nonce,
            authorizedParty = payload.azp,
            authTime = payload.auth_time,
            organizationId = payload.org_id,
            organizationName = payload.org_name,
        )
    }.getOrNull()
}

@InternalAuth0Api
public fun decodeJwtHeader(idToken: String): JwtHeader? {
    val segments = idToken.split(".")
    if (segments.size != 3) return null
    return runCatching {
        val bytes = segments[0].decodeBase64Url()
        val header = json.decodeFromString<JwtHeaderPayload>(bytes.decodeToString())
        JwtHeader(
            algorithm = header.alg,
            keyId = header.kid,
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
