package com.auth0.kmp.authentication.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A challenge issued by Auth0 for authenticating an existing user with a passkey.
 *
 * @param authSession the opaque session token echoed back when completing the login.
 * @param authParamsPublicKey the WebAuthn public-key options the authenticator
 *   needs to produce an assertion.
 */
@Serializable
public data class PasskeyLoginChallenge(
    @SerialName("auth_session") val authSession: String,
    @SerialName("authn_params_public_key") val authParamsPublicKey: AuthParamsPublicKey,
)

/**
 * The WebAuthn public-key request options carried by a [PasskeyLoginChallenge].
 *
 * @param challenge the base64url-encoded challenge the authenticator must sign.
 * @param rpId the relying-party identifier the credential is scoped to.
 * @param timeout the time, in milliseconds, the caller is given to respond.
 * @param userVerification the user-verification requirement, e.g. `required`.
 */
@Serializable
public data class AuthParamsPublicKey(
    @SerialName("challenge") val challenge: String,
    @SerialName("rpId") val rpId: String,
    @SerialName("timeout") val timeout: Long,
    @SerialName("userVerification") val userVerification: String,
)
