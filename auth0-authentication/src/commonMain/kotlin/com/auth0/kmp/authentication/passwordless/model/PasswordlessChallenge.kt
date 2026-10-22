package com.auth0.kmp.authentication.passwordless.model

/**
 * The challenge returned by a DB-connection passwordless challenge request
 * (`POST /otp/challenge`).
 *
 * Pass this to [com.auth0.kmp.authentication.passwordless.PasswordlessClient.loginWithOTP]
 * together with the one-time code the user received to complete the flow.
 *
 * @param authSession opaque token that links this challenge to the subsequent
 *   [com.auth0.kmp.authentication.passwordless.PasswordlessClient.loginWithOTP] call.
 */
public data class PasswordlessChallenge(
    val authSession: String,
)
