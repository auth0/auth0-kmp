package com.auth0.kmp.core.primitives

import com.auth0.kmp.core.annotation.InternalAuth0Api

/**
 * Computes the SHA-256 digest of these bytes.
 *
 * @return a 32-byte array containing the digest.
 */
@InternalAuth0Api
public expect fun ByteArray.sha256(): ByteArray

/**
 * Generates [size] cryptographically secure random bytes.
 *
 * @param size the number of random bytes to produce.
 * @return a new array of [size] securely generated random bytes.
 */
@InternalAuth0Api
public expect fun generateSecureRandomBytes(size: Int): ByteArray

/**
 * Verifies an RSA SHA-256 (RS256) signature against an RSA public key.
 *
 * @param signedData the bytes that were signed (the JWS signing input, i.e.
 *   ASCII of `header.payload`).
 * @param signature the signature bytes to verify.
 * @param modulus the RSA modulus (`n`) as raw big-endian bytes.
 * @param exponent the RSA public exponent (`e`) as raw big-endian bytes.
 * @return `true` if the signature is valid for [signedData] under the key,
 *   `false` if it is invalid or the key material is malformed.
 */
@InternalAuth0Api
public expect fun verifyRs256(
    signedData: ByteArray,
    signature: ByteArray,
    modulus: ByteArray,
    exponent: ByteArray,
): Boolean