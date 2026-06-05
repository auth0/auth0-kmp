package com.auth0.core.model

import kotlin.time.Instant

/**
 * Tokens obtained from Auth0 after a successful authentication.
 *
 * @param accessToken token for authenticated requests to the API (the `audience`).
 * @param idToken token containing the authenticated user's information.
 * @param tokenType how the access token should be used, e.g. `Bearer`.
 * @param expiresAt the instant the access token expires.
 * @param refreshToken token used to obtain new credentials; requires the
 *   `offline_access` scope. `null` when not granted.
 * @param scope the scopes granted by Auth0, if any.
 */
data class Credentials(
    val accessToken: String,
    val idToken: String,
    val tokenType: String,
    val expiresAt: Instant,
    val refreshToken: String? = null,
    val scope: String? = null,
) {
    override fun toString(): String {
        return "Credentials(idToken='xxxxx', accessToken='xxxxx', type='$tokenType', refreshToken='xxxxx', expiresAt='$expiresAt', scope='$scope')"
    }
}
