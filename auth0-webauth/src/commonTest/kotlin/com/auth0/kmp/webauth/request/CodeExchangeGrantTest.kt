package com.auth0.kmp.webauth.request

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content

class CodeExchangeGrantTest {

    @Test
    fun builds_authorization_code_parameters() {
        val params = CodeExchangeGrant(
            code = "the-code",
            codeVerifier = "the-verifier",
            redirectUri = "myapp://callback",
            clientId = "cid",
        ).parameters

        assertEquals("authorization_code", params.str("grant_type"))
        assertEquals("cid", params.str("client_id"))
        assertEquals("the-code", params.str("code"))
        assertEquals("the-verifier", params.str("code_verifier"))
        assertEquals("myapp://callback", params.str("redirect_uri"))
    }
}
