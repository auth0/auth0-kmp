package com.auth0.kmp.credentials

import com.auth0.kmp.core.model.ApiCredentials
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

internal object ApiCredentialsSerializer {

    private val json = Json { ignoreUnknownKeys = true }


    @Serializable
    private data class Dto(
        val accessToken: String,
        val tokenType: String,
        val expiresAtEpochSeconds: Long,
        val scope: String? = null,
    )


    fun encode(entries: Map<String, ApiCredentials>): String =
        json.encodeToString(
            entries.mapValues { (_, credentials) ->
                Dto(
                    accessToken = credentials.accessToken,
                    tokenType = credentials.tokenType,
                    expiresAtEpochSeconds = credentials.expiresAt.epochSeconds,
                    scope = credentials.scope,
                )
            },
        )

    fun decode(value: String): Map<String, ApiCredentials> {
        val dtos = json.decodeFromString<Map<String, Dto>>(value)
        return dtos.mapValues { (_, dto) ->
            ApiCredentials(
                accessToken = dto.accessToken,
                tokenType = dto.tokenType,
                expiresAt = Instant.fromEpochSeconds(dto.expiresAtEpochSeconds),
                scope = dto.scope,
            )
        }
    }
}
