package com.auth0.kmp.networking.transport

import com.auth0.kmp.core.error.TransportError
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.networking.request.HttpMethod
import com.auth0.kmp.networking.request.NetworkRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SafeCallTest {

    private val url = "https://example.auth0.com/oauth/token"

    private fun respondingClient(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = "",
    ) = HttpClient(MockEngine { respond(content = body, status = status) })

    private fun throwingClient(error: Throwable) =
        HttpClient(MockEngine { throw error })

    private fun request(
        method: HttpMethod = HttpMethod.GET,
        path: String = "/oauth/token",
        headers: Map<String, String> = emptyMap(),
        query: Map<String, String> = emptyMap(),
        body: String? = null,
    ) = NetworkRequest(method, path, headers, query, body)

    @Test
    fun success_deserializesBody_on200() = runTest {
        val client = respondingClient(HttpStatusCode.OK, """{"token":"abc"}""")

        val result = safeCall(client, url, request()) { it }

        assertEquals(Result.Success("""{"token":"abc"}"""), result)
    }

    @Test
    fun success_on299_isStillSuccess() = runTest {
        val client = respondingClient(HttpStatusCode(299, "Custom"), "ok")

        val result = safeCall(client, url, request()) { it }

        assertEquals(Result.Success("ok"), result)
    }

    @Test
    fun mapsServerWithBody_on401() = runTest {
        val client = respondingClient(HttpStatusCode.Unauthorized, "nope")

        val result = safeCall(client, url, request()) { it }

        assertEquals(Result.Failure(TransportError.Server(401, "nope")), result)
    }

    @Test
    fun mapsServerWithBody_on403() = runTest {
        val client = respondingClient(HttpStatusCode.Forbidden, "nope")

        val result = safeCall(client, url, request()) { it }

        assertEquals(Result.Failure(TransportError.Server(403, "nope")), result)
    }

    @Test
    fun mapsServer_onOther4xx_withStatusAndBody() = runTest {
        val client = respondingClient(HttpStatusCode.BadRequest, "bad input")

        val result = safeCall(client, url, request()) { it }

        assertEquals(Result.Failure(TransportError.Server(400, "bad input")), result)
    }

    @Test
    fun mapsServer_on5xx_withStatusAndBody() = runTest {
        val client = respondingClient(HttpStatusCode.InternalServerError, "boom")

        val result = safeCall(client, url, request()) { it }

        assertEquals(Result.Failure(TransportError.Server(500, "boom")), result)
    }

    @Test
    fun mapsSerialization_whenDeserializeThrows() = runTest {
        val client = respondingClient(HttpStatusCode.OK, "not json")

        val result = safeCall(client, url, request()) {
            throw SerializationException("bad field")
        }

        assertEquals(Result.Failure(TransportError.Serialization("bad field")), result)
    }

    @Test
    fun mapsTimeout_onRequestTimeout() = runTest {
        val client = throwingClient(HttpRequestTimeoutException(url, 1000L))

        val result = safeCall(client, url, request()) { it }

        assertEquals(Result.Failure(TransportError.Timeout), result)
    }

    @Test
    fun mapsTimeout_onConnectTimeout() = runTest {
        val client = throwingClient(ConnectTimeoutException("connect timed out"))

        val result = safeCall(client, url, request()) { it }

        assertEquals(Result.Failure(TransportError.Timeout), result)
    }

    @Test
    fun mapsTimeout_onSocketTimeout() = runTest {
        val client = throwingClient(SocketTimeoutException("socket timed out"))

        val result = safeCall(client, url, request()) { it }

        assertEquals(Result.Failure(TransportError.Timeout), result)
    }

    @Test
    fun mapsNoInternet_onIOException() = runTest {
        val client = throwingClient(kotlinx.io.IOException("no route to host"))

        val result = safeCall(client, url, request()) { it }

        assertEquals(Result.Failure(TransportError.NoInternet), result)
    }

    @Test
    fun mapsUnknown_onUnexpectedThrowable() = runTest {
        val client = throwingClient(IllegalStateException("weird"))

        val result = safeCall(client, url, request()) { it }

        assertEquals(Result.Failure(TransportError.Unknown("weird")), result)
    }

    @Test
    fun rethrowsCancellation() = runTest {
        val client = throwingClient(CancellationException("cancelled"))

        assertFailsWith<CancellationException> {
            safeCall(client, url, request()) { it }
        }
    }

    @Test
    fun sendsMethodHeadersQueryAndBody() = runTest {
        var captured: HttpRequestData? = null
        val client = HttpClient(MockEngine { req ->
            captured = req
            respond(content = "ok", status = HttpStatusCode.OK)
        })

        safeCall(
            client,
            url,
            request(
                method = HttpMethod.POST,
                headers = mapOf("X-Custom" to "value"),
                query = mapOf("audience" to "api"),
                body = """{"grant":"x"}""",
            ),
        ) { it }

        val sent = captured!!
        assertEquals("POST", sent.method.value)
        assertEquals("value", sent.headers["X-Custom"])
        assertEquals("api", sent.url.parameters["audience"])
        assertEquals("""{"grant":"x"}""", (sent.body as TextContent).text)
    }

    @Test
    fun omitsBody_whenRequestBodyNull() = runTest {
        var captured: HttpRequestData? = null
        val client = HttpClient(MockEngine { req ->
            captured = req
            respond(content = "ok", status = HttpStatusCode.OK)
        })

        safeCall(client, url, request(method = HttpMethod.GET, body = null)) { it }

        assertTrue((captured!!.body as? TextContent) == null)
    }
}
