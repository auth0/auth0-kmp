package com.auth0.kmp.myaccount

import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.error.TransportError
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.myaccount.model.AuthenticatorResponse
import com.auth0.kmp.myaccount.model.AuthnParamsPublicKey
import com.auth0.kmp.myaccount.model.AuthenticatorSelection
import com.auth0.kmp.myaccount.model.PasskeyEnrollmentChallenge
import com.auth0.kmp.myaccount.model.PasskeyUser
import com.auth0.kmp.myaccount.model.PubKeyCredParam
import com.auth0.kmp.myaccount.model.PublicKeyCredentials
import com.auth0.kmp.myaccount.model.RelyingParty
import com.auth0.kmp.networking.NetworkClient
import com.auth0.kmp.networking.request.NetworkRequest
import com.auth0.kmp.networking.retry.RetryPolicy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A [NetworkClient] test double that records the outgoing request and replays a
 * canned outcome, mirroring the real transport: the deserialize lambda runs
 * inside the same try/catch [com.auth0.kmp.networking.transport.SafeCall] uses,
 * so a decode failure surfaces as [TransportError.Serialization]/[TransportError.Unknown]
 * exactly as it would in production. Canned [headers] let tests exercise the
 * `Location`-header identifier path.
 */
@OptIn(InternalAuth0Api::class)
internal class RecordingNetworkClient(
    private val outcome: Result<String, TransportError>,
    private val headers: Map<String, List<String>> = emptyMap(),
) : NetworkClient {
    var lastRequest: NetworkRequest? = null
        private set
    var lastRetryPolicy: RetryPolicy? = null
        private set
    var callCount = 0
        private set

    override suspend fun <T> request(
        request: NetworkRequest,
        retryPolicy: RetryPolicy,
        deserialize: (String) -> T,
    ): Result<T, TransportError> = request(request, retryPolicy) { body, _ -> deserialize(body) }

    override suspend fun <T> request(
        request: NetworkRequest,
        retryPolicy: RetryPolicy,
        deserialize: (body: String, headers: Map<String, List<String>>) -> T,
    ): Result<T, TransportError> {
        callCount++
        lastRequest = request
        lastRetryPolicy = retryPolicy
        return when (outcome) {
            is Result.Success -> try {
                Result.Success(deserialize(outcome.data, headers))
            } catch (e: SerializationException) {
                Result.Failure(TransportError.Serialization(e.message ?: "deserialize failed"))
            } catch (e: Throwable) {
                Result.Failure(TransportError.Unknown(e.message))
            }
            is Result.Failure -> Result.Failure(outcome.error)
        }
    }

    override fun close() {}
}

@OptIn(InternalAuth0Api::class)
internal fun client(
    outcome: Result<String, TransportError>,
    headers: Map<String, List<String>> = emptyMap(),
    useDPoP: Boolean = false,
    accessToken: String = "test-token",
): Pair<DefaultMyAccountClient, RecordingNetworkClient> {
    val net = RecordingNetworkClient(outcome, headers)
    return DefaultMyAccountClient(accessToken, useDPoP, net) to net
}

/** A `Location` header pointing at the newly created authentication method [id]. */
internal fun location(id: String): Map<String, List<String>> =
    mapOf("Location" to listOf("/me/v1/authentication-methods/$id"))

internal fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content

internal fun bodyOf(request: NetworkRequest): JsonObject =
    Json.parseToJsonElement(request.body!!).jsonObject

// --- Challenge response fixtures (POST /authentication-methods) ---

internal fun totpChallengeJson(): String =
    """{"auth_session":"sess","barcode_uri":"otpauth://totp/x","manual_input_code":"ABCDEF"}"""

internal fun pushChallengeJson(): String =
    """{"auth_session":"sess","barcode_uri":"otpauth://x"}"""

internal fun emailChallengeJson(): String = """{"auth_session":"sess"}"""

internal fun phoneChallengeJson(): String = """{"auth_session":"sess"}"""

internal fun recoveryCodeChallengeJson(): String =
    """{"auth_session":"sess","recovery_code":"REC-123"}"""

internal fun passwordChallengeJson(): String =
    """{"auth_session":"sess","policy":{"complexity":{"min_length":8,""" +
        """"character_types":["lowercase","uppercase"],"character_type_rule":"three_of_four",""" +
        """"identical_characters":"disallow","sequential_characters":"disallow",""" +
        """"max_length_exceeded":"truncate"},"profile_data":{"active":true,"blocked_fields":["email"]},""" +
        """"history":{"active":true,"size":5},"dictionary":{"active":false,"default":"en_10k"}}}"""

internal fun passkeyChallengeJson(): String =
    """{"auth_session":"sess","authn_params_public_key":{""" +
        """"authenticatorSelection":{"residentKey":"required","userVerification":"required"},""" +
        """"challenge":"ch","pubKeyCredParams":[{"alg":-7,"type":"public-key"}],""" +
        """"rp":{"id":"rp","name":"RP"},"timeout":60000,""" +
        """"user":{"displayName":"Al","id":"uid","name":"a@b.com"}}}"""

// --- Authentication-method response fixtures (POST .../verify) ---

internal fun totpMethodJson(id: String = "AM123"): String =
    """{"id":"$id","type":"totp","created_at":"2026-01-02T03:04:05Z","usage":["mfa"]}"""

internal fun pushMethodJson(id: String = "AM123"): String =
    """{"id":"$id","type":"push-notification","created_at":"2026-01-02T03:04:05Z","usage":["mfa"]}"""

internal fun emailMethodJson(id: String = "AM123"): String =
    """{"id":"$id","type":"email","created_at":"2026-01-02T03:04:05Z","usage":["mfa"],"email":"a@b.com"}"""

internal fun phoneMethodJson(id: String = "AM123"): String =
    """{"id":"$id","type":"phone","created_at":"2026-01-02T03:04:05Z","usage":["mfa"],""" +
        """"phone_number":"+15551234567","preferred_authentication_method":"sms"}"""

internal fun recoveryCodeMethodJson(id: String = "AM123"): String =
    """{"id":"$id","type":"recovery-code","created_at":"2026-01-02T03:04:05Z","usage":["mfa"]}"""

internal fun passwordMethodJson(id: String = "AM123"): String =
    """{"id":"$id","type":"password","created_at":"2026-01-02T03:04:05Z","usage":["password"],""" +
        """"identity_user_id":"uid","last_password_reset":"2026-02-03T04:05:06Z"}"""

internal fun passkeyMethodJson(id: String = "AM123"): String =
    """{"id":"$id","type":"passkey","created_at":"2026-01-02T03:04:05Z","usage":["mfa"],""" +
        """"aaguid":"aa","relying_party_id":"rp","key_id":"kid","public_key":"pk",""" +
        """"user_handle":"uh","credential_device_type":"multi_device","credential_backed_up":true}"""

// --- Passkey verify inputs ---

internal fun passkeyChallenge(
    id: String = "AM123",
    authSession: String = "sess",
): PasskeyEnrollmentChallenge =
    PasskeyEnrollmentChallenge(
        authenticationMethodId = id,
        authSession = authSession,
        authParamsPublicKey = AuthnParamsPublicKey(
            authenticatorSelection = AuthenticatorSelection("required", "required"),
            challenge = "ch",
            pubKeyCredParams = listOf(PubKeyCredParam(-7, "public-key")),
            relyingParty = RelyingParty("rp", "RP"),
            timeout = 60_000,
            user = PasskeyUser("Al", "uid", "a@b.com"),
        ),
    )

internal fun publicKeyCredentials(id: String = "cred-1"): PublicKeyCredentials =
    PublicKeyCredentials(
        id = id,
        rawId = "raw",
        type = "public-key",
        response = AuthenticatorResponse(
            clientDataJSON = "cdj",
            attestationObject = "att",
            transports = listOf("internal"),
        ),
    )
