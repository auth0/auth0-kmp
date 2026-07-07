package com.auth0.kmp.webauth.jwks

/**
 * Supplies the RSA public key from a tenant's JWKS for a given `kid`.
 */
internal interface JwksProvider {

    /**
     * Returns the key whose `kid` equals [kid], or `null` if the JWKS contains
     * no such key.
     *
     * @param kid the key identifier from the JWT header.
     */
    suspend fun fetch(kid: String): Jwk?
}
