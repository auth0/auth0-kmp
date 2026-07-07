package com.auth0.kmp.webauth.jwks

/**
 * An RSA public key from a tenant's JWKS, reduced to the fields needed to
 * verify an RS256 signature.
 *
 * @param kid the key identifier, matched against the JWT header `kid`.
 * @param modulus the RSA modulus (`n`), base64url-encoded.
 * @param exponent the RSA public exponent (`e`), base64url-encoded.
 */
internal data class Jwk(
    val kid: String,
    val modulus: String,
    val exponent: String,
)
