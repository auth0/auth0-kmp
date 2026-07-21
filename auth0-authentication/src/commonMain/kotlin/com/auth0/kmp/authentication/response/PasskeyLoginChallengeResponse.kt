package com.auth0.kmp.authentication.response

import com.auth0.kmp.authentication.model.AuthParamsPublicKey
import com.auth0.kmp.authentication.model.PasskeyLoginChallenge
import com.auth0.kmp.core.annotation.InternalAuth0Api
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@InternalAuth0Api
public data class PasskeyLoginChallengeResponse(
    @SerialName("auth_session") val authSession: String,
    @SerialName("authn_params_public_key") val authParamsPublicKey: AuthParamsPublicKeyResponse,
)

@Serializable
@InternalAuth0Api
public data class AuthParamsPublicKeyResponse(
    @SerialName("challenge") val challenge: String,
    @SerialName("rpId") val rpId: String,
    @SerialName("timeout") val timeout: Long,
    @SerialName("userVerification") val userVerification: String,
)

@InternalAuth0Api
public fun PasskeyLoginChallengeResponse.toPasskeyLoginChallenge(): PasskeyLoginChallenge =
    PasskeyLoginChallenge(
        authSession = authSession,
        authParamsPublicKey = authParamsPublicKey.toAuthParamsPublicKey(),
    )

@InternalAuth0Api
public fun AuthParamsPublicKeyResponse.toAuthParamsPublicKey(): AuthParamsPublicKey =
    AuthParamsPublicKey(
        challenge = challenge,
        rpId = rpId,
        timeout = timeout,
        userVerification = userVerification,
    )
