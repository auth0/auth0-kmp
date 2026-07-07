package com.auth0.kmp.webauth.browser

import com.auth0.kmp.core.Auth0Account
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AndroidBrowserAgentTest {

    private val account = Auth0Account(clientId = "cid", domain = "tenant.auth0.com")

    private val agent = AndroidBrowserAgent()

    @Test
    fun buildRedirectUri_withExplicitScheme_usesIt() {
        val uri = agent.buildAndroidRedirectUri(
            account,
            scheme = "myscheme",
            packageName = "com.example.app"
        )
        assertEquals("myscheme://tenant.auth0.com/android/com.example.app/callback", uri)
    }

    @Test
    fun buildRedirectUri_withNullScheme_defaultsToPackageName() {
        val uri =
            agent.buildAndroidRedirectUri(account, scheme = null, packageName = "com.example.app")
        assertEquals("com.example.app://tenant.auth0.com/android/com.example.app/callback", uri)
    }

    @Test
    fun buildRedirectUri_hasExpectedStructure() {
        val uri = agent.buildAndroidRedirectUri(
            account,
            scheme = "myscheme",
            packageName = "com.example.app"
        )
        assertTrue(uri.startsWith("myscheme://tenant.auth0.com/android/"), "was: $uri")
        assertTrue(uri.endsWith("/callback"), "was: $uri")
    }
}
