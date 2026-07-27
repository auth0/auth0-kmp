package com.auth0.kmp.webauth.authorize

import com.auth0.kmp.core.Auth0Account
import com.auth0.kmp.webauth.LogoutOptions
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class LogoutUrlBuilderTest {

    private val account = Auth0Account(clientId = "cid", domain = "test.auth0.com")
    private val returnTo = "myapp://test.auth0.com/android/com.example/callback"

    @Test
    fun builds_https_logout_url_for_account_domain() {
        val url = buildLogoutUrl(account, returnTo, LogoutOptions())
        assertTrue(url.startsWith("https://test.auth0.com/v2/logout?"), "url: $url")
    }

    @Test
    fun includes_client_id_and_returnTo() {
        val url = buildLogoutUrl(account, returnTo, LogoutOptions())
        assertContains(url, "client_id=cid")
        assertContains(url, "returnTo=")
    }

    @Test
    fun url_encodes_returnTo() {
        val url = buildLogoutUrl(account, returnTo, LogoutOptions())
        assertContains(url, "returnTo=myapp%3A%2F%2Ftest.auth0.com%2Fandroid%2Fcom.example%2Fcallback")
    }

    @Test
    fun omits_federated_when_false() {
        val url = buildLogoutUrl(account, returnTo, LogoutOptions())
        assertTrue(!url.contains("federated"), "url: $url")
    }

    @Test
    fun includes_federated_when_true() {
        val url = buildLogoutUrl(account, returnTo, LogoutOptions(federated = true))
        assertContains(url, "federated=1")
    }

    @Test
    fun appends_extra_parameters() {
        val url = buildLogoutUrl(account, returnTo, LogoutOptions(extraParameters = mapOf("ui_locales" to "en")))
        assertContains(url, "ui_locales=en")
    }

    @Test
    fun extra_parameters_cannot_override_sdk_params() {
        val url = buildLogoutUrl(
            account,
            returnTo,
            LogoutOptions(
                federated = false,
                extraParameters = mapOf(
                    "client_id" to "attacker",
                    "returnTo" to "https://evil.example.com",
                    "ui_locales" to "en",
                ),
            ),
        )
        assertContains(url, "client_id=cid")
        assertTrue(!url.contains("client_id=attacker"), "url: $url")
        assertTrue(!url.contains("evil.example.com"), "url: $url")
        assertContains(url, "ui_locales=en")
    }
}
