package com.auth0.kmp.authentication.request

import com.auth0.kmp.core.token.TokenGrant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Token grant for the passwordless OTP verify step
 * (`http://auth0.com/oauth/grant-type/passwordless/otp`).
 *
 * Shared by both passwordless flows. The flow-specific identification is passed via
 * [extraParameters]: `username` + `realm` for the classic flow, or `auth_session` for the
 * DB-connection flow.
 */
internal class PasswordlessGrant(
    otp: String,
    clientId: String,
    scope: String,
    audience: String?,
    extraParameters: Map<String, String> = emptyMap(),
) : TokenGrant {
    override val parameters: JsonObject = buildJsonObject {
        extraParameters.forEach { (key, value) -> put(key, value) }
        put("grant_type", "http://auth0.com/oauth/grant-type/passwordless/otp")
        put("client_id", clientId)
        put("otp", otp)
        put("scope", scope)
        audience?.let { put("audience", it) }
    }
}
