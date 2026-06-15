package com.auth0.kmp.networking.transport

import com.auth0.kmp.core.Auth0Account
import com.auth0.kmp.core.error.NetworkError
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.networking.request.HttpMethod
import com.auth0.kmp.networking.request.NetworkRequest
import com.auth0.kmp.networking.retry.Backoff
import com.auth0.kmp.networking.retry.RetryPolicy
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration

class DefaultNetworkClientTest {

    private val account = Auth0Account(clientId = "client", domain = "example.auth0.com")

    private fun retry(maxAttempts: Int) = RetryPolicy(
        maxAttempts = maxAttempts,
        backoff = Backoff.Fixed(Duration.ZERO),
        retryOn = { true },
    )

    @Test
    fun resolvesRequestPath_againstAccountDomain() = runTest {
        val hitUrls = mutableListOf<String>()
        val client = HttpClient(MockEngine { req ->
            hitUrls.add(req.url.toString())
            respond(content = "ok", status = HttpStatusCode.OK)
        })
        val networkClient = DefaultNetworkClient(client, EndpointResolver(account))

        networkClient.request(
            NetworkRequest(HttpMethod.POST, "/oauth/token"),
            RetryPolicy.None,
        ) { it }

        assertEquals(listOf("https://example.auth0.com/oauth/token"), hitUrls)
    }

    @Test
    fun returnsDeserializedSuccess() = runTest {
        val client = HttpClient(MockEngine { respond(content = "raw-body", status = HttpStatusCode.OK) })
        val networkClient = DefaultNetworkClient(client, EndpointResolver(account))

        val result = networkClient.request(
            NetworkRequest(HttpMethod.GET, "/userinfo"),
            RetryPolicy.None,
        ) { "decoded:$it" }

        assertEquals(Result.Success("decoded:raw-body"), result)
    }

    @Test
    fun appliesRetryPolicy_retryingUntilBudgetExhausted() = runTest {
        var attempts = 0
        val client = HttpClient(MockEngine {
            attempts++
            respond(content = "boom", status = HttpStatusCode.InternalServerError)
        })
        val networkClient = DefaultNetworkClient(client, EndpointResolver(account))

        val result = networkClient.request(
            NetworkRequest(HttpMethod.GET, "/userinfo"),
            retry(maxAttempts = 3),
        ) { it }

        assertEquals(3, attempts)
        assertEquals(Result.Failure(NetworkError.Server(500, "boom")), result)
    }

    @Test
    fun appliesRetryPolicy_stopsRetryingOnSuccess() = runTest {
        var attempts = 0
        val client = HttpClient(MockEngine {
            attempts++
            if (attempts < 2) respond(content = "boom", status = HttpStatusCode.InternalServerError)
            else respond(content = "ok", status = HttpStatusCode.OK)
        })
        val networkClient = DefaultNetworkClient(client, EndpointResolver(account))

        val result = networkClient.request(
            NetworkRequest(HttpMethod.GET, "/userinfo"),
            retry(maxAttempts = 5),
        ) { it }

        assertEquals(2, attempts)
        assertEquals(Result.Success("ok"), result)
    }

    @Test
    fun close_closesTheUnderlyingHttpClient() {
        val client = HttpClient(MockEngine { respond(content = "ok", status = HttpStatusCode.OK) })
        val networkClient = DefaultNetworkClient(client, EndpointResolver(account))

        assertTrue(client.isActive)
        networkClient.close()
        assertFalse(client.isActive)
    }
}
