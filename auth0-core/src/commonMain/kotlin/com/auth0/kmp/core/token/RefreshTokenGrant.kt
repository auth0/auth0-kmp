package com.auth0.kmp.core.token

import com.auth0.kmp.core.annotation.InternalAuth0Api
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@InternalAuth0Api
public class RefreshTokenGrant(
    refreshToken: String,
    clientId: String,
    scope: String? = null,
    audience: String? = null,
    extraParameters: Map<String, String> = emptyMap(),
) : TokenGrant {
    override val parameters: JsonObject = buildJsonObject {
        extraParameters.forEach { (key, value) -> put(key, value) }
        put("grant_type", "refresh_token")
        put("client_id", clientId)
        put("refresh_token", refreshToken)
        scope?.let { put("scope", it) }
        audience?.let { put("audience", it) }
    }
}
