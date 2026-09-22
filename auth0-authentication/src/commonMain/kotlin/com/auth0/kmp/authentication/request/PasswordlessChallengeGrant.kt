package com.auth0.kmp.authentication.request

import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.token.TokenGrant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Token grant for the DB-connection passwordless OTP verify step.
 *
 * Uses the same `http://auth0.com/oauth/grant-type/passwordless/otp` grant type as the
 * classic flow, but identifies the session via the opaque [authSession] returned by
 * `POST /otp/challenge` instead of a username + realm pair.
 */
@OptIn(InternalAuth0Api::class)
internal class PasswordlessChallengeGrant(
    authSession: String,
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
        put("auth_session", authSession)
        put("otp", otp)
        put("scope", scope)
        audience?.let { put("audience", it) }
    }
}
