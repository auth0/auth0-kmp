package com.auth0.kmp.webauth.authorize

import io.ktor.http.ParametersBuilder

/**
 * Appends caller-supplied extra query parameters, skipping any key the SDK
 * manages so extras can never override an SDK-set protocol parameter.
 */
internal fun ParametersBuilder.appendExtraParameters(
    extraParameters: Map<String, String>,
    reserved: Set<String>,
) {
    extraParameters.forEach { (key, value) ->
        if (key !in reserved) append(key, value)
    }
}
