package com.auth0.kmp.credentials

import kotlin.test.Test
import kotlin.test.assertEquals

class RefreshTokenGrantTest {

    @Test
    fun builds_reserved_parameters() {
        val params = RefreshTokenGrant(
            refreshToken = "rt",
            clientId = "cid",
            scope = "openid profile",
            audience = "https://api",
        ).parameters

        assertEquals("refresh_token", params["grant_type"])
        assertEquals("cid", params["client_id"])
        assertEquals("rt", params["refresh_token"])
        assertEquals("openid profile", params["scope"])
        assertEquals("https://api", params["audience"])
    }

    @Test
    fun omits_optional_parameters_when_null() {
        val params = RefreshTokenGrant(refreshToken = "rt", clientId = "cid").parameters

        assertEquals(false, params.containsKey("scope"))
        assertEquals(false, params.containsKey("audience"))
    }

    @Test
    fun extraParams_cannot_override_reserved_keys() {
        val params = RefreshTokenGrant(
            refreshToken = "rt",
            clientId = "cid",
            extraParams = mapOf(
                "grant_type" to "malicious",
                "client_id" to "spoofed",
                "refresh_token" to "spoofed",
            ),
        ).parameters

        assertEquals("refresh_token", params["grant_type"])
        assertEquals("cid", params["client_id"])
        assertEquals("rt", params["refresh_token"])
    }

    @Test
    fun extraParams_non_reserved_keys_pass_through() {
        val params = RefreshTokenGrant(
            refreshToken = "rt",
            clientId = "cid",
            extraParams = mapOf("custom" to "value"),
        ).parameters

        assertEquals("value", params["custom"])
    }
}
