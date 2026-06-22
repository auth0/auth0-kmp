package com.auth0.kmp.authentication.validation

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

private fun validator(leeway: Long = 60): ClaimsIdTokenValidator =
    ClaimsIdTokenValidator(
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

class ClaimsIdTokenValidatorTest {

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
        assertNull(validator().validate(validToken(aud = """["other-client","$AUDIENCE"]""")))
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
}
