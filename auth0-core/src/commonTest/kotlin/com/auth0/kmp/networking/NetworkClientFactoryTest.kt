package com.auth0.kmp.networking

import com.auth0.kmp.core.Auth0Account
import com.auth0.kmp.core.NetworkingConfiguration
import com.auth0.kmp.core.useragent.UserAgent
import com.auth0.kmp.networking.request.HttpMethod
import com.auth0.kmp.networking.request.NetworkRequest
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkClientFactoryTest {

    private class FakeUserAgent(
        override val headerName: String = "Auth0-Client",
        override val value: String = "fake-ua-value",
    ) : UserAgent

    // Routes the real networkClient() factory through a MockEngine so the test
    // can read the headers actually placed on the outgoing request.
    private class CapturingEngine {
        var headers: Headers? = null
        private val mock = MockEngine { req ->
            headers = req.headers
            respond(content = "ok", status = HttpStatusCode.OK)
        }
        val factory = object : HttpClientEngineFactory<MockEngineConfig> {
            override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine = mock
        }
    }

    // Long.MAX_VALUE is Ktor's INFINITE_TIMEOUT_MS sentinel: it stops HttpTimeout
    // from launching a timeout coroutine that runTest's virtual clock would
    // otherwise auto-advance to before the MockEngine responds.
    private fun account(defaultHeaders: Map<String, String> = emptyMap()) =
        Auth0Account(
            clientId = "client",
            domain = "example.auth0.com",
            configuration = NetworkingConfiguration(
                requestTimeoutMillis = Long.MAX_VALUE,
                defaultHeaders = defaultHeaders,
            ),
        )

    @Test
    fun outgoing_request_carries_userAgent_header() = runTest {
        val capturing = CapturingEngine()
        val client = networkClient(account(), FakeUserAgent(), capturing.factory)

        client.request(NetworkRequest(HttpMethod.GET, "/x")) { it }

        assertEquals("fake-ua-value", capturing.headers!!["Auth0-Client"])
    }

    @Test
    fun sdk_userAgent_overrides_caller_supplied_header() = runTest {
        val capturing = CapturingEngine()
        val client = networkClient(
            account(defaultHeaders = mapOf("Auth0-Client" to "caller-spoofed")),
            FakeUserAgent(value = "sdk-identity"),
            capturing.factory,
        )

        client.request(NetworkRequest(HttpMethod.GET, "/x")) { it }

        assertEquals("sdk-identity", capturing.headers!!["Auth0-Client"])
    }
}
