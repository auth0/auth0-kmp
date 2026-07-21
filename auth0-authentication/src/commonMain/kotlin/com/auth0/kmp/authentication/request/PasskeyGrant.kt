package com.auth0.kmp.authentication.request

import com.auth0.kmp.authentication.model.PublicKeyCredentials
import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.token.TokenGrant
import com.auth0.kmp.networking.transport.json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

@OptIn(InternalAuth0Api::class)
internal class PasskeyGrant(
    authSession: String,
    authResponse: PublicKeyCredentials,
    clientId: String,
    realm: String?,
    organization: String?,
    scope: String,
    audience: String?,
    extraParameters: Map<String, String> = emptyMap(),
) : TokenGrant {
    override val parameters: JsonObject = buildJsonObject {
        extraParameters.forEach { (key, value) -> put(key, value) }
        put("grant_type", "urn:okta:params:oauth:grant-type:webauthn")
        put("client_id", clientId)
        put("auth_session", authSession)
        put("authn_response", json.encodeToJsonElement(authResponse))
        realm?.let { put("realm", it) }
        organization?.let { put("organization", it) }
        put("scope", scope)
        audience?.let { put("audience", it) }
    }
}
