package com.auth0.kmp.authentication.passwordless

import com.auth0.kmp.authentication.error.AuthenticationError
import com.auth0.kmp.authentication.model.DeliveryMethod
import com.auth0.kmp.authentication.model.PasswordlessChallenge
import com.auth0.kmp.authentication.model.PasswordlessType
import com.auth0.kmp.core.RequestOptions
import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.error.TransportError
import com.auth0.kmp.core.model.Credentials
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.core.token.TokenClient
import com.auth0.kmp.core.token.TokenGrant
import com.auth0.kmp.core.validation.IdTokenValidationContext
import com.auth0.kmp.core.validation.IdTokenValidationError
import com.auth0.kmp.core.validation.IdTokenValidator
import com.auth0.kmp.networking.NetworkClient
import com.auth0.kmp.networking.request.HttpMethod
import com.auth0.kmp.networking.request.NetworkRequest
import com.auth0.kmp.networking.retry.RetryPolicy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

// ─── Test helpers ────────────────────────────────────────────────────────────

private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content
private fun JsonObject.bool(key: String): Boolean? = this[key]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
private fun bodyOf(request: NetworkRequest): JsonObject =
    Json.parseToJsonElement(request.body!!).jsonObject

private const val ID_TOKEN = "header.payload.signature"
private const val CLIENT_ID = "client-123"

private fun credentials(idToken: String = ID_TOKEN): Credentials = Credentials(
    accessToken = "access-abc",
    idToken = idToken,
    tokenType = "Bearer",
    expiresAt = Instant.fromEpochSeconds(1_000 + 3_600),
    refreshToken = "refresh-xyz",
    scope = "openid profile email",
)

@OptIn(InternalAuth0Api::class)
private class FakeTokenClient(
    private val outcome: Result<Credentials, TransportError>,
) : TokenClient {
    var lastGrant: TokenGrant? = null

    override suspend fun fetchToken(
        grant: TokenGrant,
        headers: Map<String, String>,
        retryPolicy: RetryPolicy,
    ): Result<Credentials, TransportError> {
        lastGrant = grant
        return outcome
    }

    override suspend fun fetchSsoCredentials(
        grant: TokenGrant,
        headers: Map<String, String>,
        retryPolicy: RetryPolicy,
    ): Result<com.auth0.kmp.core.model.SsoCredentials, TransportError> {
        error("not used in passwordless tests")
    }
}

private class FakeIdTokenValidator(
    private val verdict: IdTokenValidationError? = null,
) : IdTokenValidator {
    var lastIdToken: String? = null

    override fun validate(
        idToken: String,
        context: IdTokenValidationContext,
    ): IdTokenValidationError? {
        lastIdToken = idToken
        return verdict
    }
}

@OptIn(InternalAuth0Api::class)
private class RecordingNetworkClient(
    private val outcome: Result<String, TransportError>,
) : NetworkClient {
    var lastRequest: NetworkRequest? = null
    var callCount: Int = 0

    override suspend fun <T> request(
        request: NetworkRequest,
        retryPolicy: RetryPolicy,
        deserialize: (String) -> T,
    ): Result<T, TransportError> {
        callCount++
        lastRequest = request
        return when (outcome) {
            is Result.Success -> try {
                Result.Success(deserialize(outcome.data))
            } catch (e: SerializationException) {
                Result.Failure(TransportError.Serialization(e.message ?: "deserialization failed"))
            } catch (e: Throwable) {
                Result.Failure(TransportError.Unknown(e.message))
            }
            is Result.Failure -> Result.Failure(outcome.error)
        }
    }

    override fun close() {}
}

/** Token-based call (verify steps). */
private fun tokenClient(
    outcome: Result<Credentials, TransportError> = Result.Success(credentials()),
    validator: FakeIdTokenValidator = FakeIdTokenValidator(),
): Triple<DefaultPasswordlessClient, FakeTokenClient, FakeIdTokenValidator> {
    val tc = FakeTokenClient(outcome)
    val client = DefaultPasswordlessClient(
        clientId = CLIENT_ID,
        tokenClient = tc,
        idTokenValidator = validator,
        networkClient = RecordingNetworkClient(Result.Success("")),
    )
    return Triple(client, tc, validator)
}

/** REST-call (start / challenge steps). */
private fun restClient(
    outcome: Result<String, TransportError> = Result.Success(""),
): Pair<DefaultPasswordlessClient, RecordingNetworkClient> {
    val net = RecordingNetworkClient(outcome)
    val client = DefaultPasswordlessClient(
        clientId = CLIENT_ID,
        tokenClient = FakeTokenClient(Result.Failure(TransportError.NoInternet)),
        idTokenValidator = FakeIdTokenValidator(),
        networkClient = net,
    )
    return client to net
}

// ─── Tests ───────────────────────────────────────────────────────────────────

class DefaultPasswordlessClientTest {

    // ── passwordlessWithEmail ────────────────────────────────────────────────

    @Test
    fun passwordlessWithEmail_success_buildsStartRequest() = runTest {
        val (client, net) = restClient()

        val result = client.passwordlessWithEmail(email = "a@b.com")

        assertTrue(result is Result.Success)
        val req = net.lastRequest!!
        assertEquals(HttpMethod.POST, req.method)
        assertEquals("/passwordless/start", req.path)
        val body = bodyOf(req)
        assertEquals(CLIENT_ID, body.str("client_id"))
        assertEquals("email", body.str("connection"))
        assertEquals("a@b.com", body.str("email"))
        assertEquals("code", body.str("send"))
    }

    @Test
    fun passwordlessWithEmail_usesTypeAndConnection_whenProvided() = runTest {
        val (client, net) = restClient()

        client.passwordlessWithEmail(
            email = "a@b.com",
            type = PasswordlessType.LINK,
            connection = "custom-email",
        )

        val body = bodyOf(net.lastRequest!!)
        assertEquals("link", body.str("send"))
        assertEquals("custom-email", body.str("connection"))
    }

    @Test
    fun passwordlessWithEmail_blankEmail_failsWithoutNetworkCall() = runTest {
        val (client, net) = restClient()

        val result = client.passwordlessWithEmail(email = " ")

        assertTrue(result is Result.Failure && result.error is AuthenticationError.InvalidInput)
        assertEquals(0, net.callCount)
    }

    @Test
    fun passwordlessWithEmail_blankConnection_failsWithoutNetworkCall() = runTest {
        val (client, net) = restClient()

        val result = client.passwordlessWithEmail(email = "a@b.com", connection = " ")

        assertTrue(result is Result.Failure && result.error is AuthenticationError.InvalidInput)
        assertEquals(0, net.callCount)
    }

    @Test
    fun passwordlessWithEmail_serverError_mapsToApiError() = runTest {
        val (client, _) = restClient(
            Result.Failure(TransportError.Server(400, """{"error":"bad_request","error_description":"nope"}""")),
        )

        val result = client.passwordlessWithEmail(email = "a@b.com")

        assertEquals(
            Result.Failure(AuthenticationError.ApiError("bad_request", "nope", 400)),
            result,
        )
    }

    // ── passwordlessWithSMS ──────────────────────────────────────────────────

    @Test
    fun passwordlessWithSMS_success_buildsStartRequest() = runTest {
        val (client, net) = restClient()

        val result = client.passwordlessWithSMS(phoneNumber = "+15551234567")

        assertTrue(result is Result.Success)
        val req = net.lastRequest!!
        assertEquals("/passwordless/start", req.path)
        val body = bodyOf(req)
        assertEquals(CLIENT_ID, body.str("client_id"))
        assertEquals("sms", body.str("connection"))
        assertEquals("+15551234567", body.str("phone_number"))
        assertEquals("code", body.str("send"))
    }

    @Test
    fun passwordlessWithSMS_blankPhoneNumber_failsWithoutNetworkCall() = runTest {
        val (client, net) = restClient()

        val result = client.passwordlessWithSMS(phoneNumber = " ")

        assertTrue(result is Result.Failure && result.error is AuthenticationError.InvalidInput)
        assertEquals(0, net.callCount)
    }

    // ── loginWithEmail ───────────────────────────────────────────────────────

    @Test
    fun loginWithEmail_success_buildsPasswordlessGrant_andValidatesIdToken() = runTest {
        val (client, tc, validator) = tokenClient()

        val result = client.loginWithEmail(email = "a@b.com", code = "123456", scope = "openid")

        assertTrue(result is Result.Success)
        val params = tc.lastGrant!!.parameters
        assertEquals("http://auth0.com/oauth/grant-type/passwordless/otp", params.str("grant_type"))
        assertEquals(CLIENT_ID, params.str("client_id"))
        assertEquals("a@b.com", params.str("username"))
        assertEquals("123456", params.str("otp"))
        assertEquals("email", params.str("realm"))
        assertEquals("openid", params.str("scope"))
        assertEquals(ID_TOKEN, validator.lastIdToken)
    }

    @Test
    fun loginWithEmail_includesAudience_whenProvided() = runTest {
        val (client, tc, _) = tokenClient()

        client.loginWithEmail(email = "a@b.com", code = "123456", audience = "https://api")

        assertEquals("https://api", tc.lastGrant!!.parameters.str("audience"))
    }

    @Test
    fun loginWithEmail_blankCode_failsWithoutBuildingGrant() = runTest {
        val (client, tc, _) = tokenClient()

        val result = client.loginWithEmail(email = "a@b.com", code = " ")

        assertTrue(result is Result.Failure && result.error is AuthenticationError.InvalidInput)
        assertNull(tc.lastGrant)
    }

    @Test
    fun loginWithEmail_blankEmail_failsWithoutBuildingGrant() = runTest {
        val (client, tc, _) = tokenClient()

        val result = client.loginWithEmail(email = " ", code = "123456")

        assertTrue(result is Result.Failure && result.error is AuthenticationError.InvalidInput)
        assertNull(tc.lastGrant)
    }

    @Test
    fun loginWithEmail_invalidIdToken_failsWithIdTokenValidation() = runTest {
        val validator = FakeIdTokenValidator(IdTokenValidationError.InvalidIssuer)
        val (client, _, _) = tokenClient(validator = validator)

        val result = client.loginWithEmail(email = "a@b.com", code = "123456")

        assertTrue(result is Result.Failure && result.error is AuthenticationError.IdTokenValidation)
    }

    // ── loginWithPhoneNumber ─────────────────────────────────────────────────

    @Test
    fun loginWithPhoneNumber_success_buildsPasswordlessGrant_withSmsRealm() = runTest {
        val (client, tc, _) = tokenClient()

        client.loginWithPhoneNumber(phoneNumber = "+15551234567", code = "123456")

        val params = tc.lastGrant!!.parameters
        assertEquals("+15551234567", params.str("username"))
        assertEquals("123456", params.str("otp"))
        assertEquals("sms", params.str("realm"))
    }

    @Test
    fun loginWithPhoneNumber_blankPhoneNumber_failsWithoutBuildingGrant() = runTest {
        val (client, tc, _) = tokenClient()

        val result = client.loginWithPhoneNumber(phoneNumber = " ", code = "123456")

        assertTrue(result is Result.Failure && result.error is AuthenticationError.InvalidInput)
        assertNull(tc.lastGrant)
    }

    // ── challengeWithEmail ───────────────────────────────────────────────────

    @Test
    fun challengeWithEmail_success_buildsOtpChallengeRequest() = runTest {
        val (client, net) = restClient(
            Result.Success("""{"auth_session":"sess-abc"}"""),
        )

        val result = client.challengeWithEmail(
            email = "a@b.com",
            connection = "Username-Password-Authentication",
        )

        assertTrue(result is Result.Success)
        assertEquals("sess-abc", result.data.authSession)
        val req = net.lastRequest!!
        assertEquals(HttpMethod.POST, req.method)
        assertEquals("/otp/challenge", req.path)
        val body = bodyOf(req)
        assertEquals(CLIENT_ID, body.str("client_id"))
        assertEquals("a@b.com", body.str("email"))
        assertEquals("Username-Password-Authentication", body.str("connection"))
        assertEquals("false", body.str("allow_signup"))
    }

    @Test
    fun challengeWithEmail_withAllowSignup_sendsTrue() = runTest {
        val (client, net) = restClient(Result.Success("""{"auth_session":"s"}"""))

        client.challengeWithEmail(
            email = "a@b.com",
            connection = "con",
            allowSignup = true,
        )

        assertEquals("true", bodyOf(net.lastRequest!!).str("allow_signup"))
    }

    @Test
    fun challengeWithEmail_blankEmail_failsWithoutNetworkCall() = runTest {
        val (client, net) = restClient()

        val result = client.challengeWithEmail(email = " ", connection = "con")

        assertTrue(result is Result.Failure && result.error is AuthenticationError.InvalidInput)
        assertEquals(0, net.callCount)
    }

    @Test
    fun challengeWithEmail_blankConnection_failsWithoutNetworkCall() = runTest {
        val (client, net) = restClient()

        val result = client.challengeWithEmail(email = "a@b.com", connection = " ")

        assertTrue(result is Result.Failure && result.error is AuthenticationError.InvalidInput)
        assertEquals(0, net.callCount)
    }

    // ── challengeWithPhoneNumber ─────────────────────────────────────────────

    @Test
    fun challengeWithPhoneNumber_success_buildsOtpChallengeRequest() = runTest {
        val (client, net) = restClient(Result.Success("""{"auth_session":"sess-xyz"}"""))

        val result = client.challengeWithPhoneNumber(
            phoneNumber = "+15551234567",
            connection = "Username-Password-Authentication",
        )

        assertTrue(result is Result.Success)
        assertEquals("sess-xyz", result.data.authSession)
        val req = net.lastRequest!!
        assertEquals("/otp/challenge", req.path)
        val body = bodyOf(req)
        assertEquals("+15551234567", body.str("phone_number"))
        assertEquals("text", body.str("delivery_method"))
        assertEquals("false", body.str("allow_signup"))
    }

    @Test
    fun challengeWithPhoneNumber_voice_sendsDeliveryMethod() = runTest {
        val (client, net) = restClient(Result.Success("""{"auth_session":"s"}"""))

        client.challengeWithPhoneNumber(
            phoneNumber = "+15551234567",
            connection = "con",
            deliveryMethod = DeliveryMethod.VOICE,
        )

        assertEquals("voice", bodyOf(net.lastRequest!!).str("delivery_method"))
    }

    @Test
    fun challengeWithPhoneNumber_blankPhoneNumber_failsWithoutNetworkCall() = runTest {
        val (client, net) = restClient()

        val result = client.challengeWithPhoneNumber(phoneNumber = " ", connection = "con")

        assertTrue(result is Result.Failure && result.error is AuthenticationError.InvalidInput)
        assertEquals(0, net.callCount)
    }

    // ── loginWithOTP ─────────────────────────────────────────────────────────

    @Test
    fun loginWithOTP_success_buildsChallengeGrant_andValidatesIdToken() = runTest {
        val (client, tc, validator) = tokenClient()
        val challenge = PasswordlessChallenge(authSession = "sess-abc")

        val result = client.loginWithOTP(challenge = challenge, otp = "654321", scope = "openid")

        assertTrue(result is Result.Success)
        val params = tc.lastGrant!!.parameters
        assertEquals("http://auth0.com/oauth/grant-type/passwordless/otp", params.str("grant_type"))
        assertEquals(CLIENT_ID, params.str("client_id"))
        assertEquals("sess-abc", params.str("auth_session"))
        assertEquals("654321", params.str("otp"))
        assertEquals("openid", params.str("scope"))
        assertNull(params.str("username"))    // must NOT send username in this flow
        assertNull(params.str("realm"))       // must NOT send realm in this flow
        assertEquals(ID_TOKEN, validator.lastIdToken)
    }

    @Test
    fun loginWithOTP_includesAudience_whenProvided() = runTest {
        val (client, tc, _) = tokenClient()

        client.loginWithOTP(
            challenge = PasswordlessChallenge("s"),
            otp = "123",
            audience = "https://api",
        )

        assertEquals("https://api", tc.lastGrant!!.parameters.str("audience"))
    }

    @Test
    fun loginWithOTP_blankOtp_failsWithoutBuildingGrant() = runTest {
        val (client, tc, _) = tokenClient()

        val result = client.loginWithOTP(challenge = PasswordlessChallenge("s"), otp = " ")

        assertTrue(result is Result.Failure && result.error is AuthenticationError.InvalidInput)
        assertNull(tc.lastGrant)
    }

    @Test
    fun loginWithOTP_invalidIdToken_failsWithIdTokenValidation() = runTest {
        val validator = FakeIdTokenValidator(IdTokenValidationError.InvalidIssuer)
        val (client, _, _) = tokenClient(validator = validator)

        val result = client.loginWithOTP(challenge = PasswordlessChallenge("s"), otp = "123")

        assertTrue(result is Result.Failure && result.error is AuthenticationError.IdTokenValidation)
    }
}
