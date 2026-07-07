package com.auth0.kmp.core.validation

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

private const val ISSUER = "https://t.auth0.com/"
private const val AUDIENCE = "client-123"
private const val NOW = 1_000L

private val unpadded = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

private fun jwt(payloadJson: String): String {
    val header = unpadded.encode("""{"alg":"none"}""".encodeToByteArray())
    val payload = unpadded.encode(payloadJson.encodeToByteArray())
    return "$header.$payload.sig"
}

private class FixedClock(private val at: Instant) : Clock {
    override fun now(): Instant = at
}

private fun validator(leeway: Long = 60): IdTokenClaimsValidator =
    IdTokenClaimsValidator(
        issuer = ISSUER,
        audience = AUDIENCE,
        clock = FixedClock(Instant.fromEpochSeconds(NOW)),
        leeway = leeway,
    )

/** A token that passes every check; individual tests override one claim. */
private fun validToken(
    iss: String = ISSUER,
    sub: String = "user-1",
    aud: String = "\"$AUDIENCE\"",
    exp: Long = 5_000,
    iat: Long = 900,
): String = jwt("""{"iss":"$iss","sub":"$sub","aud":$aud,"exp":$exp,"iat":$iat}""")

/**
 * A token whose mandatory claims always pass; the gated claims (nonce, azp,
 * auth_time, org_id, org_name) are emitted only when supplied (null = omitted),
 * so each gated-check test sets just the claim it exercises.
 */
private fun gatedToken(
    aud: String = "\"$AUDIENCE\"",
    nonce: String? = null,
    azp: String? = null,
    authTime: Long? = null,
    orgId: String? = null,
    orgName: String? = null,
): String {
    val fields = buildList {
        add("\"iss\":\"$ISSUER\"")
        add("\"sub\":\"user-1\"")
        add("\"aud\":$aud")
        add("\"exp\":5000")
        add("\"iat\":900")
        nonce?.let { add("\"nonce\":\"$it\"") }
        azp?.let { add("\"azp\":\"$it\"") }
        authTime?.let { add("\"auth_time\":$it") }
        orgId?.let { add("\"org_id\":\"$it\"") }
        orgName?.let { add("\"org_name\":\"$it\"") }
    }
    return jwt("{${fields.joinToString(",")}}")
}

class IdTokenClaimsValidatorTest {

    @Test
    fun validToken_returnsNull() {
        assertNull(validator().validate(validToken()))
    }

    @Test
    fun cannotDecode_returnsCannotDecode() {
        assertEquals(IdTokenValidationError.CannotDecode, validator().validate("a.b"))
    }

    @Test
    fun corruptExpType_returnsCannotDecode() {
        val token = jwt("""{"iss":"$ISSUER","sub":"user","aud":"$AUDIENCE","exp":"not-a-number","iat":900}""")

        assertEquals(IdTokenValidationError.CannotDecode, validator().validate(token))
    }

    @Test
    fun audAsObject_returnsCannotDecode() {
        val token = jwt("""{"iss":"$ISSUER","sub":"user","aud":{"x":"y"},"exp":5000,"iat":900}""")

        assertEquals(IdTokenValidationError.CannotDecode, validator().validate(token))
    }

    @Test
    fun issuerMissing_returnsInvalidIssuer() {
        val token = jwt("""{"sub":"user","aud":"$AUDIENCE","exp":5000,"iat":900}""")

        assertEquals(IdTokenValidationError.InvalidIssuer, validator().validate(token))
    }

    @Test
    fun issuerMismatch_returnsInvalidIssuer() {
        assertEquals(
            IdTokenValidationError.InvalidIssuer,
            validator().validate(validToken(iss = "https://evil.example.com/")),
        )
    }

    @Test
    fun subjectMissing_returnsMissingSubject() {
        val token = jwt("""{"iss":"$ISSUER","aud":"$AUDIENCE","exp":5000,"iat":900}""")

        assertEquals(IdTokenValidationError.MissingSubject, validator().validate(token))
    }

    @Test
    fun subjectEmpty_returnsMissingSubject() {
        assertEquals(
            IdTokenValidationError.MissingSubject,
            validator().validate(validToken(sub = "")),
        )
    }

    @Test
    fun audienceMissing_returnsInvalidAudience() {
        val token = jwt("""{"iss":"$ISSUER","sub":"user","exp":5000,"iat":900}""")

        assertEquals(IdTokenValidationError.InvalidAudience, validator().validate(token))
    }

    @Test
    fun audienceMismatch_returnsInvalidAudience() {
        assertEquals(
            IdTokenValidationError.InvalidAudience,
            validator().validate(validToken(aud = "\"other-client\"")),
        )
    }

    @Test
    fun audienceArrayContainsExpected_passes() {
        // multi-element aud now also requires a matching azp (separate gate)
        assertNull(validator().validate(gatedToken(aud = """["other-client","$AUDIENCE"]""", azp = AUDIENCE)))
    }

    @Test
    fun expMissing_returnsExpired() {
        val token = jwt("""{"iss":"$ISSUER","sub":"user","aud":"$AUDIENCE","iat":900}""")

        assertEquals(IdTokenValidationError.Expired, validator().validate(token))
    }

    @Test
    fun expired_whenExpPlusLeewayAtNow() {
        // exp 940 + leeway 60 = 1000, and 1000 <= now(1000) → expired (boundary)
        assertEquals(
            IdTokenValidationError.Expired,
            validator().validate(validToken(exp = 940)),
        )
    }

    @Test
    fun valid_whenExpPlusLeewayJustAfterNow() {
        // exp 941 + leeway 60 = 1001 > now(1000) → valid (boundary)
        assertNull(validator().validate(validToken(exp = 941)))
    }

    @Test
    fun zeroLeeway_expiresExactlyAtNow() {
        // exp 1000 + leeway 0 = 1000 <= now(1000) → expired; proves leeway threads through
        assertEquals(
            IdTokenValidationError.Expired,
            validator(leeway = 0).validate(validToken(exp = NOW)),
        )
    }

    @Test
    fun issuedAtMissing_returnsMissingIssuedAt() {
        val token = jwt("""{"iss":"$ISSUER","sub":"user","aud":"$AUDIENCE","exp":5000}""")

        assertEquals(IdTokenValidationError.MissingIssuedAt, validator().validate(token))
    }

    @Test
    fun firstFailureWins_issuerCheckedBeforeSubject() {
        // both iss wrong AND sub missing → issuer error wins (ordering)
        val token = jwt("""{"iss":"https://evil/","aud":"$AUDIENCE","exp":5000,"iat":900}""")

        assertEquals(IdTokenValidationError.InvalidIssuer, validator().validate(token))
    }


    @Test
    fun emptyContext_skipsAllGatedChecks() {
        // no nonce/azp/auth_time/org claims + default empty context → valid
        assertNull(validator().validate(gatedToken()))
    }

    @Test
    fun emptyContext_ignoresPresentNonceClaim() {
        // token carries a nonce, but context.nonce is null → gate must not fire
        assertNull(validator().validate(gatedToken(nonce = "abc")))
    }


    @Test
    fun nonce_matches_returnsNull() {
        assertNull(
            validator().validate(
                gatedToken(nonce = "abc"),
                IdTokenValidationContext(nonce = "abc"),
            ),
        )
    }

    @Test
    fun nonce_mismatch_returnsInvalidNonce() {
        assertEquals(
            IdTokenValidationError.InvalidNonce,
            validator().validate(
                gatedToken(nonce = "abc"),
                IdTokenValidationContext(nonce = "expected"),
            ),
        )
    }

    @Test
    fun nonce_contextSet_butClaimMissing_returnsInvalidNonce() {
        assertEquals(
            IdTokenValidationError.InvalidNonce,
            validator().validate(
                gatedToken(nonce = null),
                IdTokenValidationContext(nonce = "expected"),
            ),
        )
    }


    @Test
    fun multiAud_azpMatches_returnsNull() {
        assertNull(
            validator().validate(
                gatedToken(aud = """["other-client","$AUDIENCE"]""", azp = AUDIENCE),
            ),
        )
    }

    @Test
    fun multiAud_azpMismatch_returnsInvalidAuthorizedParty() {
        assertEquals(
            IdTokenValidationError.InvalidAuthorizedParty,
            validator().validate(
                gatedToken(aud = """["other-client","$AUDIENCE"]""", azp = "other-client"),
            ),
        )
    }

    @Test
    fun multiAud_azpMissing_returnsInvalidAuthorizedParty() {
        assertEquals(
            IdTokenValidationError.InvalidAuthorizedParty,
            validator().validate(
                gatedToken(aud = """["other-client","$AUDIENCE"]""", azp = null),
            ),
        )
    }

    @Test
    fun singleAud_strayAzpIgnored_returnsNull() {
        // single aud → azp not checked even when present and wrong
        assertNull(validator().validate(gatedToken(azp = "wrong-party")))
    }


    @Test
    fun maxAge_withinAge_returnsNull() {
        // authTime 900 + maxAge 200 + leeway 60 = 1160 >= now(1000) → valid
        assertNull(
            validator().validate(
                gatedToken(authTime = 900),
                IdTokenValidationContext(maxAge = 200),
            ),
        )
    }

    @Test
    fun maxAge_exceeded_returnsAuthTimeExceeded() {
        // authTime 500 + maxAge 100 + leeway 60 = 660 < now(1000) → exceeded
        assertEquals(
            IdTokenValidationError.AuthTimeExceeded,
            validator().validate(
                gatedToken(authTime = 500),
                IdTokenValidationContext(maxAge = 100),
            ),
        )
    }

    @Test
    fun maxAge_set_butAuthTimeMissing_returnsAuthTimeExceeded() {
        assertEquals(
            IdTokenValidationError.AuthTimeExceeded,
            validator().validate(
                gatedToken(authTime = null),
                IdTokenValidationContext(maxAge = 100),
            ),
        )
    }


    @Test
    fun orgId_matches_returnsNull() {
        assertNull(
            validator().validate(
                gatedToken(orgId = "org_abc"),
                IdTokenValidationContext(organization = "org_abc"),
            ),
        )
    }

    @Test
    fun orgId_mismatch_returnsInvalidOrganization() {
        assertEquals(
            IdTokenValidationError.InvalidOrganization,
            validator().validate(
                gatedToken(orgId = "org_other"),
                IdTokenValidationContext(organization = "org_abc"),
            ),
        )
    }

    @Test
    fun orgName_caseInsensitiveMatch_returnsNull() {
        assertNull(
            validator().validate(
                gatedToken(orgName = "myorg"),
                IdTokenValidationContext(organization = "MyOrg"),
            ),
        )
    }

    @Test
    fun orgName_mismatch_returnsInvalidOrganization() {
        assertEquals(
            IdTokenValidationError.InvalidOrganization,
            validator().validate(
                gatedToken(orgName = "other"),
                IdTokenValidationContext(organization = "MyOrg"),
            ),
        )
    }

    @Test
    fun gatedOrder_nonceCheckedBeforeAzp() {
        // both nonce wrong AND multi-aud azp missing → nonce error wins
        val token = gatedToken(aud = """["other-client","$AUDIENCE"]""", nonce = "abc", azp = null)

        assertEquals(
            IdTokenValidationError.InvalidNonce,
            validator().validate(token, IdTokenValidationContext(nonce = "expected")),
        )
    }
}
