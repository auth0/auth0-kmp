package com.auth0.kmp.core.validation

import com.auth0.kmp.core.annotation.InternalAuth0Api
import kotlin.time.Clock

@InternalAuth0Api
public class IdTokenClaimsValidator(
    private val issuer: String,
    private val audience: String,
    private val clock: Clock,
    private val leeway: Long = 60,
) : IdTokenValidator {

    override fun validate(
        idToken: String,
        context: IdTokenValidationContext,
    ): IdTokenValidationError? {

        val claims = decodeJwtClaims(idToken)
            ?: return IdTokenValidationError.CannotDecode

        if (claims.issuer == null || claims.issuer != issuer)
            return IdTokenValidationError.InvalidIssuer

        if (claims.subject.isNullOrEmpty())
            return IdTokenValidationError.MissingSubject

        if (audience !in claims.audience)
            return IdTokenValidationError.InvalidAudience

        val exp = claims.expiresAt ?: return IdTokenValidationError.Expired
        if (exp + leeway <= clock.now().epochSeconds)
            return IdTokenValidationError.Expired

        if (claims.issuedAt == null)
            return IdTokenValidationError.MissingIssuedAt


        if (context.nonce != null && context.nonce != claims.nonce)
            return IdTokenValidationError.InvalidNonce


        if (claims.audience.size > 1 && claims.authorizedParty != audience)
            return IdTokenValidationError.InvalidAuthorizedParty

        if (context.maxAge != null) {
            val authTime = claims.authTime ?: return IdTokenValidationError.AuthTimeExceeded
            if (clock.now().epochSeconds > authTime + context.maxAge + leeway)
                return IdTokenValidationError.AuthTimeExceeded
        }

        if (context.organization != null) {
            if (context.organization.startsWith("org_")) {
                if (claims.organizationId != context.organization)
                    return IdTokenValidationError.InvalidOrganization

            } else {
                if (claims.organizationName != context.organization.lowercase())
                    return IdTokenValidationError.InvalidOrganization
            }
        }

        return null
    }
}
