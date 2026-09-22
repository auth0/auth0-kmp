package com.auth0.kmp.authentication.request

import com.auth0.kmp.core.annotation.InternalAuth0Api
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content

@OptIn(InternalAuth0Api::class)
class PasswordlessLoginGrantTest {

    @Test
    fun buildsAllWireParameters_whenAudienceProvided() {
        val params = PasswordlessLoginGrant(
            username = "a@b.com",
            otp = "123456",
            realm = "email",
            clientId = "client-123",
            scope = "openid profile",
            audience = "https://api",
        ).parameters

        assertEquals("http://auth0.com/oauth/grant-type/passwordless/otp", params.str("grant_type"))
        assertEquals("client-123", params.str("client_id"))
        assertEquals("a@b.com", params.str("username"))
        assertEquals("123456", params.str("otp"))
        assertEquals("email", params.str("realm"))
        assertEquals("openid profile", params.str("scope"))
        assertEquals("https://api", params.str("audience"))
    }

    @Test
    fun omitsAudience_whenNull() {
        val params = PasswordlessLoginGrant(
            username = "+15551234567",
            otp = "123456",
            realm = "sms",
            clientId = "client-123",
            scope = "openid",
            audience = null,
        ).parameters

        assertFalse(params.containsKey("audience"))
    }

    @Test
    fun extraParameters_appearInWireParameters() {
        val params = PasswordlessLoginGrant(
            username = "a@b.com",
            otp = "123456",
            realm = "email",
            clientId = "client-123",
            scope = "openid",
            audience = null,
            extraParameters = mapOf("organization" to "org_123"),
        ).parameters

        assertEquals("org_123", params.str("organization"))
        assertEquals("http://auth0.com/oauth/grant-type/passwordless/otp", params.str("grant_type"))
        assertEquals("client-123", params.str("client_id"))
    }

    @Test
    fun reservedKeys_notOverridableByExtraParameters() {
        val params = PasswordlessLoginGrant(
            username = "a@b.com",
            otp = "123456",
            realm = "email",
            clientId = "client-123",
            scope = "openid",
            audience = null,
            extraParameters = mapOf(
                "grant_type" to "evil",
                "client_id" to "hacker",
                "username" to "attacker",
                "otp" to "000000",
                "realm" to "evil-realm",
                "scope" to "hacked",
            ),
        ).parameters

        assertEquals("http://auth0.com/oauth/grant-type/passwordless/otp", params.str("grant_type"))
        assertEquals("client-123", params.str("client_id"))
        assertEquals("a@b.com", params.str("username"))
        assertEquals("123456", params.str("otp"))
        assertEquals("email", params.str("realm"))
        assertEquals("openid", params.str("scope"))
    }

    @Test
    fun typedAudience_winsOver_extraParameters() {
        val params = PasswordlessLoginGrant(
            username = "a@b.com",
            otp = "123456",
            realm = "email",
            clientId = "client-123",
            scope = "openid",
            audience = "https://typed",
            extraParameters = mapOf("audience" to "https://from-extra"),
        ).parameters

        assertEquals("https://typed", params.str("audience"))
    }
}
