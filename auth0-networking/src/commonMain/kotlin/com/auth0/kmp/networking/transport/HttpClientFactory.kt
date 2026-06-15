package com.auth0.kmp.networking.transport

import com.auth0.kmp.networking.NetworkingConfiguration
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.appendIfNameAbsent

internal fun buildHttpClient(config: NetworkingConfiguration): HttpClient =
    HttpClient(httpEngineFactory()) { applyNetworkingConfig(config) }

internal fun HttpClientConfig<*>.applyNetworkingConfig(config: NetworkingConfiguration) {
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
            level = LogLevel.ALL
        }
    }

    defaultRequest {
        config.defaultHeaders.forEach { (key, value) ->
            headers.appendIfNameAbsent(key, value)
        }
    }
}