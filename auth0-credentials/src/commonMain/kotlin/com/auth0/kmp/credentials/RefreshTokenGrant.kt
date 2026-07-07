package com.auth0.kmp.credentials

import com.auth0.kmp.core.token.TokenGrant

internal class RefreshTokenGrant(
    refreshToken: String,
    clientId: String,
    scope: String? = null,
    audience: String? = null,
    extraParams: Map<String, String> = emptyMap()
) : TokenGrant {

    override val parameters: Map<String, String> = buildMap {
        putAll(extraParams)
        put("grant_type", "refresh_token")
        put("client_id", clientId)
        put("refresh_token", refreshToken)
        scope?.let {
            put("scope", it)
        }
        audience?.let {
            put("audience", it)
        }
    }
}