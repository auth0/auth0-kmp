package com.auth0.kmp.authentication.request

import com.auth0.kmp.core.annotation.InternalAuth0Api
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content

@OptIn(InternalAuth0Api::class)
class PasswordRealmGrantTest {

    @Test
    fun buildsAllWireParameters_whenAudienceProvided() {
        val params = PasswordRealmGrant(
            usernameOrEmail = "user",
            password = "pw",
            realm = "db",
            clientId = "client-123",
            scope = "openid profile",
            audience = "https://api",
        ).parameters

        assertEquals("http://auth0.com/oauth/grant-type/password-realm", params.str("grant_type"))
        assertEquals("client-123", params.str("client_id"))
        assertEquals("user", params.str("username"))
        assertEquals("pw", params.str("password"))
        assertEquals("db", params.str("realm"))
        assertEquals("openid profile", params.str("scope"))
        assertEquals("https://api", params.str("audience"))
    }

    @Test
    fun omitsAudience_whenNull() {
        val params = PasswordRealmGrant(
            usernameOrEmail = "user",
            password = "pw",
            realm = "db",
            clientId = "client-123",
            scope = "openid",
            audience = null,
        ).parameters

        assertFalse(params.containsKey("audience"))
    }

    @Test
    fun extraParameters_appearInWireParameters() {
        val params = PasswordRealmGrant(
            usernameOrEmail = "user",
            password = "pw",
            realm = "db",
            clientId = "client-123",
            scope = "openid",
            audience = null,
            extraParameters = mapOf("organization" to "org_123"),
        ).parameters

        assertEquals("org_123", params.str("organization"))
        assertEquals("http://auth0.com/oauth/grant-type/password-realm", params.str("grant_type"))
        assertEquals("client-123", params.str("client_id"))
    }

    @Test
    fun reservedKeys_notOverridableByExtraParameters() {
        val params = PasswordRealmGrant(
            usernameOrEmail = "user",
            password = "pw",
            realm = "db",
            clientId = "client-123",
            scope = "openid",
            audience = null,
            extraParameters = mapOf(
                "grant_type" to "evil",
                "client_id" to "hacker",
                "username" to "attacker",
                "password" to "leak",
                "realm" to "evil-realm",
                "scope" to "hacked",
            ),
        ).parameters

        assertEquals("http://auth0.com/oauth/grant-type/password-realm", params.str("grant_type"))
        assertEquals("client-123", params.str("client_id"))
        assertEquals("user", params.str("username"))
        assertEquals("pw", params.str("password"))
        assertEquals("db", params.str("realm"))
        assertEquals("openid", params.str("scope"))
    }

    @Test
    fun audienceFromExtraParameters_survives_whenTypedAudienceIsNull() {
        val params = PasswordRealmGrant(
            usernameOrEmail = "user",
            password = "pw",
            realm = "db",
            clientId = "client-123",
            scope = "openid",
            audience = null,
            extraParameters = mapOf("audience" to "https://from-extra"),
        ).parameters

        assertEquals("https://from-extra", params.str("audience"))
    }

    @Test
    fun typedAudience_winsOver_extraParameters() {
        val params = PasswordRealmGrant(
            usernameOrEmail = "user",
            password = "pw",
            realm = "db",
            clientId = "client-123",
            scope = "openid",
            audience = "https://typed",
            extraParameters = mapOf("audience" to "https://from-extra"),
        ).parameters

        assertEquals("https://typed", params.str("audience"))
    }
}
