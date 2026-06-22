package com.auth0.kmp.networking.transport

import com.auth0.kmp.core.annotation.InternalAuth0Api
import kotlinx.serialization.json.Json

@InternalAuth0Api
public val json: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = false
}