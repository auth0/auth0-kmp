package com.auth0.kmp.networking.transport

import com.auth0.kmp.core.NetworkingConfiguration
import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.dpop.DPoPCollaborators
import com.auth0.kmp.core.dpop.DPoPNonceStore
import com.auth0.kmp.core.dpop.DPoPProofGenerator
import com.auth0.kmp.core.dpop.FakeDPoPKeyStore
import com.auth0.kmp.core.primitives.decodeBase64Url
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(InternalAuth0Api::class)
class DPoPPluginTest {

    private val tokenUrl = "https://example.auth0.com/oauth/token"

    private fun collaborators(
        store: FakeDPoPKeyStore = FakeDPoPKeyStore(),
    ): Pair<DPoPCollaborators, DPoPNonceStore> {
        val nonceStore = DPoPNonceStore()
        val collaborators = DPoPCollaborators(
            proofGenerator = DPoPProofGenerator(store),
            nonceStore = nonceStore,
            keygenLock = Mutex(),
        )
        return collaborators to nonceStore
    }

    private fun sequentialEngine(vararg handlers: MockRequestHandler): MockEngine =
        MockEngine(
            MockEngineConfig().apply {
                reuseHandlers = false
                handlers.forEach { addHandler(it) }
            },
        )

    private fun clientWith(collaborators: DPoPCollaborators, engine: MockEngine): HttpClient =
        HttpClient(engine) {
            // Long.MAX_VALUE is Ktor's INFINITE_TIMEOUT_MS sentinel; without it runTest's
            // virtual clock auto-advances to the timeout before the MockEngine resolves.
            applyNetworkingConfig(
                NetworkingConfiguration(requestTimeoutMillis = Long.MAX_VALUE),
                collaborators,
            )
        }

    private fun payloadOf(proof: String): JsonObject =
        Json.parseToJsonElement(proof.split(".")[1].decodeBase64Url().decodeToString()).jsonObject

    private suspend fun HttpClient.postToken() {
        request(tokenUrl) {
            method = HttpMethod.Post
            setBody(TextContent("""{"grant_type":"authorization_code"}""", ContentType.Application.Json))
        }
    }

    @Test
    fun attaches_dpop_header_on_qualifying_token_request() = runTest {
        val (collaborators, _) = collaborators()
        val engine = sequentialEngine({ respond("{}", HttpStatusCode.OK) })

        clientWith(collaborators, engine).postToken()

        assertNotNull(engine.requestHistory.single().headers["DPoP"])
    }

    @Test
    fun does_not_attach_dpop_header_when_no_keypair_and_not_token_endpoint() = runTest {
        val (collaborators, _) = collaborators(FakeDPoPKeyStore(hasKey = false))
        val engine = sequentialEngine({ respond("{}", HttpStatusCode.OK) })

        clientWith(collaborators, engine).request("https://example.auth0.com/userinfo") {
            method = HttpMethod.Get
        }

        assertNull(engine.requestHistory.single().headers["DPoP"])
    }

    @Test
    fun regenerates_and_resends_once_on_400_nonce_challenge() = runTest {
        val (collaborators, _) = collaborators()
        val engine = sequentialEngine(
            {
                respond(
                    """{"error":"use_dpop_nonce"}""",
                    HttpStatusCode.BadRequest,
                    headersOf("DPoP-Nonce", "nonce-1"),
                )
            },
            { respond("{}", HttpStatusCode.OK) },
        )

        clientWith(collaborators, engine).postToken()

        assertEquals(2, engine.requestHistory.size)
        val first = payloadOf(engine.requestHistory[0].headers["DPoP"]!!)
        val second = payloadOf(engine.requestHistory[1].headers["DPoP"]!!)
        assertNull(first["nonce"])
        assertEquals("nonce-1", second["nonce"]!!.jsonPrimitive.content)
        assertNotEquals(
            first["jti"]!!.jsonPrimitive.content,
            second["jti"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun regenerates_and_resends_once_on_401_www_authenticate_challenge() = runTest {
        val (collaborators, _) = collaborators()
        val engine = sequentialEngine(
            {
                respond(
                    "",
                    HttpStatusCode.Unauthorized,
                    Headers.build {
                        append("WWW-Authenticate", """DPoP error="use_dpop_nonce"""")
                        append("DPoP-Nonce", "nonce-1")
                    },
                )
            },
            { respond("{}", HttpStatusCode.OK) },
        )

        clientWith(collaborators, engine).request("https://example.auth0.com/userinfo") {
            method = HttpMethod.Get
        }

        assertEquals(2, engine.requestHistory.size)
        val second = payloadOf(engine.requestHistory[1].headers["DPoP"]!!)
        assertEquals("nonce-1", second["nonce"]!!.jsonPrimitive.content)
    }

    @Test
    fun does_not_retry_more_than_once_on_repeated_challenge() = runTest {
        val (collaborators, _) = collaborators()
        val engine = sequentialEngine(
            {
                respond(
                    """{"error":"use_dpop_nonce"}""",
                    HttpStatusCode.BadRequest,
                    headersOf("DPoP-Nonce", "nonce-1"),
                )
            },
            {
                respond(
                    """{"error":"use_dpop_nonce"}""",
                    HttpStatusCode.BadRequest,
                    headersOf("DPoP-Nonce", "nonce-2"),
                )
            },
        )

        clientWith(collaborators, engine).postToken()

        assertEquals(2, engine.requestHistory.size)
    }

    @Test
    fun stores_nonce_from_success_response_for_the_next_request() = runTest {
        val (collaborators, _) = collaborators()
        val engine = sequentialEngine(
            { respond("{}", HttpStatusCode.OK, headersOf("DPoP-Nonce", "nonce-1")) },
            { respond("{}", HttpStatusCode.OK) },
        )
        val client = clientWith(collaborators, engine)

        client.postToken()
        client.postToken()

        assertNull(payloadOf(engine.requestHistory[0].headers["DPoP"]!!)["nonce"])
        assertEquals(
            "nonce-1",
            payloadOf(engine.requestHistory[1].headers["DPoP"]!!)["nonce"]!!.jsonPrimitive.content,
        )
    }
}
