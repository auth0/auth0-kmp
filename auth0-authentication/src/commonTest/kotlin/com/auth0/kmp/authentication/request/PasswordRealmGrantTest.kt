package com.auth0.kmp.authentication.request

import com.auth0.kmp.core.annotation.InternalAuth0Api
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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

        assertEquals("http://auth0.com/oauth/grant-type/password-realm", params["grant_type"])
        assertEquals("client-123", params["client_id"])
        assertEquals("user", params["username"])
        assertEquals("pw", params["password"])
        assertEquals("db", params["realm"])
        assertEquals("openid profile", params["scope"])
        assertEquals("https://api", params["audience"])
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
}
