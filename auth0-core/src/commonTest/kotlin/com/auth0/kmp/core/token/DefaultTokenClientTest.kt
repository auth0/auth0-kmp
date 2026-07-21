package com.auth0.kmp.core.token

import com.auth0.kmp.core.error.TransportError
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.networking.NetworkClient
import com.auth0.kmp.networking.request.HttpMethod
import com.auth0.kmp.networking.request.NetworkRequest
import com.auth0.kmp.networking.retry.Backoff
import com.auth0.kmp.networking.retry.RetryPolicy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

private fun tokenJson(): String =
    """{"access_token":"at","id_token":"it","token_type":"Bearer","expires_in":3600,"refresh_token":"rt","scope":"openid"}"""

private class FixedClock(private val at: Instant) : Clock {
    override fun now(): Instant = at
}

private class FakeNetworkClient(
    private val outcome: Result<String, TransportError>,
) : NetworkClient {
    var lastRequest: NetworkRequest? = null
    var lastRetryPolicy: RetryPolicy? = null
    override suspend fun <T> request(
        request: NetworkRequest,
        retryPolicy: RetryPolicy,
        deserialize: (String) -> T,
    ): Result<T, TransportError> {
        lastRequest = request
        lastRetryPolicy = retryPolicy
        return when (outcome) {
            is Result.Success -> Result.Success(deserialize(outcome.data))
            is Result.Failure -> outcome
        }
    }
    override fun close() {}
}

private class StubGrant(override val parameters: JsonObject) : TokenGrant

@OptIn(kotlin.time.ExperimentalTime::class)
class DefaultTokenClientTest {

    @Test
    fun posts_grant_parameters_as_json_body_to_oauth_token() = runTest {
        val net = FakeNetworkClient(Result.Success(tokenJson()))
        val client = DefaultTokenClient(net, FixedClock(Instant.fromEpochSeconds(1_000)))

        client.fetchToken(StubGrant(buildJsonObject { put("grant_type", "refresh_token"); put("client_id", "cid") }))

        val req = net.lastRequest!!
        assertEquals(HttpMethod.POST, req.method)
        assertEquals("/oauth/token", req.path)
        assertTrue(req.body!!.contains(""""grant_type":"refresh_token""""))
        assertTrue(req.body.contains(""""client_id":"cid""""))
    }

    @Test
    fun maps_success_body_to_credentials_with_clock_expiry() = runTest {
        val net = FakeNetworkClient(Result.Success(tokenJson()))
        val client = DefaultTokenClient(net, FixedClock(Instant.fromEpochSeconds(1_000)))

        val result = client.fetchToken(StubGrant(buildJsonObject { }))

        result as Result.Success
        assertEquals("at", result.data.accessToken)
        assertEquals("rt", result.data.refreshToken)
        assertEquals(Instant.fromEpochSeconds(1_000 + 3600), result.data.expiresAt)
    }

    @Test
    fun forwards_headers_to_the_request() = runTest {
        val net = FakeNetworkClient(Result.Success(tokenJson()))
        val client = DefaultTokenClient(net, FixedClock(Instant.fromEpochSeconds(0)))

        client.fetchToken(StubGrant(buildJsonObject { }), headers = mapOf("X-Test" to "1"))

        assertEquals("1", net.lastRequest!!.headers["X-Test"])
    }

    @Test
    fun propagates_transport_failure() = runTest {
        val net = FakeNetworkClient(Result.Failure(TransportError.NoInternet))
        val client = DefaultTokenClient(net, FixedClock(Instant.fromEpochSeconds(0)))

        val result = client.fetchToken(StubGrant(buildJsonObject { }))

        result as Result.Failure<TransportError>
        assertEquals(TransportError.NoInternet, result.error)
    }

    @Test
    fun forwards_retryPolicy_to_networkClient() = runTest {
        val net = FakeNetworkClient(Result.Success(tokenJson()))
        val client = DefaultTokenClient(net, FixedClock(Instant.fromEpochSeconds(0)))
        val policy = RetryPolicy(
            maxAttempts = 3,
            backoff = Backoff.Fixed(Duration.ZERO),
            retryOn = { true },
        )

        client.fetchToken(StubGrant(buildJsonObject { }), retryPolicy = policy)

        assertSame(policy, net.lastRetryPolicy)
    }
}
