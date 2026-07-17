package com.auth0.kmp.networking.transport

import com.auth0.kmp.core.NetworkLogLevel
import com.auth0.kmp.core.NetworkingConfiguration
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val TOKEN = "secret-access-token-value"
private const val BODY = """{"access_token":"$TOKEN","scope":"openid profile"}"""

private class CapturingLogger : Logger {
    private val lines = mutableListOf<String>()
    override fun log(message: String) {
        lines += message
    }

    val text: String get() = lines.joinToString("\n")
}

/**
 * Drives one request through a client configured with [config] and returns
 * everything the capturing logger saw. The response echoes a token-bearing JSON
 * body so a test can observe how much of the exchange reaches the log.
 */
private suspend fun capturedLog(config: NetworkingConfiguration): String {
    val logger = CapturingLogger()
    val engine = MockEngine {
        respond(
            content = BODY,
            status = HttpStatusCode.OK,
            headers = headersOf("Content-Type", "application/json"),
        )
    }
    val client = HttpClient(engine) {
        // Long.MAX_VALUE is Ktor's INFINITE_TIMEOUT_MS sentinel so HttpTimeout
        // never launches a timeout coroutine that runTest's virtual clock would
        // otherwise fast-forward into a spurious timeout.
        applyNetworkingConfig(
            config.copy(requestTimeoutMillis = Long.MAX_VALUE),
            logger = logger,
        )
    }
    client.request("https://example.auth0.com/oauth/token") {
        header("Authorization", "Bearer $TOKEN")
    }
    return logger.text
}

class NetworkLoggingTest {

    @Test
    fun none_logsNothing() = runTest {
        val log = capturedLog(NetworkingConfiguration(logLevel = NetworkLogLevel.NONE))
        assertTrue(log.isEmpty(), "expected no log output, got: $log")
    }

    @Test
    fun basic_logsStatusLine_butNotBody() = runTest {
        val log = capturedLog(NetworkingConfiguration(logLevel = NetworkLogLevel.BASIC))
        assertTrue(log.isNotEmpty(), "BASIC should log the request/response line")
        assertFalse(log.contains("scope"), "BASIC must not log the body: $log")
    }

    @Test
    fun body_logsWholeBody() = runTest {
        val log = capturedLog(NetworkingConfiguration(logLevel = NetworkLogLevel.BODY))
        assertTrue(log.contains("scope"), "BODY should log the response body: $log")
    }
}
