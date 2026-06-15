package com.auth0.kmp.networking.request

import com.auth0.kmp.core.annotation.InternalAuth0Api


/**
 * A transport-level description of a single HTTP request.
 *
 * @param method the HTTP method to use.
 * @param path the request path relative to the Auth0 domain, e.g. `/oauth/token`.
 * @param headers headers to send with the request.
 * @param query query-string parameters, added to the URL.
 * @param body the request body, already serialized, or `null` for no-body.
 */
@InternalAuth0Api
public data class NetworkRequest(
    val method: HttpMethod,
    val path: String,
    val headers: Map<String, String> = emptyMap(),
    val query: Map<String, String> = emptyMap(),
    val body: String? = null
)