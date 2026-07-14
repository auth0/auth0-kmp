package com.auth0.kmp.webauth.request

import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.token.TokenGrant

@OptIn(InternalAuth0Api::class)
internal class CodeExchangeGrant(
    code: String,
    codeVerifier: String,
    redirectUri: String,
    clientId: String,
) : TokenGrant {
    override val parameters: Map<String, String> = buildMap {
        put("grant_type", "authorization_code")
        put("client_id", clientId)
        put("code", code)
        put("code_verifier", codeVerifier)
        put("redirect_uri", redirectUri)
    }
}
