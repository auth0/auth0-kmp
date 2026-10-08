package com.auth0.kmp.authentication.passwordless.response

import com.auth0.kmp.authentication.passwordless.model.PasswordlessChallenge
import com.auth0.kmp.core.annotation.InternalAuth0Api
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire representation of the `POST /otp/challenge` response. Mapped to the
 * [PasswordlessChallenge] domain model before it is handed to consumers.
 */
@Serializable
@InternalAuth0Api
public data class PasswordlessChallengeResponse(
    @SerialName("auth_session") val authSession: String,
)

@InternalAuth0Api
public fun PasswordlessChallengeResponse.toPasswordlessChallenge(): PasswordlessChallenge =
    PasswordlessChallenge(authSession = authSession)
