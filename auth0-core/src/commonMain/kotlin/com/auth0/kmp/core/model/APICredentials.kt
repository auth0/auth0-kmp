package com.auth0.kmp.core.model

import kotlin.time.Instant

/**
 * An access token scoped to a specific API (`audience`), obtained by exchanging
 * a refresh token.
 *
 * @param accessToken token for authenticated requests to the API (the `audience`).
 * @param tokenType how the access token should be used, e.g. `Bearer`.
 * @param expiresAt the instant the access token expires.
 * @param scope the scopes granted by Auth0, if any.
 */
public data class APICredentials(
    val accessToken: String,
    val tokenType: String,
    val expiresAt: Instant,
    val scope: String? = null,
) {
    override fun toString(): String {
        return "APICredentials(accessToken='xxxxx', type='$tokenType', expiresAt='$expiresAt', scope='$scope')"
    }
}
