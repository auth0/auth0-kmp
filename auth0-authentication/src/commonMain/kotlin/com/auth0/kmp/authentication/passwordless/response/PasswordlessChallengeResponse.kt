package com.auth0.kmp.authentication.passwordless.response

import com.auth0.kmp.authentication.passwordless.model.PasswordlessChallenge
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire representation of the `POST /otp/challenge` response. Mapped to the
 * [PasswordlessChallenge] domain model before it is handed to consumers.
 */
@Serializable
internal data class PasswordlessChallengeResponse(
    @SerialName("auth_session") val authSession: String,
)

internal fun PasswordlessChallengeResponse.toPasswordlessChallenge(): PasswordlessChallenge =
    PasswordlessChallenge(authSession = authSession)
