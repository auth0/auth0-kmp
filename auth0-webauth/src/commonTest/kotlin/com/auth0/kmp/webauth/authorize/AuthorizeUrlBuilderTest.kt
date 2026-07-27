package com.auth0.kmp.webauth.authorize

import com.auth0.kmp.core.Auth0Account
import com.auth0.kmp.webauth.LoginOptions
import com.auth0.kmp.webauth.pkce.Pkce
import com.auth0.kmp.webauth.transaction.AuthorizeTransaction
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class AuthorizeUrlBuilderTest {

    private val account = Auth0Account(clientId = "cid", domain = "test.auth0.com")

    private fun transaction(redirectUri: String = "myapp://test.auth0.com/android/com.example/callback") =
        AuthorizeTransaction(
            state = "the-state",
            nonce = "the-nonce",
            pkce = Pkce.generate(),
            redirectUri = redirectUri,
        )

    @Test
    fun builds_https_authorize_url_for_account_domain() {
        val url = buildAuthorizeUrl(account, transaction(), LoginOptions(scope = "openid"))
        assertTrue(url.startsWith("https://test.auth0.com/authorize?"), "url: $url")
    }

    @Test
    fun includes_all_fixed_params() {
        val txn = transaction()
        val url = buildAuthorizeUrl(account, txn, LoginOptions(scope = "openid profile email"))
        assertContains(url, "response_type=code")
        assertContains(url, "client_id=cid")
        assertContains(url, "state=the-state")
        assertContains(url, "nonce=the-nonce")
        assertContains(url, "code_challenge=${txn.pkce.codeChallenge}")
        assertContains(url, "code_challenge_method=S256")
    }

    @Test
    fun url_encodes_scope_and_redirect_uri() {
        val url =
            buildAuthorizeUrl(account, transaction(), LoginOptions(scope = "openid profile email"))
        // space in scope must be percent/plus encoded, not a raw space
        assertTrue(!url.contains("scope=openid profile email"), "scope not encoded: $url")
        // ':' and '/' in redirect_uri must be percent-encoded
        assertContains(
            url,
            "redirect_uri=myapp%3A%2F%2Ftest.auth0.com%2Fandroid%2Fcom.example%2Fcallback"
        )
    }

    @Test
    fun omits_optional_params_when_not_set() {
        val url = buildAuthorizeUrl(account, transaction(), LoginOptions())
        assertTrue(!url.contains("audience="), "url: $url")
        assertTrue(!url.contains("connection="), "url: $url")
        assertTrue(!url.contains("organization="), "url: $url")
        assertTrue(!url.contains("prompt="), "url: $url")
        assertTrue(!url.contains("max_age="), "url: $url")
    }

    @Test
    fun includes_optional_params_when_set() {
        val url = buildAuthorizeUrl(
            account,
            transaction(),
            LoginOptions(
                audience = "https://api.example.com",
                connection = "google-oauth2",
                organization = "org_123",
                prompt = "login",
                maxAge = 3600,
            ),
        )
        assertContains(url, "audience=https%3A%2F%2Fapi.example.com")
        assertContains(url, "connection=google-oauth2")
        assertContains(url, "organization=org_123")
        assertContains(url, "prompt=login")
        assertContains(url, "max_age=3600")
    }

    @Test
    fun appends_extra_parameters() {
        val url = buildAuthorizeUrl(
            account,
            transaction(),
            LoginOptions(extraParameters = mapOf("ui_locales" to "en", "login_hint" to "a@b.com")),
        )
        assertContains(url, "ui_locales=en")
        assertContains(url, "login_hint=a%40b.com")
    }

    @Test
    fun appends_dpop_jkt_when_provided() {
        val url =
            buildAuthorizeUrl(account, transaction(), LoginOptions(), dpopJkt = "the-thumbprint")
        assertContains(url, "dpop_jkt=the-thumbprint")
    }

    @Test
    fun omits_dpop_jkt_when_not_provided() {
        val url = buildAuthorizeUrl(account, transaction(), LoginOptions())
        assertTrue(!url.contains("dpop_jkt="), "url: $url")
    }

    @Test
    fun extra_parameters_cannot_override_sdk_params() {
        val txn = transaction()
        val forged = mapOf(
            "response_type" to "token",
            "client_id" to "attacker",
            "redirect_uri" to "myapp://evil",
            "scope" to "openid admin",
            "state" to "forged",
            "nonce" to "forged",
            "code_challenge" to "forged",
            "code_challenge_method" to "plain",
            "dpop_jkt" to "attacker-key",
            "ui_locales" to "en", // non-reserved control — must pass through
        )
        val url = buildAuthorizeUrl(
            account,
            txn,
            LoginOptions(scope = "openid", extraParameters = forged),
            dpopJkt = "the-thumbprint", // DPoP-on: real dpop_jkt collision
        )
        assertContains(url, "response_type=code")
        assertContains(url, "client_id=cid")
        assertContains(url, "state=the-state")
        assertContains(url, "nonce=the-nonce")
        assertContains(url, "code_challenge=${txn.pkce.codeChallenge}")
        assertContains(url, "code_challenge_method=S256")
        assertContains(url, "dpop_jkt=the-thumbprint")

        assertTrue(!url.contains("response_type=token"), "url: $url")
        assertTrue(!url.contains("client_id=attacker"), "url: $url")
        assertTrue(!url.contains("state=forged"), "url: $url")
        assertTrue(!url.contains("nonce=forged"), "url: $url")
        assertTrue(!url.contains("code_challenge=forged"), "url: $url")
        assertTrue(!url.contains("code_challenge_method=plain"), "url: $url")
        // non-reserved still passes through
        assertContains(url, "ui_locales=en")
    }
}
