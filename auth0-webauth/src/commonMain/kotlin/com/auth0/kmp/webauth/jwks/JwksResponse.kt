package com.auth0.kmp.webauth.jwks

import kotlinx.serialization.Serializable

@Serializable
internal data class JwksResponse(
    val keys: List<JwkKey> = emptyList(),
)

@Serializable
internal data class JwkKey(
    val kid: String? = null,
    val n: String? = null,
    val e: String? = null,
)

// Maps a JWKS entry to a [Jwk], or null if it lacks the kid/modulus/exponent an
// RS256 verification needs (e.g. a non-RSA key in a mixed key set).
internal fun JwkKey.toJwkOrNull(): Jwk? =
    if (kid != null && n != null && e != null) {
        Jwk(kid = kid, modulus = n, exponent = e)
    } else {
        null
    }
