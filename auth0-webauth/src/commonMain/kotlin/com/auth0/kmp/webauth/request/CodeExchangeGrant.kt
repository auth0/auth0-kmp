package com.auth0.kmp.webauth.request

import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.token.TokenGrant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(InternalAuth0Api::class)
internal class CodeExchangeGrant(
    code: String,
    codeVerifier: String,
    redirectUri: String,
    clientId: String,
) : TokenGrant {
    override val parameters: JsonObject = buildJsonObject {
        put("grant_type", "authorization_code")
        put("client_id", clientId)
        put("code", code)
        put("code_verifier", codeVerifier)
        put("redirect_uri", redirectUri)
    }
}
