package com.auth0.kmp.core.token

import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.model.Credentials
import com.auth0.kmp.core.model.SsoCredentials
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@Serializable
@InternalAuth0Api
public data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("id_token") val idToken: String? = null,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val scope: String? = null,
    @SerialName("issued_token_type") val issuedTokenType: String? = null,
)

@InternalAuth0Api
public fun TokenResponse.toCredentials(clock: Clock): Credentials =
    Credentials(
        accessToken = accessToken,
        idToken = idToken.orEmpty(),
        tokenType = tokenType,
        expiresAt = clock.now() + expiresIn.seconds,
        refreshToken = refreshToken,
        scope = scope,
    )

@InternalAuth0Api
public fun TokenResponse.toSsoCredentials(clock: Clock): SsoCredentials =
    SsoCredentials(
        sessionTransferToken = accessToken,
        issuedTokenType = issuedTokenType
            ?: throw SerializationException("Session-transfer exchange response is missing issued_token_type"),
        expiresAt = clock.now() + expiresIn.seconds,
        idToken = idToken
            ?: throw SerializationException("Session-transfer exchange response is missing id_token"),
        refreshToken = refreshToken,
    )
