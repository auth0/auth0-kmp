package com.auth0.kmp.webauth.request

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains

class CodeExchangeRequestTest {

    private val request = CodeExchangeRequest(
        code = "the-code",
        codeVerifier = "the-verifier",
        redirectUri = "myapp://callback",
        clientId = "cid",
    )

    @Test
    fun serializes_oauth_wire_field_names() {
        val json = Json.encodeToString(CodeExchangeRequest.serializer(), request)
        assertContains(json, "\"code\":\"the-code\"")
        assertContains(json, "\"code_verifier\":\"the-verifier\"")
        assertContains(json, "\"redirect_uri\":\"myapp://callback\"")
        assertContains(json, "\"client_id\":\"cid\"")
    }

    @Test
    fun always_emits_default_grant_type() {
        val json = Json.encodeToString(CodeExchangeRequest.serializer(), request)
        assertContains(json, "\"grant_type\":\"authorization_code\"")
    }
}
