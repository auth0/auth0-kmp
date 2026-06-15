package com.auth0.kmp.networking.transport

import kotlinx.serialization.json.Json

internal val json: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = false
}