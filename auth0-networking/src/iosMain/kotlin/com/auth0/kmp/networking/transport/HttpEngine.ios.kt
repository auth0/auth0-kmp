package com.auth0.kmp.networking.transport

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

internal actual fun httpEngineFactory(): HttpClientEngineFactory<*> = Darwin
