package com.auth0.kmp.networking.transport

import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.dpop.DPoPNonceStore
import com.auth0.kmp.core.dpop.DPoPProofGenerator
import com.auth0.kmp.core.result.getOrNull
import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Configuration for [DPoPPlugin]: the per-account DPoP collaborators the plugin operates with.
 *
 * @property proofGenerator builds the DPoP proof for a qualifying request.
 * @property nonceStore holds the latest server-issued nonce for the account.
 * @property keygenLock serializes first-time keypair materialization around the proof calls.
 */
@OptIn(InternalAuth0Api::class)
internal class DPoPPluginConfig {
    lateinit var proofGenerator: DPoPProofGenerator
    lateinit var nonceStore: DPoPNonceStore
    lateinit var keygenLock: Mutex
}

/**
 * Attaches DPoP proofs (RFC 9449) to qualifying outgoing requests and performs the
 * `use_dpop_nonce` challenge handshake.
 *
 * Installed only for accounts that opted into DPoP. For each request it decides whether a
 * proof is warranted, attaches one, captures any server nonce, and on a nonce challenge
 * regenerates the proof with the fresh nonce and resends exactly once.
 */
@OptIn(InternalAuth0Api::class)
internal val DPoPPlugin = createClientPlugin("DPoPPlugin", ::DPoPPluginConfig) {
    val proofGenerator = pluginConfig.proofGenerator
    val nonceStore = pluginConfig.nonceStore
    val keygenLock = pluginConfig.keygenLock

    on(Send) { request ->
        val url = request.url.buildString()
        val method = request.method.value
        val grantType = request.grantType()

        val shouldProof = proofGenerator.shouldGenerateProof(url, grantType).getOrNull() ?: false

        if (!shouldProof) {
            return@on proceed(request).also { it.captureNonce(nonceStore) }
        }

        val accessToken = request.accessToken()
        val proof = keygenLock.withLock {
            proofGenerator.generate(url, method, nonceStore.current(), accessToken).getOrNull()
        }
        if (proof == null) {
            return@on proceed(request).also { it.captureNonce(nonceStore) }
        }
        request.setDPoP(proof)

        var call = proceed(request)
        call.captureNonce(nonceStore)

        if (call.isNonceChallenge()) {
            val retryProof =
                proofGenerator.generate(url, method, nonceStore.current(), accessToken).getOrNull()
            if (retryProof != null) {
                request.setDPoP(retryProof)
                call = proceed(request)
                call.captureNonce(nonceStore)
            }
        }
        call
    }
}

private fun HttpRequestBuilder.grantType(): String? =
    runCatching {
        val text = (body as? TextContent)?.text ?: return null
        json.parseToJsonElement(text).jsonObject["grant_type"]?.jsonPrimitive?.content
    }.getOrNull()

private fun HttpRequestBuilder.accessToken(): String? =
    headers[HttpHeaders.Authorization]?.substringAfterLast(' ')?.takeIf { it.isNotBlank() }

private fun HttpRequestBuilder.setDPoP(proof: String) {
    headers[DPOP_HEADER] = proof
}

private fun HttpClientCall.captureNonce(store: DPoPNonceStore) {
    response.headers[DPOP_NONCE_HEADER]?.let { store.update(it) }
}

private suspend fun HttpClientCall.isNonceChallenge(): Boolean =
    when (response.status.value) {
        400 -> bodyErrorIs(USE_DPOP_NONCE)
        401 -> wwwAuthenticateNonceRequired()
        else -> false
    }

private suspend fun HttpClientCall.bodyErrorIs(code: String): Boolean =
    runCatching {
        val body = response.bodyAsText()
        json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content == code
    }.getOrNull() ?: false

private fun HttpClientCall.wwwAuthenticateNonceRequired(): Boolean {
    val header = response.headers[HttpHeaders.WWWAuthenticate] ?: return false
    if (!header.contains(DPOP_SCHEME, ignoreCase = true)) return false
    val error = WWW_AUTH_ERROR.find(header)?.groupValues?.get(1)
    return error == USE_DPOP_NONCE
}

private const val DPOP_HEADER = "DPoP"
private const val DPOP_NONCE_HEADER = "DPoP-Nonce"
private const val DPOP_SCHEME = "DPoP"
private const val USE_DPOP_NONCE = "use_dpop_nonce"
private val WWW_AUTH_ERROR = Regex(
    """\berror\s*=\s*"?([A-Za-z0-9_-]+)"?""",
    RegexOption.IGNORE_CASE,
)
