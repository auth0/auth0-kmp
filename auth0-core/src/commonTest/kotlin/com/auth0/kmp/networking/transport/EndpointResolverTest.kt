package com.auth0.kmp.networking.transport

import com.auth0.kmp.core.Auth0Account
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EndpointResolverTest {

    private fun resolver(domain: String) =
        EndpointResolver(Auth0Account(clientId = "client", domain = domain))

    @Test
    fun resolve_prependsHttps_whenDomainHasNoScheme() {
        val resolved = resolver("example.auth0.com").resolve("/oauth/token")
        assertEquals("https://example.auth0.com/oauth/token", resolved)
    }

    @Test
    fun resolve_keepsHttps_whenDomainAlreadyHttps() {
        val resolved = resolver("https://example.auth0.com").resolve("/oauth/token")
        assertEquals("https://example.auth0.com/oauth/token", resolved)
    }

    @Test
    fun resolve_lowercasesDomain() {
        val resolved = resolver("Example.Auth0.COM").resolve("/userinfo")
        assertEquals("https://example.auth0.com/userinfo", resolved)
    }

    @Test
    fun resolve_isIdentical_forLeadingSlashAndWithout() {
        val withSlash = resolver("example.auth0.com").resolve("/oauth/token")
        val withoutSlash = resolver("example.auth0.com").resolve("oauth/token")
        assertEquals(withSlash, withoutSlash)
    }

    @Test
    fun resolve_collapsesTrailingSlash() {
        val resolved = resolver("example.auth0.com").resolve("/oauth/token/")
        assertEquals("https://example.auth0.com/oauth/token", resolved)
    }

    @Test
    fun baseUrl_isHostOnly_whenDomainHasNoScheme() {
        val resolver = resolver("example.auth0.com")
        assertEquals("https://example.auth0.com/", resolver.baseUrl)
    }

    @Test
    fun baseUrl_stripsPath_whenDomainContainsPath() {
        val resolver = resolver("example.auth0.com/some/path")
        assertEquals("https://example.auth0.com/", resolver.baseUrl)
        assertEquals("https://example.auth0.com/oauth/token", resolver.resolve("/oauth/token"))
    }

    @Test
    fun baseUrl_stripsPath_whenDomainHasHttpsAndPath() {
        val resolver = resolver("https://example.auth0.com/some/path")
        assertEquals("https://example.auth0.com/", resolver.baseUrl)
        assertEquals("https://example.auth0.com/oauth/token", resolver.resolve("/oauth/token"))
    }

    @Test
    fun construction_throws_whenDomainIsHttp() {
        assertFailsWith<IllegalArgumentException> { resolver("http://example.auth0.com") }
    }

    @Test
    fun construction_throws_whenDomainIsSchemeOnly() {
        assertFailsWith<IllegalArgumentException> { resolver("https://") }
    }

    @Test
    fun construction_throws_whenDomainIsBlank() {
        assertFailsWith<IllegalArgumentException> { resolver("") }
    }
}
