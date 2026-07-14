package com.auth0.kmp.core.dpop

import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.primitives.decodeBase64Url
import com.auth0.kmp.core.primitives.encodeBase64Url
import com.auth0.kmp.core.primitives.sha256
import com.auth0.kmp.core.result.Result
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(InternalAuth0Api::class)
class DPoPProofGeneratorTest {

    private val fixedClock = object : Clock {
        override fun now(): Instant = Instant.fromEpochSeconds(1_700_000_000)
    }

    private fun generator(store: FakeDPoPKeyStore) = DPoPProofGenerator(store, fixedClock)

    private fun <T> Result<T, DPoPError>.success(): T = when (this) {
        is Result.Success -> data
        is Result.Failure -> fail("expected Success but was Failure($error)")
    }

    private fun decodeSegment(segment: String): JsonObject =
        Json.parseToJsonElement(segment.decodeBase64Url().decodeToString()).jsonObject

    private val tokenUrl = "https://example.auth0.com/oauth/token"

    @Test
    fun generate_produces_three_base64url_segments() {
        val proof = generator(FakeDPoPKeyStore()).generate(tokenUrl, "POST").success()

        val parts = proof.split(".")
        assertEquals(3, parts.size)
        parts.forEach { assertTrue(it.isNotEmpty()) }
    }

    @Test
    fun header_carries_typ_alg_and_public_jwk() {
        val store = FakeDPoPKeyStore(jwk = DPoPJwk(x = "xx", y = "yy"))
        val proof = generator(store).generate(tokenUrl, "POST").success()

        val header = decodeSegment(proof.split(".")[0])
        assertEquals("dpop+jwt", header["typ"]!!.jsonPrimitive.content)
        assertEquals("ES256", header["alg"]!!.jsonPrimitive.content)
        val jwk = header["jwk"]!!.jsonObject
        assertEquals("EC", jwk["kty"]!!.jsonPrimitive.content)
        assertEquals("P-256", jwk["crv"]!!.jsonPrimitive.content)
        assertEquals("xx", jwk["x"]!!.jsonPrimitive.content)
        assertEquals("yy", jwk["y"]!!.jsonPrimitive.content)
    }

    @Test
    fun payload_carries_uppercased_htm_stripped_htu_jti_and_iat() {
        val proof = generator(FakeDPoPKeyStore())
            .generate("$tokenUrl?foo=bar#frag", "post").success()

        val payload = decodeSegment(proof.split(".")[1])
        assertEquals("POST", payload["htm"]!!.jsonPrimitive.content)
        assertEquals(tokenUrl, payload["htu"]!!.jsonPrimitive.content)
        assertEquals(1_700_000_000L, payload["iat"]!!.jsonPrimitive.content.toLong())
        assertTrue(payload["jti"]!!.jsonPrimitive.content.isNotEmpty())
    }

    @Test
    fun payload_includes_ath_when_access_token_present() {
        val proof = generator(FakeDPoPKeyStore())
            .generate(tokenUrl, "POST", accessToken = "the-access-token").success()

        val payload = decodeSegment(proof.split(".")[1])
        val expectedAth = "the-access-token".encodeToByteArray().sha256().encodeBase64Url()
        assertEquals(expectedAth, payload["ath"]!!.jsonPrimitive.content)
    }

    @Test
    fun payload_omits_ath_and_nonce_when_absent() {
        val proof = generator(FakeDPoPKeyStore()).generate(tokenUrl, "POST").success()

        val payload = decodeSegment(proof.split(".")[1])
        assertNull(payload["ath"])
        assertNull(payload["nonce"])
    }

    @Test
    fun payload_includes_nonce_when_provided() {
        val proof = generator(FakeDPoPKeyStore())
            .generate(tokenUrl, "POST", nonce = "server-nonce").success()

        val payload = decodeSegment(proof.split(".")[1])
        assertEquals("server-nonce", payload["nonce"]!!.jsonPrimitive.content)
    }

    @Test
    fun signature_segment_is_base64url_of_keystore_signature() {
        val store = FakeDPoPKeyStore(signature = ByteArray(64) { (it * 3).toByte() })
        val proof = generator(store).generate(tokenUrl, "POST").success()

        assertEquals(store.signature.encodeBase64Url(), proof.split(".")[2])
    }

    @Test
    fun signing_input_is_the_header_dot_payload_bytes() {
        val store = FakeDPoPKeyStore()
        val proof = generator(store).generate(tokenUrl, "POST").success()

        val expected = (proof.substringBeforeLast(".")).encodeToByteArray()
        assertTrue(expected.contentEquals(store.lastSignInput))
    }

    @Test
    fun generate_returns_malformed_url_for_unparseable_url() {
        val result = generator(FakeDPoPKeyStore()).generate("not a url", "POST")

        assertEquals(Result.Failure(DPoPError.MalformedUrl), result)
    }

    @Test
    fun generate_maps_dpop_exception_to_its_error() {
        val store = FakeDPoPKeyStore().apply { failSignWith = DPoPError.SigningFailed() }

        val result = store.let { generator(it).generate(tokenUrl, "POST") }

        assertEquals(Result.Failure(DPoPError.SigningFailed()), result)
    }

    @Test
    fun generate_maps_unexpected_throwable_to_unknown() {
        val boom = IllegalStateException("boom")
        val store = FakeDPoPKeyStore().apply { signThrowable = boom }

        val result = generator(store).generate(tokenUrl, "POST")

        assertEquals(Result.Failure(DPoPError.Unknown(boom)), result)
    }

    @Test
    fun shouldGenerateProof_true_for_token_endpoint_non_refresh_grant_even_without_key() {
        val store = FakeDPoPKeyStore(hasKey = false)

        val result = generator(store).shouldGenerateProof(tokenUrl, "authorization_code")

        assertEquals(Result.Success(true), result)
        assertEquals(0, store.hasKeyCallCount)
    }

    @Test
    fun shouldGenerateProof_refresh_grant_falls_through_to_has_key() {
        val store = FakeDPoPKeyStore(hasKey = true)

        val result = generator(store).shouldGenerateProof(tokenUrl, "refresh_token")

        assertEquals(Result.Success(true), result)
        assertEquals(1, store.hasKeyCallCount)
    }

    @Test
    fun shouldGenerateProof_non_token_endpoint_returns_has_key() {
        val store = FakeDPoPKeyStore(hasKey = false)

        val result = generator(store).shouldGenerateProof("https://api.example.com/me", "GET")

        assertEquals(Result.Success(false), result)
        assertEquals(1, store.hasKeyCallCount)
    }

    @Test
    fun jkt_returns_public_jwk_thumbprint() {
        val store = FakeDPoPKeyStore(jwk = DPoPJwk(x = "abc", y = "def"))

        val result = generator(store).jkt()

        assertEquals(Result.Success(store.jwk.thumbprint()), result)
    }

    @Test
    fun jkt_maps_public_jwk_failure_to_its_error() {
        val store = FakeDPoPKeyStore().apply { failPublicJwkWith = DPoPError.KeyStoreFailed() }

        val result = generator(store).jkt()

        assertEquals(Result.Failure(DPoPError.KeyStoreFailed()), result)
    }

    @Test
    fun each_proof_carries_a_unique_jti() {
        val generator = generator(FakeDPoPKeyStore())

        val first = decodeSegment(generator.generate(tokenUrl, "POST").success().split(".")[1])
        val second = decodeSegment(generator.generate(tokenUrl, "POST").success().split(".")[1])

        assertNotEquals(
            first["jti"]!!.jsonPrimitive.content,
            second["jti"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun htu_preserves_port_and_strips_query() {
        val proof = generator(FakeDPoPKeyStore())
            .generate("https://h.example.com:8443/oauth/token?x=1", "POST").success()

        val payload = decodeSegment(proof.split(".")[1])
        assertEquals(
            "https://h.example.com:8443/oauth/token",
            payload["htu"]!!.jsonPrimitive.content
        )
    }

    @Test
    fun htu_normalizes_uppercase_scheme_and_drops_default_port() {
        val proof = generator(FakeDPoPKeyStore())
            .generate("HTTPS://Example.Auth0.com:443/oauth/token?a=1#f", "POST").success()

        val payload = decodeSegment(proof.split(".")[1])
        assertEquals(
            "https://example.auth0.com/oauth/token",
            payload["htu"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun generate_rejects_scheme_less_url_via_startsWith_guard() {
        val result = generator(FakeDPoPKeyStore()).generate("example.com/oauth/token", "POST")

        assertEquals(Result.Failure(DPoPError.MalformedUrl), result)
    }
}
