package com.auth0.kmp.myaccount

import com.auth0.kmp.core.RequestOptions
import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.myaccount.error.MyAccountError
import com.auth0.kmp.myaccount.model.PasskeyAuthenticationMethod
import com.auth0.kmp.myaccount.model.PasskeyEnrollmentChallenge
import com.auth0.kmp.myaccount.model.PublicKeyCredentials
import com.auth0.kmp.myaccount.response.PasskeyAuthenticationMethodResponse
import com.auth0.kmp.myaccount.response.PasskeyEnrollmentChallengeResponse
import com.auth0.kmp.myaccount.response.toPasskeyAuthenticationMethod
import com.auth0.kmp.myaccount.response.toPasskeyEnrollmentChallenge
import com.auth0.kmp.myaccount.request.toPasskeyEnrollmentCredentialRequest
import com.auth0.kmp.networking.NetworkClient
import com.auth0.kmp.networking.request.HttpMethod
import com.auth0.kmp.networking.request.NetworkRequest
import com.auth0.kmp.networking.transport.json
import io.ktor.http.decodeURLPart
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

@OptIn(InternalAuth0Api::class)
internal class DefaultMyAccountClient(
    private val accessToken: String,
    private val useDPoP: Boolean,
    private val networkClient: NetworkClient,
) : MyAccountClient {

    override suspend fun passkeyEnrollmentChallenge(
        userIdentityId: String?,
        connection: String?,
        options: RequestOptions,
    ): Result<PasskeyEnrollmentChallenge, MyAccountError> {
        val body = jsonBody(options) {
            put("type", "passkey")
            userIdentityId?.let { put("identity_user_id", it) }
            connection?.let { put("connection", it) }
        }

        val request = NetworkRequest(
            method = HttpMethod.POST,
            path = "/me/v1/authentication-methods",
            headers = authHeaders(options),
            body = body,
        )

        return networkClient.request(request, options.retryPolicy) { responseBody, headers ->
            val authenticationMethodId = headers.locationLastSegment()
                ?: throw MissingLocationHeaderException()
            json.decodeFromString<PasskeyEnrollmentChallengeResponse>(responseBody)
                .toPasskeyEnrollmentChallenge(authenticationMethodId)
        }.toMyAccountResult()
    }

    override suspend fun verifyPasskeyEnrollment(
        credential: PublicKeyCredentials,
        challenge: PasskeyEnrollmentChallenge,
        options: RequestOptions,
    ): Result<PasskeyAuthenticationMethod, MyAccountError> {
        val body = jsonBody(options) {
            put("auth_session", challenge.authSession)
            put("authn_response", json.encodeToJsonElement(credential.toPasskeyEnrollmentCredentialRequest()))
        }

        val request = NetworkRequest(
            method = HttpMethod.POST,
            path = "/me/v1/authentication-methods/${challenge.authenticationMethodId}/verify",
            headers = authHeaders(options),
            body = body,
        )

        return networkClient.request(request, options.retryPolicy) {
            json.decodeFromString<PasskeyAuthenticationMethodResponse>(it).toPasskeyAuthenticationMethod()
        }.toMyAccountResult()
    }

    private fun authHeaders(options: RequestOptions): Map<String, String> {
        val scheme = if (useDPoP) "DPoP" else "Bearer"
        return options.headers + ("Authorization" to "$scheme $accessToken")
    }

    private inline fun jsonBody(
        options: RequestOptions,
        build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ): String = json.encodeToString(
        buildJsonObject {
            options.parameters.forEach { (key, value) -> put(key, value) }
            build()
        },
    )
}

/**
 * Signals that a successful challenge response did not carry the `Location`
 * header the authentication method identifier is read from. Thrown inside the
 * deserialize lambda so it surfaces as [com.auth0.kmp.core.error.TransportError.Unknown]
 * and, in turn, [MyAccountError.Unknown] — never as an uncaught throw.
 */
internal class MissingLocationHeaderException :
    Exception("Authentication method ID not found in Location header.")

/**
 * Returns the last path segment of the `Location` header, URL-decoded, or `null`
 * when the header is absent or has no path. Header names are matched
 * case-insensitively.
 */
private fun Map<String, List<String>>.locationLastSegment(): String? =
    entries.firstOrNull { it.key.equals("Location", ignoreCase = true) }
        ?.value
        ?.firstOrNull()
        ?.substringAfterLast('/')
        ?.takeIf { it.isNotEmpty() }
        ?.decodeURLPart()
