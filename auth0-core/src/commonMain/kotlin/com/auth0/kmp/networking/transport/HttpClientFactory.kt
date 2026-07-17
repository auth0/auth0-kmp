package com.auth0.kmp.networking.transport

import com.auth0.kmp.core.NetworkLogLevel
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
import io.ktor.client.plugins.logging.LoggingFormat
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.appendIfNameAbsent

private fun NetworkLogLevel.toKtor(): LogLevel = when (this) {
    NetworkLogLevel.NONE -> LogLevel.NONE
    NetworkLogLevel.BASIC -> LogLevel.INFO
    NetworkLogLevel.HEADERS -> LogLevel.HEADERS
    NetworkLogLevel.BODY -> LogLevel.ALL
}

internal fun buildHttpClient(
    config: NetworkingConfiguration,
    engineFactory: HttpClientEngineFactory<*> = httpEngineFactory(),
    dpopCollaborators: DPoPCollaborators? = null,
    logger: Logger = Logger.SIMPLE,
): HttpClient =
    HttpClient(engineFactory) { applyNetworkingConfig(config, dpopCollaborators, logger) }

internal fun HttpClientConfig<*>.applyNetworkingConfig(
    config: NetworkingConfiguration,
    dpopCollaborators: DPoPCollaborators? = null,
    logger: Logger = Logger.SIMPLE,
) {
    expectSuccess = false

    install(HttpTimeout) {
        connectTimeoutMillis = config.connectTimeoutMillis
        requestTimeoutMillis = config.requestTimeoutMillis
    }

    install(ContentNegotiation) {
        json(json)
    }

    if (config.logLevel != NetworkLogLevel.NONE) {
        install(Logging) {
            this.logger = logger
            format = LoggingFormat.OkHttp
            level = config.logLevel.toKtor()
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
