package com.auth0.kmp.core.dpop

import com.auth0.kmp.core.primitives.encodeBase64Url
import com.auth0.kmp.core.primitives.sha256

/**
 * The public half of a DPoP keypair, as a JSON Web Key.
 *
 * Only the EC P-256 curve is supported, matching the `ES256` proof algorithm.
 *
 * @param x the base64url-encoded affine X coordinate (32 bytes).
 * @param y the base64url-encoded affine Y coordinate (32 bytes).
 * @param kty the key type; always `EC`.
 * @param crv the curve; always `P-256`.
 */
public data class DPoPJwk(
    val x: String,
    val y: String,
    val kty: String = "EC",
    val crv: String = "P-256",
) {

    /**
     * The RFC 7638 JWK thumbprint, base64url-encoded.
     *
     * The hash is taken over the canonical member ordering (`crv`, `kty`, `x`, `y`)
     * with no whitespace, independent of any JSON serializer's field order.
     */
    public fun thumbprint(): String {
        val canonical = "{\"crv\":\"$crv\",\"kty\":\"$kty\",\"x\":\"$x\",\"y\":\"$y\"}"
        return canonical.encodeToByteArray().sha256().encodeBase64Url()
    }
}
