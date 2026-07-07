package com.auth0.kmp.webauth.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class RandomTest {

    @Test
    fun base64UrlNoPad_encodes_known_vector() {
        assertEquals("Zm9vYmFy", "foobar".encodeToByteArray().base64UrlNoPad())
    }

    @Test
    fun base64UrlNoPad_strips_padding() {
        // standard base64 of "fo" is "Zm8=" — the trailing '=' must be gone
        assertEquals("Zm8", "fo".encodeToByteArray().base64UrlNoPad())
    }

    @Test
    fun base64UrlNoPad_uses_url_safe_alphabet_and_no_padding() {
        // 0xFB 0xFF 0xBF -> standard base64 "+/+/", url-safe "-_-_"
        val bytes = byteArrayOf(0xFB.toByte(), 0xFF.toByte(), 0xBF.toByte())
        val encoded = bytes.base64UrlNoPad()
        assertFalse(encoded.contains('+'), "must not contain '+'")
        assertFalse(encoded.contains('/'), "must not contain '/'")
        assertFalse(encoded.contains('='), "must not contain '='")
    }

    @Test
    fun base64UrlNoPad_empty_is_empty_string() {
        assertEquals("", ByteArray(0).base64UrlNoPad())
    }

    @Test
    fun randomUrlSafeString_default_is_43_chars_for_32_bytes() {
        // 32 bytes -> 43 base64url chars once padding is stripped (RFC 7636 minimum verifier length)
        assertEquals(43, randomUrlSafeString().length)
    }

    @Test
    fun randomUrlSafeString_is_url_safe_no_padding() {
        val value = randomUrlSafeString()
        assertFalse(value.contains('+'))
        assertFalse(value.contains('/'))
        assertFalse(value.contains('='))
    }

    @Test
    fun randomUrlSafeString_two_calls_differ() {
        assertNotEquals(randomUrlSafeString(), randomUrlSafeString())
    }
}
