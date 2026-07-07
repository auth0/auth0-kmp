package com.auth0.kmp.webauth.validation

import com.auth0.kmp.core.validation.IdTokenValidationError
import com.auth0.kmp.webauth.jwks.Jwk
import com.auth0.kmp.webauth.jwks.JwksProvider
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

private val b64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

// RFC 7515 Appendix A.2 — the same RSA key + signature exercised by Rs256VerifyTest.
private const val A2_PAYLOAD =
    "eyJpc3MiOiJqb2UiLA0KICJleHAiOjEzMDA4MTkzODAsDQogImh0dHA6Ly9leGFt" +
        "cGxlLmNvbS9pc19yb290Ijp0cnVlfQ"
private const val A2_SIGNATURE =
    "cC4hiUPoj9Eetdgtv3hF80EGrhuB__dzERat0XF9g2VtQgr9PJbu3XOiZj5RZmh7" +
        "AAuHIm4Bh-0Qc_lF5YKt_O8W2Fp5jujGbds9uJdbF9CUAr7t1dnZcAcQjbKBYNX" +
        "4BAynRFdiuB--f_nZLgrnbyTyWzO75vRK5h6xBArLIARNPvkSjtQBMHlb1L07Qe" +
        "7K0GarZRmB_eSN9383LcOLn6_dO--xi12jzDwusC-eOkHWEsqtFZESc6BfI7noO" +
        "PqvhJ1phCnvWh6IeYI2w9QOYEUipUTI8np6LbgGY9Fs98rqVt5AXLIhWkWywlVm" +
        "tVrBp0igcN_IoypGlUPQGe77Rw"
private const val A2_MODULUS =
    "ofgWCuLjybRlzo0tZWJjNiuSfb4p4fAkd_wWJcyQoTbji9k0l8W26mPddxHmfHQp" +
        "-Vaw-4qPCJrcS2mJPMEzP1Pt0Bm4d4QlL-yRT-SFd2lZS-pCgNMsD1W_YpRPEw" +
        "OWvG6b32690r2jZ47soMZo9wGzjb_7OMg0LOL-bSf63kpaSHSXndS5z5rexMdbB" +
        "YUsLA9e-KXBdQOS-UTo7WTBEMa2R2CapHg665xsmtdVMTBQY4uDZlxvb3qCo5Zw" +
        "Kh9kG4LT6_I5IhlJH7aGhyxXFvUK-DWNmoudF8NAco9_h9iaGNj8q2ethFkMLs9" +
        "1kzk2PAcDTW9gb54h4FRWyuXpoQ"
private const val A2_EXPONENT = "AQAB"

private fun header(json: String): String = b64.encode(json.encodeToByteArray())

private fun token(headerJson: String, payload: String = A2_PAYLOAD, signature: String = A2_SIGNATURE): String =
    "${header(headerJson)}.$payload.$signature"

private class FakeJwksProvider(private val keys: Map<String, Jwk> = emptyMap()) : JwksProvider {
    var fetchCount = 0
        private set

    override suspend fun fetch(kid: String): Jwk? {
        fetchCount++
        return keys[kid]
    }
}

private fun providerWith(kid: String): FakeJwksProvider =
    FakeJwksProvider(mapOf(kid to Jwk(kid = kid, modulus = A2_MODULUS, exponent = A2_EXPONENT)))

class Rs256IdTokenSignatureValidatorTest {

    @Test
    fun unsupported_algorithm_is_rejected_without_querying_jwks() = runTest {
        val provider = FakeJwksProvider()
        val validator = Rs256IdTokenSignatureValidator(provider)

        val error = validator.verify(token("""{"alg":"HS256","kid":"k1"}"""))

        assertEquals(IdTokenValidationError.UnsupportedAlgorithm, error)
        assertEquals(0, provider.fetchCount)
    }

    @Test
    fun none_algorithm_is_rejected() = runTest {
        val validator = Rs256IdTokenSignatureValidator(FakeJwksProvider())

        val error = validator.verify(token("""{"alg":"none","kid":"k1"}"""))

        assertEquals(IdTokenValidationError.UnsupportedAlgorithm, error)
    }

    @Test
    fun missing_kid_returns_public_key_not_found() = runTest {
        val validator = Rs256IdTokenSignatureValidator(FakeJwksProvider())

        val error = validator.verify(token("""{"alg":"RS256"}"""))

        assertEquals(IdTokenValidationError.PublicKeyNotFound, error)
    }

    @Test
    fun unknown_kid_returns_public_key_not_found() = runTest {
        val validator = Rs256IdTokenSignatureValidator(FakeJwksProvider())

        val error = validator.verify(token("""{"alg":"RS256","kid":"k1"}"""))

        assertEquals(IdTokenValidationError.PublicKeyNotFound, error)
    }

    @Test
    fun non_jwt_returns_cannot_decode() = runTest {
        val validator = Rs256IdTokenSignatureValidator(FakeJwksProvider())

        assertEquals(IdTokenValidationError.CannotDecode, validator.verify("a.b"))
    }

    @Test
    fun bad_signature_returns_invalid_signature() = runTest {
        // The header carries a kid, so the signed bytes differ from A.2's header.payload;
        // the A.2 signature therefore does not verify against this token.
        val validator = Rs256IdTokenSignatureValidator(providerWith("k1"))

        val error = validator.verify(token("""{"alg":"RS256","kid":"k1"}"""))

        assertEquals(IdTokenValidationError.InvalidSignature, error)
    }

    @Test
    fun undecodable_signature_fails_closed_to_invalid_signature() = runTest {
        val validator = Rs256IdTokenSignatureValidator(providerWith("k1"))

        val error = validator.verify(token("""{"alg":"RS256","kid":"k1"}""", signature = "!!!"))

        assertEquals(IdTokenValidationError.InvalidSignature, error)
    }
}
