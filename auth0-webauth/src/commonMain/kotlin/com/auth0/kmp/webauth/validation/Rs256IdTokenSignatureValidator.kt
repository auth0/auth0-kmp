package com.auth0.kmp.webauth.validation

import com.auth0.kmp.core.primitives.decodeBase64Url
import com.auth0.kmp.core.primitives.verifyRs256
import com.auth0.kmp.core.validation.IdTokenValidationError
import com.auth0.kmp.core.validation.decodeJwtHeader
import com.auth0.kmp.webauth.jwks.JwksProvider

private const val RS256 = "RS256"

internal class Rs256IdTokenSignatureValidator(
    private val jwksProvider: JwksProvider,
) : IdTokenSignatureValidator {

    override suspend fun verify(idToken: String): IdTokenValidationError? {
        val header = decodeJwtHeader(idToken)
            ?: return IdTokenValidationError.CannotDecode
        if (header.algorithm != RS256) {
            return IdTokenValidationError.UnsupportedAlgorithm
        }
        val kid = header.keyId
            ?: return IdTokenValidationError.PublicKeyNotFound
        val jwk = jwksProvider.fetch(kid)
            ?: return IdTokenValidationError.PublicKeyNotFound

        val segments = idToken.split(".")
        return runCatching {
            val signedData = "${segments[0]}.${segments[1]}".encodeToByteArray()
            val signature = segments[2].decodeBase64Url()
            val modulus = jwk.modulus.decodeBase64Url()
            val exponent = jwk.exponent.decodeBase64Url()
            if (verifyRs256(signedData, signature, modulus, exponent)) {
                null
            } else {
                IdTokenValidationError.InvalidSignature
            }
        }.getOrElse { IdTokenValidationError.InvalidSignature }
    }
}
