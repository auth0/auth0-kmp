package com.auth0.kmp.webauth.jwks

import com.auth0.kmp.core.error.TransportError
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.networking.NetworkClient
import com.auth0.kmp.networking.request.HttpMethod
import com.auth0.kmp.networking.request.NetworkRequest
import com.auth0.kmp.networking.retry.RetryPolicy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun jwksJson(vararg keys: String): String =
    """{"keys":[${keys.joinToString(",")}]}"""

private fun rsaKey(kid: String, n: String = "modulus-$kid", e: String = "AQAB"): String =
    """{"kty":"RSA","kid":"$kid","n":"$n","e":"$e","use":"sig"}"""

private class FakeNetworkClient(
    var outcome: Result<String, TransportError>,
) : NetworkClient {
    var requestCount = 0
        private set
    var lastRequest: NetworkRequest? = null

    override suspend fun <T> request(
        request: NetworkRequest,
        retryPolicy: RetryPolicy,
        deserialize: (String) -> T,
    ): Result<T, TransportError> {
        requestCount++
        lastRequest = request
        return when (val o = outcome) {
            is Result.Success -> Result.Success(deserialize(o.data))
            is Result.Failure -> o
        }
    }

    override fun close() {}
}

class DefaultJwksProviderTest {

    @Test
    fun fetch_returns_matching_key_mapping_n_and_e() = runTest {
        val net = FakeNetworkClient(Result.Success(jwksJson(rsaKey("key-1", n = "abc", e = "AQAB"))))
        val provider = DefaultJwksProvider(net)

        val jwk = provider.fetch("key-1")

        assertEquals(Jwk(kid = "key-1", modulus = "abc", exponent = "AQAB"), jwk)
    }

    @Test
    fun fetch_issues_get_to_well_known_jwks() = runTest {
        val net = FakeNetworkClient(Result.Success(jwksJson(rsaKey("key-1"))))

        DefaultJwksProvider(net).fetch("key-1")

        val request = net.lastRequest!!
        assertEquals(HttpMethod.GET, request.method)
        assertEquals("/.well-known/jwks.json", request.path)
    }

    @Test
    fun second_fetch_of_cached_kid_does_not_refetch() = runTest {
        val net = FakeNetworkClient(Result.Success(jwksJson(rsaKey("key-1"))))
        val provider = DefaultJwksProvider(net)

        provider.fetch("key-1")
        provider.fetch("key-1")

        assertEquals(1, net.requestCount)
    }

    @Test
    fun cache_miss_refetches_and_finds_rotated_in_key() = runTest {
        val net = FakeNetworkClient(Result.Success(jwksJson(rsaKey("old"))))
        val provider = DefaultJwksProvider(net)

        assertNull(provider.fetch("new"))

        net.outcome = Result.Success(jwksJson(rsaKey("old"), rsaKey("new", n = "rotated")))
        val jwk = provider.fetch("new")

        assertEquals(Jwk(kid = "new", modulus = "rotated", exponent = "AQAB"), jwk)
        assertEquals(2, net.requestCount)
    }

    @Test
    fun unknown_kid_returns_null() = runTest {
        val net = FakeNetworkClient(Result.Success(jwksJson(rsaKey("key-1"))))

        assertNull(DefaultJwksProvider(net).fetch("absent"))
    }

    @Test
    fun transport_failure_returns_null() = runTest {
        val net = FakeNetworkClient(Result.Failure(TransportError.NoInternet))

        assertNull(DefaultJwksProvider(net).fetch("key-1"))
    }

    @Test
    fun incomplete_key_entries_are_skipped() = runTest {
        val incomplete = """{"kty":"EC","kid":"ec-1","crv":"P-256"}"""
        val net = FakeNetworkClient(Result.Success(jwksJson(incomplete, rsaKey("rsa-1"))))
        val provider = DefaultJwksProvider(net)

        assertNull(provider.fetch("ec-1"))
        assertEquals(Jwk(kid = "rsa-1", modulus = "modulus-rsa-1", exponent = "AQAB"), provider.fetch("rsa-1"))
    }
}
