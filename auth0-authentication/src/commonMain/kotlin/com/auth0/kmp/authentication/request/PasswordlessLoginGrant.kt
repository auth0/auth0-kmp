package com.auth0.kmp.authentication.request

import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.token.TokenGrant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(InternalAuth0Api::class)
internal class PasswordlessLoginGrant(
    username: String,
    otp: String,
    realm: String,
    clientId: String,
    scope: String,
    audience: String?,
    extraParameters: Map<String, String> = emptyMap(),
) : TokenGrant {
    override val parameters: JsonObject = buildJsonObject {
        extraParameters.forEach { (key, value) -> put(key, value) }
        put("grant_type", "http://auth0.com/oauth/grant-type/passwordless/otp")
        put("client_id", clientId)
        put("username", username)
        put("otp", otp)
        put("realm", realm)
        put("scope", scope)
        audience?.let { put("audience", it) }
    }
}
