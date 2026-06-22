package com.auth0.kmp.networking.transport

import com.auth0.kmp.core.NetworkingConfiguration
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HttpClientFactoryTest {

    private class CapturingEngine {
        var headers: io.ktor.http.Headers? = null
        val engine = MockEngine { req ->
            headers = req.headers
            respond(content = "ok", status = HttpStatusCode.OK)
        }
    }

    private fun clientWithDefaults(defaults: Map<String, String>): Pair<HttpClient, CapturingEngine> {
        val capturing = CapturingEngine()
        val client = HttpClient(capturing.engine) {
            applyNetworkingConfig(NetworkingConfiguration(defaultHeaders = defaults))
        }
        return client to capturing
    }

    @Test
    fun defaultHeader_isSent_whenRequestDoesNotSetIt() = runTest {
        val (client, capturing) = clientWithDefaults(mapOf("X-Default" to "default-value"))

        client.request("https://example.auth0.com/x")

        assertEquals(listOf("default-value"), capturing.headers!!.getAll("X-Default"))
    }

    @Test
    fun requestHeader_overridesDefault_onSameKey() = runTest {
        val (client, capturing) = clientWithDefaults(mapOf("X-Dup" to "from-default"))

        client.request("https://example.auth0.com/x") {
            header("X-Dup", "from-request")
        }

        assertEquals(listOf("from-request"), capturing.headers!!.getAll("X-Dup"))
    }

    @Test
    fun distinctKeys_areBothSent() = runTest {
        val (client, capturing) = clientWithDefaults(mapOf("X-Default" to "default-value"))

        client.request("https://example.auth0.com/x") {
            header("X-Request", "request-value")
        }

        assertEquals(listOf("default-value"), capturing.headers!!.getAll("X-Default"))
        assertEquals(listOf("request-value"), capturing.headers!!.getAll("X-Request"))
    }

    @Test
    fun noDefaultHeaders_sendsOnlyRequestHeaders() = runTest {
        val (client, capturing) = clientWithDefaults(emptyMap())

        client.request("https://example.auth0.com/x") {
            header("X-Request", "request-value")
        }

        assertEquals(listOf("request-value"), capturing.headers!!.getAll("X-Request"))
        assertNull(capturing.headers!!.getAll("X-Default"))
    }
}
