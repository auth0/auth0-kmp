package com.auth0.kmp.core.dpop

import com.auth0.kmp.core.primitives.encodeBase64Url
import com.auth0.kmp.core.primitives.sha256
import kotlin.test.Test
import kotlin.test.assertEquals

class DPoPJwkTest {

    // Expected value computed independently:
    //   printf '%s' '{"crv":"P-256","kty":"EC","x":"test-x-coordinate","y":"test-y-coordinate"}' \
    //     | openssl dgst -sha256 -binary | openssl base64 -A | tr '+/' '-_' | tr -d '='
    @Test
    fun thumbprint_matches_precomputed_rfc7638_hash() {
        val jwk = DPoPJwk(x = "test-x-coordinate", y = "test-y-coordinate")

        assertEquals("wPa-eHOqjh1bAzCWFqeRyvBMYCXRLZfCk6SpbRifzBs", jwk.thumbprint())
    }

    @Test
    fun thumbprint_hashes_members_in_rfc7638_order_crv_kty_x_y() {
        val jwk = DPoPJwk(x = "abc", y = "def")

        // RFC 7638 requires the canonical JSON to order members lexicographically:
        // crv, kty, x, y — with no whitespace.
        val canonical = """{"crv":"P-256","kty":"EC","x":"abc","y":"def"}"""
        val expected = canonical.encodeToByteArray().sha256().encodeBase64Url()

        assertEquals(expected, jwk.thumbprint())
    }
}
