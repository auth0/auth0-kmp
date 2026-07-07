package com.auth0.kmp.core.validation

import com.auth0.kmp.core.validation.model.JwtHeader
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JwtHeaderTest {

    private val b64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    private fun token(headerJson: String): String {
        val header = b64.encode(headerJson.encodeToByteArray())
        return "$header.payload.signature"
    }

    @Test
    fun decodes_alg_and_kid_from_valid_header() {
        val header = decodeJwtHeader(token("""{"alg":"RS256","kid":"abc"}"""))

        assertEquals(JwtHeader(algorithm = "RS256", keyId = "abc"), header)
    }

    @Test
    fun kid_absent_decodes_to_null_key_id() {
        val header = decodeJwtHeader(token("""{"alg":"RS256"}"""))

        assertEquals(JwtHeader(algorithm = "RS256", keyId = null), header)
    }

    @Test
    fun returns_null_when_not_three_segments() {
        assertNull(decodeJwtHeader("a.b"))
    }

    @Test
    fun returns_null_when_header_segment_not_base64() {
        assertNull(decodeJwtHeader("!!!.payload.signature"))
    }

    @Test
    fun ignores_unknown_header_fields() {
        val header = decodeJwtHeader(token("""{"alg":"RS256","kid":"abc","typ":"JWT"}"""))

        assertEquals(JwtHeader(algorithm = "RS256", keyId = "abc"), header)
    }
}
