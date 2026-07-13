package com.auth0.kmp.networking.transport

import com.auth0.kmp.core.NetworkingConfiguration
import com.auth0.kmp.core.dpop.DPoPCollaborators
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.appendIfNameAbsent

internal fun buildHttpClient(
    config: NetworkingConfiguration,
    engineFactory: HttpClientEngineFactory<*> = httpEngineFactory(),
    dpopCollaborators: DPoPCollaborators? = null,
): HttpClient =
    HttpClient(engineFactory) { applyNetworkingConfig(config, dpopCollaborators) }

internal fun HttpClientConfig<*>.applyNetworkingConfig(
    config: NetworkingConfiguration,
    dpopCollaborators: DPoPCollaborators? = null,
) {
    expectSuccess = false

    install(HttpTimeout) {
        connectTimeoutMillis = config.connectTimeoutMillis
        requestTimeoutMillis = config.requestTimeoutMillis
    }

    install(ContentNegotiation) {
        json(json)
    }

    if (config.enableLogging) {
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.INFO
        }
    }

    defaultRequest {
        config.defaultHeaders.forEach { (key, value) ->
            headers.appendIfNameAbsent(key, value)
        }
    }

    dpopCollaborators?.let { collaborators ->
        install(DPoPPlugin) {
            proofGenerator = collaborators.proofGenerator
            nonceStore = collaborators.nonceStore
            keygenLock = collaborators.keygenLock
        }
    }
}