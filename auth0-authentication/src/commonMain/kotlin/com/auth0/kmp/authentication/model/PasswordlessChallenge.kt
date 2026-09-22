package com.auth0.kmp.authentication.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The challenge returned by a DB-connection passwordless challenge request
 * (`POST /otp/challenge`).
 *
 * Pass this to [com.auth0.kmp.authentication.passwordless.PasswordlessClient.loginWithOTP]
 * together with the one-time code the user received to complete the flow.
 */
@Serializable
public class PasswordlessChallenge(
    /**
     * Opaque token that links this challenge to the subsequent [loginWithOTP] call.
     * Sent back as the `auth_session` request parameter.
     */
    @SerialName("auth_session")
    public val authSession: String,
)
