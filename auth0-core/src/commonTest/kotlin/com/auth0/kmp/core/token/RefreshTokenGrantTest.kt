package com.auth0.kmp.core.token

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content

class RefreshTokenGrantTest {

    @Test
    fun builds_reserved_parameters() {
        val params = RefreshTokenGrant(
            refreshToken = "rt",
            clientId = "cid",
            scope = "openid profile",
            audience = "https://api",
        ).parameters

        assertEquals("refresh_token", params.str("grant_type"))
        assertEquals("cid", params.str("client_id"))
        assertEquals("rt", params.str("refresh_token"))
        assertEquals("openid profile", params.str("scope"))
        assertEquals("https://api", params.str("audience"))
    }

    @Test
    fun omits_optional_parameters_when_null() {
        val params = RefreshTokenGrant(refreshToken = "rt", clientId = "cid").parameters

        assertEquals(false, params.containsKey("scope"))
        assertEquals(false, params.containsKey("audience"))
    }

    @Test
    fun extraParameters_cannot_override_reserved_keys() {
        val params = RefreshTokenGrant(
            refreshToken = "rt",
            clientId = "cid",
            extraParameters = mapOf(
                "grant_type" to "malicious",
                "client_id" to "spoofed",
                "refresh_token" to "spoofed",
            ),
        ).parameters

        assertEquals("refresh_token", params.str("grant_type"))
        assertEquals("cid", params.str("client_id"))
        assertEquals("rt", params.str("refresh_token"))
    }

    @Test
    fun extraParameters_non_reserved_keys_pass_through() {
        val params = RefreshTokenGrant(
            refreshToken = "rt",
            clientId = "cid",
            extraParameters = mapOf("custom" to "value"),
        ).parameters

        assertEquals("value", params.str("custom"))
    }
}
