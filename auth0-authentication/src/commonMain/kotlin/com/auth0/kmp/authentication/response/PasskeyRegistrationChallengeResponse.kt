package com.auth0.kmp.authentication.response

import com.auth0.kmp.authentication.model.AuthenticatorSelection
import com.auth0.kmp.authentication.model.AuthnParamsPublicKey
import com.auth0.kmp.authentication.model.PasskeyRegistrationChallenge
import com.auth0.kmp.authentication.model.PasskeyUser
import com.auth0.kmp.authentication.model.PubKeyCredParam
import com.auth0.kmp.authentication.model.RelyingParty
import com.auth0.kmp.core.annotation.InternalAuth0Api
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@InternalAuth0Api
public data class PasskeyRegistrationChallengeResponse(
    @SerialName("auth_session") val authSession: String,
    @SerialName("authn_params_public_key") val authParamsPublicKey: AuthnParamsPublicKeyResponse,
)

@Serializable
@InternalAuth0Api
public data class AuthnParamsPublicKeyResponse(
    @SerialName("authenticatorSelection") val authenticatorSelection: AuthenticatorSelectionResponse,
    @SerialName("challenge") val challenge: String,
    @SerialName("pubKeyCredParams") val pubKeyCredParams: List<PubKeyCredParamResponse>,
    @SerialName("rp") val relyingParty: RelyingPartyResponse,
    @SerialName("timeout") val timeout: Long,
    @SerialName("user") val user: PasskeyUserResponse,
)

@Serializable
@InternalAuth0Api
public data class AuthenticatorSelectionResponse(
    @SerialName("residentKey") val residentKey: String,
    @SerialName("userVerification") val userVerification: String,
)

@Serializable
@InternalAuth0Api
public data class PubKeyCredParamResponse(
    @SerialName("alg") val alg: Int,
    @SerialName("type") val type: String,
)

@Serializable
@InternalAuth0Api
public data class RelyingPartyResponse(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
)

@Serializable
@InternalAuth0Api
public data class PasskeyUserResponse(
    @SerialName("displayName") val displayName: String,
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
)

@InternalAuth0Api
public fun PasskeyRegistrationChallengeResponse.toPasskeyRegistrationChallenge(): PasskeyRegistrationChallenge =
    PasskeyRegistrationChallenge(
        authSession = authSession,
        authParamsPublicKey = authParamsPublicKey.toAuthnParamsPublicKey(),
    )

@InternalAuth0Api
public fun AuthnParamsPublicKeyResponse.toAuthnParamsPublicKey(): AuthnParamsPublicKey =
    AuthnParamsPublicKey(
        authenticatorSelection = authenticatorSelection.toAuthenticatorSelection(),
        challenge = challenge,
        pubKeyCredParams = pubKeyCredParams.map { it.toPubKeyCredParam() },
        relyingParty = relyingParty.toRelyingParty(),
        timeout = timeout,
        user = user.toPasskeyUser(),
    )

@InternalAuth0Api
public fun AuthenticatorSelectionResponse.toAuthenticatorSelection(): AuthenticatorSelection =
    AuthenticatorSelection(
        residentKey = residentKey,
        userVerification = userVerification,
    )

@InternalAuth0Api
public fun PubKeyCredParamResponse.toPubKeyCredParam(): PubKeyCredParam =
    PubKeyCredParam(
        alg = alg,
        type = type,
    )

@InternalAuth0Api
public fun RelyingPartyResponse.toRelyingParty(): RelyingParty =
    RelyingParty(
        id = id,
        name = name,
    )

@InternalAuth0Api
public fun PasskeyUserResponse.toPasskeyUser(): PasskeyUser =
    PasskeyUser(
        displayName = displayName,
        id = id,
        name = name,
    )
