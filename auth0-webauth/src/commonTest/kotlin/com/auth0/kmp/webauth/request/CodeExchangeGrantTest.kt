package com.auth0.kmp.webauth.request

import kotlin.test.Test
import kotlin.test.assertEquals

class CodeExchangeGrantTest {

    @Test
    fun builds_authorization_code_parameters() {
        val params = CodeExchangeGrant(
            code = "the-code",
            codeVerifier = "the-verifier",
            redirectUri = "myapp://callback",
            clientId = "cid",
        ).parameters

        assertEquals("authorization_code", params["grant_type"])
        assertEquals("cid", params["client_id"])
        assertEquals("the-code", params["code"])
        assertEquals("the-verifier", params["code_verifier"])
        assertEquals("myapp://callback", params["redirect_uri"])
    }
}
