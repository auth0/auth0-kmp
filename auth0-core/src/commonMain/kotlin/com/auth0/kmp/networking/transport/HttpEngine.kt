package com.auth0.kmp.networking.transport

import io.ktor.client.engine.HttpClientEngineFactory

internal expect fun httpEngineFactory(): HttpClientEngineFactory<*>
