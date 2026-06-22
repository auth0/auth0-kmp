package com.auth0.kmp.authentication.response

import com.auth0.kmp.core.model.Credentials
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@Serializable
internal data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("id_token") val idToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val scope: String? = null,
)

internal fun TokenResponse.toCredentials(clock: Clock): Credentials =
    Credentials(
        accessToken = accessToken,
        idToken = idToken,
        tokenType = tokenType,
        expiresAt = clock.now() + expiresIn.seconds,
        refreshToken = refreshToken,
        scope = scope,
    )
