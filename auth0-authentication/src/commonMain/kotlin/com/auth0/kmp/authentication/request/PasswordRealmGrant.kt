package com.auth0.kmp.authentication.request

import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.token.TokenGrant

@OptIn(InternalAuth0Api::class)
internal class PasswordRealmGrant(
    usernameOrEmail: String,
    password: String,
    realm: String,
    clientId: String,
    scope: String,
    audience: String?,
    extraParameters: Map<String, String> = emptyMap(),
) : TokenGrant {
    override val parameters: Map<String, String> = buildMap {
        putAll(extraParameters)
        put("grant_type", "http://auth0.com/oauth/grant-type/password-realm")
        put("client_id", clientId)
        put("username", usernameOrEmail)
        put("password", password)
        put("realm", realm)
        put("scope", scope)
        audience?.let { put("audience", it) }
    }
}
