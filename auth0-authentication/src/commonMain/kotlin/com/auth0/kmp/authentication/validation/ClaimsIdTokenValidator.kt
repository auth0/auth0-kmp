package com.auth0.kmp.authentication.validation

import kotlin.time.Clock

internal class ClaimsIdTokenValidator(
    private val issuer: String,
    private val audience: String,
    private val clock: Clock,
    private val leeway: Long = 60,
) : IdTokenValidator {

    override fun validate(idToken: String): IdTokenValidationError? {
        // Claims-only validation. The signature is intentionally not verified
        // here: the token is received directly from Auth0 over TLS. Signature
        // /JWKS verification is introduced with the WebAuth flow.
        val claims = decodeJwtClaims(idToken)
            ?: return IdTokenValidationError.CannotDecode

        if (claims.issuer == null || claims.issuer != issuer) {
            return IdTokenValidationError.InvalidIssuer
        }
        if (claims.subject.isNullOrEmpty()) {
            return IdTokenValidationError.MissingSubject
        }
        if (audience !in claims.audience) {
            return IdTokenValidationError.InvalidAudience
        }
        val exp = claims.expiresAt ?: return IdTokenValidationError.Expired
        if (exp + leeway <= clock.now().epochSeconds) {
            return IdTokenValidationError.Expired
        }
        if (claims.issuedAt == null) {
            return IdTokenValidationError.MissingIssuedAt
        }

        return null
    }
}
