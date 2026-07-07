package com.auth0.kmp.core.validation

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val unpadded = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
private val padded = Base64.UrlSafe.withPadding(Base64.PaddingOption.PRESENT)

private fun jwt(
    payloadJson: String,
    encoder: Base64 = unpadded,
): String {
    val header = unpadded.encode("""{"alg":"none"}""".encodeToByteArray())
    val payload = encoder.encode(payloadJson.encodeToByteArray())
    return "$header.$payload.sig"
}

class JwtTest {

    @Test
    fun decodesAllClaims() {
        val claims = decodeJwtClaims(
            jwt("""{"iss":"https://t.auth0.com/","sub":"user-1","aud":"the-aud","exp":1500,"iat":1000}"""),
        )

        assertEquals("https://t.auth0.com/", claims!!.issuer)
        assertEquals("user-1", claims.subject)
        assertEquals(listOf("the-aud"), claims.audience)
        assertEquals(1500, claims.expiresAt)
        assertEquals(1000, claims.issuedAt)
    }

    @Test
    fun audAsArray_becomesList() {
        val claims = decodeJwtClaims(jwt("""{"aud":["a","b"]}"""))

        assertEquals(listOf("a", "b"), claims!!.audience)
    }

    @Test
    fun audAbsent_isEmptyList() {
        val claims = decodeJwtClaims(jwt("""{"sub":"user"}"""))

        assertEquals(emptyList(), claims!!.audience)
    }

    @Test
    fun audArray_filtersNonStringElements() {
        val claims = decodeJwtClaims(jwt("""{"aud":["a",123,true]}"""))

        assertEquals(listOf("a"), claims!!.audience)
    }

    @Test
    fun optionalClaimsAbsent_areNull() {
        val claims = decodeJwtClaims(jwt("{}"))

        assertNull(claims!!.issuer)
        assertNull(claims.subject)
        assertNull(claims.expiresAt)
        assertNull(claims.issuedAt)
        assertEquals(emptyList(), claims.audience)
    }

    @Test
    fun wrongSegmentCount_returnsNull() {
        assertNull(decodeJwtClaims("a.b"))
        assertNull(decodeJwtClaims("a.b.c.d"))
    }

    @Test
    fun paddedPayloadSegment_decodes() {
        val claims = decodeJwtClaims(jwt("""{"sub":"user"}""", encoder = padded))

        assertEquals("user", claims!!.subject)
    }

    @Test
    fun malformedBase64Payload_returnsNull() {
        assertNull(decodeJwtClaims("aaaa.!!!notbase64!!!.cccc"))
    }

    @Test
    fun payloadNotJsonObject_returnsNull() {
        assertNull(decodeJwtClaims(jwt(""""hello"""")))
    }

    @Test
    fun audAsJsonObject_returnsNull() {
        assertNull(decodeJwtClaims(jwt("""{"aud":{"nested":"x"}}""")))
    }
}
