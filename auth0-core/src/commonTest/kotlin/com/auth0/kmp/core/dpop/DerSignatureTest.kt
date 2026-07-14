package com.auth0.kmp.core.dpop

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DerSignatureTest {

    // Builds `SEQUENCE { INTEGER r, INTEGER s }` with short-form lengths (bodies < 128 bytes).
    private fun der(r: ByteArray, s: ByteArray): ByteArray {
        val body = byteArrayOf(0x02, r.size.toByte()) + r + byteArrayOf(0x02, s.size.toByte()) + s
        return byteArrayOf(0x30, body.size.toByte()) + body
    }

    @Test
    fun full_length_r_and_s_are_concatenated_verbatim() {
        val r = ByteArray(32) { 0x11 }
        val s = ByteArray(32) { 0x22 }

        val raw = derToRawSignature(der(r, s))

        assertTrue((r + s).contentEquals(raw))
    }

    @Test
    fun leading_zero_sign_guard_is_trimmed() {
        // R is 33 bytes: a 0x00 pad guarding a high-bit-set MSB, which must be dropped.
        val guardedR = byteArrayOf(0x00) + ByteArray(32) { 0xFF.toByte() }
        val s = ByteArray(32) { 0x22 }

        val raw = derToRawSignature(der(guardedR, s))

        val expected = ByteArray(32) { 0xFF.toByte() } + s
        assertTrue(expected.contentEquals(raw))
    }

    @Test
    fun short_integer_is_left_padded_to_32() {
        val shortR = byteArrayOf(0x05)
        val s = ByteArray(32) { 0x22 }

        val raw = derToRawSignature(der(shortR, s))

        val expectedR = ByteArray(32).also { it[31] = 0x05 }
        assertTrue((expectedR + s).contentEquals(raw))
    }

    @Test
    fun long_form_integer_length_is_decoded() {
        // R length encoded in long form (0x81 0x20 == 32) exercises decodeDerLength's high-bit branch.
        val r = ByteArray(32) { 0x33 }
        val s = ByteArray(32) { 0x44 }
        val rInteger = byteArrayOf(0x02, 0x81.toByte(), 0x20) + r
        val sInteger = byteArrayOf(0x02, s.size.toByte()) + s
        val body = rInteger + sInteger
        val encoded = byteArrayOf(0x30, body.size.toByte()) + body

        val raw = derToRawSignature(encoded)

        assertTrue((r + s).contentEquals(raw))
    }

    @Test
    fun malformed_der_missing_sequence_throws() {
        assertFailsWith<IllegalArgumentException> {
            derToRawSignature(byteArrayOf(0x02, 0x01, 0x05))
        }
    }

    @Test
    fun der_length_mismatch_throws() {
        // Declares a 16-byte body but only 3 follow.
        assertFailsWith<IllegalArgumentException> {
            derToRawSignature(byteArrayOf(0x30, 0x10, 0x02, 0x01, 0x05))
        }
    }
}
