package com.auth0.kmp.webauth.pkce

import com.auth0.kmp.core.primitives.sha256
import com.auth0.kmp.webauth.internal.base64UrlNoPad
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PkceTest {

    @Test
    fun generate_challenge_is_s256_of_verifier() {
        val pkce = Pkce.generate()
        val expected = pkce.codeVerifier.encodeToByteArray().sha256().base64UrlNoPad()
        assertEquals(expected, pkce.codeChallenge)
    }

    @Test
    fun generate_method_is_S256() {
        assertEquals("S256", Pkce.generate().codeChallengeMethod)
    }

    @Test
    fun generate_verifier_is_url_safe_43_chars() {
        val verifier = Pkce.generate().codeVerifier
        assertEquals(43, verifier.length)
        assertTrue(verifier.all { it.isLetterOrDigit() || it == '-' || it == '_' }, "verifier: $verifier")
        assertFalse(verifier.contains('='))
    }

    @Test
    fun rfc7636_appendix_b_known_answer() {
        // RFC 7636 Appendix B: verifier -> code_challenge = BASE64URL(SHA256(ASCII(verifier)))
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val expectedChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
        val challenge = verifier.encodeToByteArray().sha256().base64UrlNoPad()
        assertEquals(expectedChallenge, challenge)
    }
}
