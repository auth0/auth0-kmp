package com.auth0.kmp.myaccount.model

/**
 * A passkey enrollment challenge, combining the authentication method ID from
 * the response headers with the challenge details from the response body.
 *
 * @param authenticationMethodId the identifier of the pending authentication
 *   method, echoed back when verifying the enrollment.
 * @param authSession the opaque session token echoed back when verifying the enrollment.
 * @param authParamsPublicKey the WebAuthn public-key options the authenticator
 *   needs to create a credential.
 */
public data class PasskeyEnrollmentChallenge(
    val authenticationMethodId: String,
    val authSession: String,
    val authParamsPublicKey: AuthnParamsPublicKey,
)
