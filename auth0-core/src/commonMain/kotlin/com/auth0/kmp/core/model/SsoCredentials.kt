package com.auth0.kmp.core.model

import kotlin.time.Instant

/**
 * A single-use session-transfer token, obtained by exchanging a refresh token, that lets a native
 * session be continued in a web context.
 *
 * @param sessionTransferToken the token used to transfer the session; consumed immediately and never stored.
 * @param issuedTokenType the type of token that was issued.
 * @param expiresAt the instant the session-transfer token expires.
 * @param idToken the ID token for the session.
 * @param refreshToken the rotated refresh token, if the tenant rotated it during the exchange.
 */
public data class SsoCredentials(
    val sessionTransferToken: String,
    val issuedTokenType: String,
    val expiresAt: Instant,
    val idToken: String,
    val refreshToken: String? = null,
) {
    override fun toString(): String {
        return "SsoCredentials(sessionTransferToken='xxxxx', issuedTokenType='$issuedTokenType', " +
            "expiresAt='$expiresAt', idToken='xxxxx', refreshToken='${if (refreshToken != null) "xxxxx" else "null"}')"
    }
}
