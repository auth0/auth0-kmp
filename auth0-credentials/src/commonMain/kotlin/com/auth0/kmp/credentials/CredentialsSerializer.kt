package com.auth0.kmp.credentials

import com.auth0.kmp.core.model.Credentials
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

internal object CredentialsSerializer {

    private val json = Json { ignoreUnknownKeys = true }


    @Serializable
    private data class Dto(
        val accessToken: String,
        val idToken: String,
        val tokenType: String,
        val expiresAtEpochSeconds: Long,
        val refreshToken: String? = null,
        val scope: String? = null,
        val dpopThumbprint: String? = null,
    )


    fun encode(credentials: Credentials, dpopThumbprint: String? = null): String =
        json.encodeToString(
            Dto(
                accessToken = credentials.accessToken,
                idToken = credentials.idToken,
                tokenType = credentials.tokenType,
                expiresAtEpochSeconds = credentials.expiresAt.epochSeconds,
                refreshToken = credentials.refreshToken,
                scope = credentials.scope,
                dpopThumbprint = dpopThumbprint,
            ),
        )

    fun decode(value: String): StoredCredentials {
        val dto = json.decodeFromString<Dto>(value)
        return StoredCredentials(
            credentials = Credentials(
                accessToken = dto.accessToken,
                idToken = dto.idToken,
                tokenType = dto.tokenType,
                expiresAt = Instant.fromEpochSeconds(dto.expiresAtEpochSeconds),
                refreshToken = dto.refreshToken,
                scope = dto.scope,
            ),
            dpopThumbprint = dto.dpopThumbprint,
        )
    }
}

/**
 * A decoded credentials blob together with the DPoP key thumbprint it is bound to, if any.
 *
 * @param credentials the stored credentials.
 * @param dpopThumbprint the JWK thumbprint of the DPoP keypair the credentials are bound to,
 * or `null` when they are not DPoP-bound.
 */
internal data class StoredCredentials(
    val credentials: Credentials,
    val dpopThumbprint: String?,
)