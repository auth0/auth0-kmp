package com.auth0.kmp.authentication.model

/**
 * A challenge issued by Auth0 for authenticating an existing user with a passkey.
 *
 * @param authSession the opaque session token echoed back when completing the login.
 * @param authParamsPublicKey the WebAuthn public-key options the authenticator
 *   needs to produce an assertion.
 */
public data class PasskeyLoginChallenge(
    val authSession: String,
    val authParamsPublicKey: AuthParamsPublicKey,
)

/**
 * The WebAuthn public-key request options carried by a [PasskeyLoginChallenge].
 *
 * @param challenge the base64url-encoded challenge the authenticator must sign.
 * @param rpId the relying-party identifier the credential is scoped to.
 * @param timeout the time, in milliseconds, the caller is given to respond.
 * @param userVerification the user-verification requirement, e.g. `required`.
 */
public data class AuthParamsPublicKey(
    val challenge: String,
    val rpId: String,
    val timeout: Long,
    val userVerification: String,
)
