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
    )


    fun encode(credentials: Credentials): String =
        json.encodeToString(
            Dto(
                accessToken = credentials.accessToken,
                idToken = credentials.idToken,
                tokenType = credentials.tokenType,
                expiresAtEpochSeconds = credentials.expiresAt.epochSeconds,
                refreshToken = credentials.refreshToken,
                scope = credentials.scope,
            ),
        )

    fun decode(value: String): Credentials {
        val dto = json.decodeFromString<Dto>(value)
        return Credentials(
            accessToken = dto.accessToken,
            idToken = dto.idToken,
            tokenType = dto.tokenType,
            expiresAt = Instant.fromEpochSeconds(dto.expiresAtEpochSeconds),
            refreshToken = dto.refreshToken,
            scope = dto.scope,
        )
    }
}