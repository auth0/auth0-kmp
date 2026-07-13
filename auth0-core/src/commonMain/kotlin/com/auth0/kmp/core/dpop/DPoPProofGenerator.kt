package com.auth0.kmp.core.dpop

import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.primitives.encodeBase64Url
import com.auth0.kmp.core.primitives.sha256
import com.auth0.kmp.core.result.Result
import io.ktor.http.Url
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Builds DPoP proof JWTs (RFC 9449) from the DPoP keypair.
 *
 * The generator holds no state of its own; a single instance is meant to be reused for the
 * lifetime of an account. The create-if-absent keypair path ([jkt] and [generate]) is not safe
 * against concurrent first-time calls on the same account and must be serialized by the caller;
 * [shouldGenerateProof] only inspects the store and is safe to call unsynchronized.
 */
@InternalAuth0Api
public class DPoPProofGenerator(
    private val keyStore: DPoPKeyStore,
    private val clock: Clock = Clock.System,
) {

    /**
     * Whether a request should carry a DPoP proof.
     *
     * A token-endpoint exchange other than a refresh grant always warrants a proof: it
     * binds the freshly issued token to the keypair, creating the keypair if needed.
     * Every other request is proofed only when a keypair already exists.
     */
    public fun shouldGenerateProof(url: String, grantType: String?): Result<Boolean, DPoPError> =
        runCatching {
            if (isTokenEndpoint(url) && grantType != null && grantType != REFRESH_TOKEN_GRANT) {
                true
            } else {
                keyStore.hasKey()
            }
        }.toDPoPResult()

    /** The JWK thumbprint (`jkt`) of the DPoP public key, creating the keypair if needed. */
    public fun jkt(): Result<String, DPoPError> =
        runCatching { keyStore.publicJwk().thumbprint() }.toDPoPResult()

    /**
     * Generates a DPoP proof for the given request, creating the keypair if needed.
     *
     * @param url the target request URL; its query and fragment are stripped for the `htu` claim.
     * @param method the HTTP method, uppercased for the `htm` claim.
     * @param nonce a server-supplied nonce to echo in the proof, if any.
     * @param accessToken the access token to bind via the `ath` claim, if any.
     */
    @OptIn(ExperimentalUuidApi::class)
    public fun generate(
        url: String,
        method: String,
        nonce: String? = null,
        accessToken: String? = null,
    ): Result<String, DPoPError> {
        val htu = canonicalHtu(url) ?: return Result.Failure(DPoPError.MalformedUrl)

        return runCatching {
            val jwk = keyStore.publicJwk()

            val header = buildJsonObject {
                put("typ", "dpop+jwt")
                put("alg", "ES256")
                put("jwk", buildJsonObject {
                    put("crv", jwk.crv)
                    put("kty", jwk.kty)
                    put("x", jwk.x)
                    put("y", jwk.y)
                })
            }
            val payload = buildJsonObject {
                put("jti", Uuid.random().toString())
                put("htm", method.uppercase())
                put("htu", htu)
                put("iat", clock.now().epochSeconds)
                nonce?.let { put("nonce", it) }
                accessToken?.let { put("ath", it.encodeToByteArray().sha256().encodeBase64Url()) }
            }

            val signingInput = header.encodeSegment() + "." + payload.encodeSegment()
            val signature = keyStore.sign(signingInput.encodeToByteArray()).encodeBase64Url()
            "$signingInput.$signature"
        }.toDPoPResult()
    }

    private fun JsonObject.encodeSegment(): String =
        toString().encodeToByteArray().encodeBase64Url()

    private fun canonicalHtu(url: String): String? =
        runCatching {
            // The htu must be an absolute URI without query or fragment (RFC 9449 §4.2).
            val parsed = Url(url)
            if (parsed.host.isBlank() || !url.startsWith("${parsed.protocol.name}://", ignoreCase = true)) {
                return null
            }
            // Scheme and host are case-insensitive (RFC 3986 §3.2.2); Ktor lowercases the scheme but
            // not the host, so we lowercase the host to match native and the server's reconstruction.
            // The path is case-sensitive and is left untouched.
            val portSuffix = if (parsed.port == parsed.protocol.defaultPort) "" else ":${parsed.port}"
            "${parsed.protocol.name}://${parsed.host.lowercase()}$portSuffix${parsed.encodedPath}"
        }.getOrNull()

    private fun isTokenEndpoint(url: String): Boolean =
        runCatching { Url(url).encodedPath.trimEnd('/').endsWith("/oauth/token") }.getOrDefault(false)

    private companion object {
        private const val REFRESH_TOKEN_GRANT = "refresh_token"
    }
}

/**
 * A throwable wrapper that carries a [DPoPError] out of a [DPoPKeyStore] `actual`.
 *
 * Keystore implementations throw this so the common engine can surface the precise
 * failure case; any other throwable maps to [DPoPError.Unknown].
 */
internal class DPoPException(val error: DPoPError) : Exception()

private fun <T> kotlin.Result<T>.toDPoPResult(): Result<T, DPoPError> =
    fold(
        onSuccess = { Result.Success(it) },
        onFailure = { cause ->
            Result.Failure(if (cause is DPoPException) cause.error else DPoPError.Unknown(cause))
        },
    )
