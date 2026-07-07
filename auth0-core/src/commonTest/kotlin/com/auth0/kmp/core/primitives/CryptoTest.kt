package com.auth0.kmp.core.primitives

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private fun ByteArray.toHex(): String =
    joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

class CryptoTest {

    @Test
    fun sha256_ofEmptyInput_matchesKnownVector() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            ByteArray(0).sha256().toHex(),
        )
    }

    @Test
    fun sha256_ofAbc_matchesKnownVector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            "abc".encodeToByteArray().sha256().toHex(),
        )
    }

    @Test
    fun sha256_producesThirtyTwoBytes() {
        assertEquals(32, "abc".encodeToByteArray().sha256().size)
    }

    @Test
    fun generateSecureRandomBytes_returnsRequestedLength() {
        assertEquals(32, generateSecureRandomBytes(32).size)
    }

    @Test
    fun generateSecureRandomBytes_withZero_returnsEmpty() {
        assertContentEquals(ByteArray(0), generateSecureRandomBytes(0))
    }

    @Test
    fun generateSecureRandomBytes_twoCalls_differ() {
        assertFalse(
            generateSecureRandomBytes(32).contentEquals(generateSecureRandomBytes(32)),
        )
    }
}
