package com.auth0.kmp.authentication

import com.auth0.kmp.authentication.error.AuthenticationError
import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.error.TransportError
import com.auth0.kmp.core.model.Credentials
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.core.RequestOptions
import com.auth0.kmp.core.token.TokenClient
import com.auth0.kmp.core.token.TokenGrant
import com.auth0.kmp.authentication.model.PublicKeyCredentials
import com.auth0.kmp.authentication.model.AuthenticatorResponse
import com.auth0.kmp.authentication.model.SignupProfile
import com.auth0.kmp.networking.NetworkClient
import com.auth0.kmp.networking.request.HttpMethod
import com.auth0.kmp.networking.request.NetworkRequest
import com.auth0.kmp.networking.retry.Backoff
import com.auth0.kmp.networking.retry.RetryPolicy
import com.auth0.kmp.core.validation.IdTokenValidationContext
import com.auth0.kmp.core.validation.IdTokenValidationError
import com.auth0.kmp.core.validation.IdTokenValidator
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content

private fun bodyOf(request: NetworkRequest): JsonObject =
    Json.parseToJsonElement(request.body!!).jsonObject

private const val ID_TOKEN = "header.payload.signature"

private fun credentials(
    accessToken: String = "access-abc",
    idToken: String = ID_TOKEN,
    tokenType: String = "Bearer",
    refreshToken: String? = "refresh-xyz",
    scope: String? = "openid profile email",
): Credentials = Credentials(
    accessToken = accessToken,
    idToken = idToken,
    tokenType = tokenType,
    expiresAt = Instant.fromEpochSeconds(1_000 + 3600),
    refreshToken = refreshToken,
    scope = scope,
)

@OptIn(InternalAuth0Api::class)
private class FakeTokenClient(
    private val outcome: Result<Credentials, TransportError>,
) : TokenClient {
    var lastGrant: TokenGrant? = null
    var lastHeaders: Map<String, String>? = null
    var lastRetryPolicy: RetryPolicy? = null

    override suspend fun fetchToken(
        grant: TokenGrant,
        headers: Map<String, String>,
        retryPolicy: RetryPolicy,
    ): Result<Credentials, TransportError> {
        lastGrant = grant
        lastHeaders = headers
        lastRetryPolicy = retryPolicy
        return outcome
    }
}

private class FakeIdTokenValidator(
    private val verdict: IdTokenValidationError?,
) : IdTokenValidator {
    var lastIdToken: String? = null
    var lastContext: IdTokenValidationContext? = null

    override fun validate(
        idToken: String,
        context: IdTokenValidationContext,
    ): IdTokenValidationError? {
        lastIdToken = idToken
        lastContext = context
        return verdict
    }
}

@OptIn(InternalAuth0Api::class)
private class RecordingNetworkClient(
    private val outcome: Result<String, TransportError>,
) : NetworkClient {
    var lastRequest: NetworkRequest? = null
    var lastRetryPolicy: RetryPolicy? = null
    var callCount: Int = 0

    override suspend fun <T> request(
        request: NetworkRequest,
        retryPolicy: RetryPolicy,
        deserialize: (String) -> T,
    ): Result<T, TransportError> {
        callCount++
        lastRequest = request
        lastRetryPolicy = retryPolicy
        return when (outcome) {
            is Result.Success -> try {
                Result.Success(deserialize(outcome.data))
            } catch (e: SerializationException) {
                Result.Failure(TransportError.Serialization(e.message ?: "Failed to deserialize response"))
            } catch (e: Throwable) {
                Result.Failure(TransportError.Unknown(e.message))
            }
            is Result.Failure -> Result.Failure(outcome.error)
        }
    }

    override fun close() {}
}

@OptIn(InternalAuth0Api::class)
private fun client(
    outcome: Result<Credentials, TransportError>,
    validator: FakeIdTokenValidator = FakeIdTokenValidator(verdict = null),
    tokenClient: FakeTokenClient = FakeTokenClient(outcome),
    networkClient: NetworkClient = RecordingNetworkClient(Result.Success("")),
): Pair<DefaultAuthenticationClient, Pair<FakeTokenClient, FakeIdTokenValidator>> {
    val impl = DefaultAuthenticationClient(
        clientId = "client-123",
        tokenClient = tokenClient,
        idTokenValidator = validator,
        networkClient = networkClient,
    )
    return impl to (tokenClient to validator)
}

@OptIn(InternalAuth0Api::class)
private fun restClient(
    outcome: Result<String, TransportError>,
    net: RecordingNetworkClient = RecordingNetworkClient(outcome),
): Pair<DefaultAuthenticationClient, RecordingNetworkClient> {
    val impl = DefaultAuthenticationClient(
        clientId = "client-123",
        tokenClient = FakeTokenClient(Result.Failure(TransportError.NoInternet)),
        idTokenValidator = FakeIdTokenValidator(verdict = null),
        networkClient = net,
    )
    return impl to net
}

class DefaultAuthenticationClientTest {

    @Test
    fun blankUsername_failsWithInvalidInput_andBuildsNoGrant() = runTest {
        val (impl, deps) = client(Result.Success(credentials()))

        val result = impl.login(usernameOrEmail = " ", password = "pw", realm = "db")

        assertTrue(result is Result.Failure && result.error is AuthenticationError.InvalidInput)
        assertNull(deps.first.lastGrant)
    }

    @Test
    fun blankPassword_failsWithInvalidInput() = runTest {
        val (impl, _) = client(Result.Success(credentials()))

        val result = impl.login(usernameOrEmail = "user", password = "", realm = "db")

        assertTrue(result is Result.Failure && result.error is AuthenticationError.InvalidInput)
    }

    @Test
    fun blankRealm_failsWithInvalidInput() = runTest {
        val (impl, _) = client(Result.Success(credentials()))

        val result = impl.login(usernameOrEmail = "user", password = "pw", realm = "")

        assertTrue(result is Result.Failure && result.error is AuthenticationError.InvalidInput)
    }

    @Test
    fun success_returnsCredentials_withMappedFields() = runTest {
        val (impl, _) = client(Result.Success(credentials()))

        val result = impl.login(usernameOrEmail = "user", password = "pw", realm = "db")

        assertTrue(result is Result.Success)
        val creds = result.data
        assertEquals("access-abc", creds.accessToken)
        assertEquals(ID_TOKEN, creds.idToken)
        assertEquals("Bearer", creds.tokenType)
        assertEquals("refresh-xyz", creds.refreshToken)
        assertEquals("openid profile email", creds.scope)
        assertEquals(Instant.fromEpochSeconds(1_000 + 3600), creds.expiresAt)
    }

    @Test
    fun success_validatesTheReturnedIdToken() = runTest {
        val validator = FakeIdTokenValidator(verdict = null)
        val (impl, _) = client(outcome = Result.Success(credentials()), validator = validator)

        impl.login(usernameOrEmail = "user", password = "pw", realm = "db")

        assertEquals(ID_TOKEN, validator.lastIdToken)
    }

    @Test
    fun success_buildsPasswordRealmGrant() = runTest {
        val (impl, deps) = client(Result.Success(credentials()))

        impl.login(usernameOrEmail = "user", password = "pw", realm = "db", scope = "openid")

        val params = deps.first.lastGrant!!.parameters
        assertEquals("http://auth0.com/oauth/grant-type/password-realm", params.str("grant_type"))
        assertEquals("client-123", params.str("client_id"))
        assertEquals("user", params.str("username"))
        assertEquals("pw", params.str("password"))
        assertEquals("db", params.str("realm"))
        assertEquals("openid", params.str("scope"))
        assertTrue(!params.containsKey("audience"))
    }

    @Test
    fun audienceIncludedInGrant_whenProvided() = runTest {
        val (impl, deps) = client(Result.Success(credentials()))

        impl.login(
            usernameOrEmail = "user",
            password = "pw",
            realm = "db",
            audience = "https://api",
        )

        assertEquals("https://api", deps.first.lastGrant!!.parameters.str("audience"))
    }

    @Test
    fun networkFailure_mapsToAuthenticationErrorNetwork() = runTest {
        val (impl, _) = client(Result.Failure(TransportError.NoInternet))

        val result = impl.login(usernameOrEmail = "user", password = "pw", realm = "db")

        assertEquals(
            Result.Failure(AuthenticationError.Network(TransportError.NoInternet)),
            result,
        )
    }

    @Test
    fun serverError_mapsToApiError() = runTest {
        val server = TransportError.Server(
            403,
            """{"error":"invalid_grant","error_description":"Wrong creds"}""",
        )
        val (impl, _) = client(Result.Failure(server))

        val result = impl.login(usernameOrEmail = "user", password = "pw", realm = "db")

        assertEquals(
            Result.Failure(AuthenticationError.ApiError("invalid_grant", "Wrong creds", 403)),
            result,
        )
    }

    @Test
    fun idTokenInvalid_failsWithIdTokenValidation() = runTest {
        val (impl, _) = client(
            outcome = Result.Success(credentials()),
            validator = FakeIdTokenValidator(verdict = IdTokenValidationError.InvalidIssuer),
        )

        val result = impl.login(usernameOrEmail = "user", password = "pw", realm = "db")

        assertEquals(
            Result.Failure(AuthenticationError.IdTokenValidation(IdTokenValidationError.InvalidIssuer)),
            result,
        )
    }

    @Test
    fun transportFailure_doesNotValidateIdToken() = runTest {
        val validator = FakeIdTokenValidator(verdict = null)
        val (impl, _) = client(outcome = Result.Failure(TransportError.Timeout), validator = validator)

        impl.login(usernameOrEmail = "user", password = "pw", realm = "db")

        assertNull(validator.lastIdToken)
    }

    @Test
    fun login_forwardsOptionsHeaders_toFetchToken() = runTest {
        val (impl, deps) = client(Result.Success(credentials()))

        impl.login(
            usernameOrEmail = "user",
            password = "pw",
            realm = "db",
            options = RequestOptions(headers = mapOf("X-H" to "v")),
        )

        assertEquals("v", deps.first.lastHeaders!!["X-H"])
    }

    @Test
    fun login_forwardsOptionsRetryPolicy_toFetchToken() = runTest {
        val (impl, deps) = client(Result.Success(credentials()))
        val policy = RetryPolicy(
            maxAttempts = 3,
            backoff = Backoff.Fixed(kotlin.time.Duration.ZERO),
            retryOn = { true },
        )

        impl.login(
            usernameOrEmail = "user",
            password = "pw",
            realm = "db",
            options = RequestOptions(retryPolicy = policy),
        )

        assertSame(policy, deps.first.lastRetryPolicy)
    }

    @Test
    fun login_mergesOptionsParameters_intoGrant() = runTest {
        val (impl, deps) = client(Result.Success(credentials()))

        impl.login(
            usernameOrEmail = "user",
            password = "pw",
            realm = "db",
            options = RequestOptions(parameters = mapOf("organization" to "org_1")),
        )

        assertEquals("org_1", deps.first.lastGrant!!.parameters.str("organization"))
    }

    @Test
    fun login_withDefaultOptions_sendsEmptyHeaders_andRetryPolicyNone() = runTest {
        val (impl, deps) = client(Result.Success(credentials()))

        impl.login(usernameOrEmail = "user", password = "pw", realm = "db")

        assertTrue(deps.first.lastHeaders!!.isEmpty())
        assertEquals(RetryPolicy.None, deps.first.lastRetryPolicy)
    }

    // --- createUser ---------------------------------------------------------

    @Test
    fun createUser_success_decodesDatabaseUser() = runTest {
        val (impl, _) = restClient(
            Result.Success("""{"_id":"u_123","email":"a@b.com","username":"al","email_verified":false}"""),
        )

        val result = impl.createUser(profile = SignupProfile(email = "a@b.com"), password = "pw", connection = "db")

        assertTrue(result is Result.Success)
        assertEquals("u_123", result.data.id)
        assertEquals("a@b.com", result.data.email)
        assertEquals("al", result.data.username)
        assertEquals(false, result.data.emailVerified)
    }

    @Test
    fun createUser_buildsSignupPostRequest() = runTest {
        val (impl, net) = restClient(Result.Success("""{"_id":"u_123","email":"a@b.com","email_verified":false}"""))

        impl.createUser(
            profile = SignupProfile(email = "a@b.com", username = "al"),
            password = "pw",
            connection = "db",
            userMetadata = mapOf("plan" to "gold"),
        )

        val req = net.lastRequest!!
        assertEquals(HttpMethod.POST, req.method)
        assertEquals("/dbconnections/signup", req.path)
        val body = bodyOf(req)
        assertEquals("a@b.com", body.str("email"))
        assertEquals("pw", body.str("password"))
        assertEquals("db", body.str("connection"))
        assertEquals("client-123", body.str("client_id"))
        assertEquals("al", body.str("username"))
        assertEquals("gold", body["user_metadata"]!!.jsonObject.str("plan"))
    }

    @Test
    fun createUser_omitsUsernameAndMetadata_whenNotProvided() = runTest {
        val (impl, net) = restClient(Result.Success("""{"_id":"u_123","email":"a@b.com","email_verified":false}"""))

        impl.createUser(profile = SignupProfile(email = "a@b.com"), password = "pw", connection = "db")

        val body = bodyOf(net.lastRequest!!)
        assertTrue(!body.containsKey("username"))
        assertTrue(!body.containsKey("user_metadata"))
    }

    @Test
    fun createUser_blankEmail_failsWithoutNetworkCall() = runTest {
        val (impl, net) = restClient(Result.Success(""))

        val result = impl.createUser(profile = SignupProfile(email = " "), password = "pw", connection = "db")

        assertTrue(result is Result.Failure && result.error is AuthenticationError.InvalidInput)
        assertEquals(0, net.callCount)
    }

    @Test
    fun createUser_serverError_mapsToApiError() = runTest {
        val (impl, _) = restClient(
            Result.Failure(TransportError.Server(400, """{"error":"user_exists","error_description":"exists"}""")),
        )

        val result = impl.createUser(profile = SignupProfile(email = "a@b.com"), password = "pw", connection = "db")

        assertEquals(
            Result.Failure(AuthenticationError.ApiError("user_exists", "exists", 400)),
            result,
        )
    }

    @Test
    fun createUser_malformed2xxBody_mapsToSerializationError_withoutThrowing() = runTest {
        val (impl, _) = restClient(Result.Success("""{"not_a_user":true}"""))

        val result = impl.createUser(profile = SignupProfile(email = "a@b.com"), password = "pw", connection = "db")

        assertTrue(result is Result.Failure)
        val error = result.error
        assertTrue(error is AuthenticationError.Unknown)
        assertTrue(error.cause is TransportError.Serialization)
    }

    // --- resetPassword ------------------------------------------------------

    @Test
    fun resetPassword_success_returnsUnit_andBuildsRequest() = runTest {
        val (impl, net) = restClient(Result.Success(""))

        val result = impl.resetPassword(email = "a@b.com", connection = "db")

        assertTrue(result is Result.Success)
        val req = net.lastRequest!!
        assertEquals(HttpMethod.POST, req.method)
        assertEquals("/dbconnections/change_password", req.path)
        val body = bodyOf(req)
        assertEquals("a@b.com", body.str("email"))
        assertEquals("client-123", body.str("client_id"))
        assertEquals("db", body.str("connection"))
    }

    @Test
    fun resetPassword_blankConnection_failsWithoutNetworkCall() = runTest {
        val (impl, net) = restClient(Result.Success(""))

        val result = impl.resetPassword(email = "a@b.com", connection = " ")

        assertTrue(result is Result.Failure && result.error is AuthenticationError.InvalidInput)
        assertEquals(0, net.callCount)
    }

    @Test
    fun resetPassword_noInternet_mapsToNetwork() = runTest {
        val (impl, _) = restClient(Result.Failure(TransportError.NoInternet))

        val result = impl.resetPassword(email = "a@b.com", connection = "db")

        assertEquals(Result.Failure(AuthenticationError.Network(TransportError.NoInternet)), result)
    }

    // --- userInfo -----------------------------------------------------------

    @Test
    fun userInfo_success_decodesTypedAndCustomClaims() = runTest {
        val (impl, _) = restClient(
            Result.Success("""{"sub":"auth0|1","email":"a@b.com","https://claim/roles":["admin"]}"""),
        )

        val result = impl.userInfo(accessToken = "at")

        assertTrue(result is Result.Success)
        assertEquals("auth0|1", result.data.sub)
        assertEquals("a@b.com", result.data.email)
        assertTrue(result.data.customClaims.containsKey("https://claim/roles"))
        assertTrue(!result.data.customClaims.containsKey("sub"))
    }

    @Test
    fun userInfo_malformed2xxBody_mapsToUnknownError_withoutThrowing() = runTest {
        val (impl, _) = restClient(Result.Success("""["not","an","object"]"""))

        val result = impl.userInfo(accessToken = "at")

        assertTrue(result is Result.Failure)
        val error = result.error
        assertTrue(error is AuthenticationError.Unknown)
        assertTrue(error.cause is TransportError.Unknown)
    }

    @Test
    fun userInfo_buildsGetRequest_withAuthorizationHeader() = runTest {
        val (impl, net) = restClient(Result.Success("""{"sub":"auth0|1"}"""))

        impl.userInfo(accessToken = "at", tokenType = "DPoP")

        val req = net.lastRequest!!
        assertEquals(HttpMethod.GET, req.method)
        assertEquals("/userinfo", req.path)
        assertEquals("DPoP at", req.headers["Authorization"])
        assertNull(req.body)
    }

    @Test
    fun userInfo_defaultsToBearer() = runTest {
        val (impl, net) = restClient(Result.Success("""{"sub":"auth0|1"}"""))

        impl.userInfo(accessToken = "at")

        assertEquals("Bearer at", net.lastRequest!!.headers["Authorization"])
    }

    @Test
    fun userInfo_blankToken_failsWithoutNetworkCall() = runTest {
        val (impl, net) = restClient(Result.Success("""{"sub":"auth0|1"}"""))

        val result = impl.userInfo(accessToken = " ")

        assertTrue(result is Result.Failure && result.error is AuthenticationError.InvalidInput)
        assertEquals(0, net.callCount)
    }

    @Test
    fun userInfo_blankTokenType_failsWithoutNetworkCall() = runTest {
        val (impl, net) = restClient(Result.Success("""{"sub":"auth0|1"}"""))

        val result = impl.userInfo(accessToken = "at", tokenType = " ")

        assertTrue(result is Result.Failure && result.error is AuthenticationError.InvalidInput)
        assertEquals(0, net.callCount)
    }

    @Test
    fun userInfo_addressWithNonPrimitiveSubField_ignoresIt_keepsPrimitives() = runTest {
        val (impl, _) = restClient(
            Result.Success("""{"sub":"auth0|1","address":{"country":"US","geo":{"lat":1}}}"""),
        )

        val result = impl.userInfo(accessToken = "at")

        assertTrue(result is Result.Success)
        assertEquals("US", result.data.address?.country)
        assertNull(result.data.address?.formatted)
    }

    // --- revoke -------------------------------------------------------------

    @Test
    fun revoke_success_returnsUnit_andBuildsRequest() = runTest {
        val (impl, net) = restClient(Result.Success(""))

        val result = impl.revoke(refreshToken = "rt")

        assertTrue(result is Result.Success)
        val req = net.lastRequest!!
        assertEquals(HttpMethod.POST, req.method)
        assertEquals("/oauth/revoke", req.path)
        val body = bodyOf(req)
        assertEquals("client-123", body.str("client_id"))
        assertEquals("rt", body.str("token"))
    }

    @Test
    fun revoke_blankToken_failsWithoutNetworkCall() = runTest {
        val (impl, net) = restClient(Result.Success(""))

        val result = impl.revoke(refreshToken = " ")

        assertTrue(result is Result.Failure && result.error is AuthenticationError.InvalidInput)
        assertEquals(0, net.callCount)
    }

    // --- renew --------------------------------------------------------------

    @Test
    fun renew_success_returnsCredentials_withoutValidatingIdToken() = runTest {
        val validator = FakeIdTokenValidator(verdict = null)
        val (impl, _) = client(outcome = Result.Success(credentials()), validator = validator)

        val result = impl.renew(refreshToken = "rt")

        assertTrue(result is Result.Success)
        assertNull(validator.lastIdToken)
    }

    @Test
    fun renew_buildsRefreshTokenGrant() = runTest {
        val (impl, deps) = client(Result.Success(credentials()))

        impl.renew(refreshToken = "rt", audience = "https://api", scope = "openid")

        val params = deps.first.lastGrant!!.parameters
        assertEquals("refresh_token", params.str("grant_type"))
        assertEquals("client-123", params.str("client_id"))
        assertEquals("rt", params.str("refresh_token"))
        assertEquals("https://api", params.str("audience"))
        assertEquals("openid", params.str("scope"))
    }

    @Test
    fun renew_blankToken_failsWithoutBuildingGrant() = runTest {
        val (impl, deps) = client(Result.Success(credentials()))

        val result = impl.renew(refreshToken = " ")

        assertTrue(result is Result.Failure && result.error is AuthenticationError.InvalidInput)
        assertNull(deps.first.lastGrant)
    }

    @Test
    fun renew_transportFailure_maps() = runTest {
        val (impl, _) = client(Result.Failure(TransportError.NoInternet))

        val result = impl.renew(refreshToken = "rt")

        assertEquals(Result.Failure(AuthenticationError.Network(TransportError.NoInternet)), result)
    }

    // --- loginWithPasskey ---------------------------------------------------

    @Test
    fun loginWithPasskey_success_returnsCredentials_andValidatesIdToken() = runTest {
        val validator = FakeIdTokenValidator(verdict = null)
        val (impl, _) = client(outcome = Result.Success(credentials()), validator = validator)

        val result = impl.loginWithPasskey(authSession = "sess", authResponse = publicKeyCredentials())

        assertTrue(result is Result.Success)
        assertEquals(ID_TOKEN, validator.lastIdToken)
    }

    @Test
    fun loginWithPasskey_buildsPasskeyGrant_withNestedAuthnResponse() = runTest {
        val (impl, deps) = client(Result.Success(credentials()))

        impl.loginWithPasskey(
            authSession = "sess",
            authResponse = publicKeyCredentials(),
            realm = "db",
            organization = "org_1",
        )

        val params = deps.first.lastGrant!!.parameters
        assertEquals("urn:okta:params:oauth:grant-type:webauthn", params.str("grant_type"))
        assertEquals("client-123", params.str("client_id"))
        assertEquals("sess", params.str("auth_session"))
        assertEquals("db", params.str("realm"))
        assertEquals("org_1", params.str("organization"))
        assertEquals("cred-1", params["authn_response"]!!.jsonObject.str("id"))
        // Default scope requests a refresh token; no audience unless supplied.
        assertEquals("openid profile email offline_access", params.str("scope"))
        assertNull(params["audience"])
    }

    @Test
    fun loginWithPasskey_sendsExplicitScopeAndAudience() = runTest {
        val (impl, deps) = client(Result.Success(credentials()))

        impl.loginWithPasskey(
            authSession = "sess",
            authResponse = publicKeyCredentials(),
            audience = "https://api.example.com",
            scope = "openid",
        )

        val params = deps.first.lastGrant!!.parameters
        assertEquals("openid", params.str("scope"))
        assertEquals("https://api.example.com", params.str("audience"))
    }

    @Test
    fun loginWithPasskey_blankSession_failsWithoutBuildingGrant() = runTest {
        val (impl, deps) = client(Result.Success(credentials()))

        val result = impl.loginWithPasskey(authSession = " ", authResponse = publicKeyCredentials())

        assertTrue(result is Result.Failure && result.error is AuthenticationError.InvalidInput)
        assertNull(deps.first.lastGrant)
    }

    @Test
    fun loginWithPasskey_invalidIdToken_failsWithIdTokenValidation() = runTest {
        val (impl, _) = client(
            outcome = Result.Success(credentials()),
            validator = FakeIdTokenValidator(verdict = IdTokenValidationError.InvalidIssuer),
        )

        val result = impl.loginWithPasskey(authSession = "sess", authResponse = publicKeyCredentials())

        assertEquals(
            Result.Failure(AuthenticationError.IdTokenValidation(IdTokenValidationError.InvalidIssuer)),
            result,
        )
    }

    // --- passkeyLoginChallenge ----------------------------------------------

    @Test
    fun passkeyLoginChallenge_success_decodes_andBuildsRequest() = runTest {
        val (impl, net) = restClient(
            Result.Success(
                """{"auth_session":"sess","authn_params_public_key":{"challenge":"ch","rpId":"rp","timeout":60000,"userVerification":"required"}}""",
            ),
        )

        val result = impl.passkeyLoginChallenge(realm = "db", organization = "org_1")

        assertTrue(result is Result.Success)
        assertEquals("sess", result.data.authSession)
        assertEquals("ch", result.data.authParamsPublicKey.challenge)
        assertEquals(60000L, result.data.authParamsPublicKey.timeout)
        assertEquals("rp", result.data.authParamsPublicKey.rpId)
        assertEquals("required", result.data.authParamsPublicKey.userVerification)
        val req = net.lastRequest!!
        assertEquals(HttpMethod.POST, req.method)
        assertEquals("/passkey/challenge", req.path)
        val body = bodyOf(req)
        assertEquals("client-123", body.str("client_id"))
        assertEquals("db", body.str("realm"))
        assertEquals("org_1", body.str("organization"))
    }

    @Test
    fun passkeyLoginChallenge_malformed2xxBody_mapsToSerializationError_withoutThrowing() = runTest {
        val (impl, _) = restClient(Result.Success("""{"garbage":1}"""))

        val result = impl.passkeyLoginChallenge(realm = "db")

        assertTrue(result is Result.Failure)
        val error = result.error
        assertTrue(error is AuthenticationError.Unknown)
        assertTrue(error.cause is TransportError.Serialization)
    }

    // --- passkeySignupChallenge ---------------------------------------------

    @Test
    fun passkeySignupChallenge_buildsRegisterRequest_withUserProfileObject() = runTest {
        val (impl, net) = restClient(
            Result.Success(
                """{"auth_session":"sess","authn_params_public_key":{"authenticatorSelection":{"residentKey":"required","userVerification":"required"},"challenge":"ch","pubKeyCredParams":[{"alg":-7,"type":"public-key"}],"rp":{"id":"rp","name":"RP"},"timeout":60000,"user":{"displayName":"Al","id":"uid","name":"a@b.com"}}}""",
            ),
        )

        val result = impl.passkeySignupChallenge(
            profile = SignupProfile(email = "a@b.com"),
            userMetadata = mapOf("plan" to "gold"),
            realm = "db",
        )

        assertTrue(result is Result.Success)
        // Assert the decoded challenge shape, not just the outgoing request.
        val challenge = result.data
        assertEquals("sess", challenge.authSession)
        val pk = challenge.authParamsPublicKey
        assertEquals("ch", pk.challenge)
        assertEquals(60000L, pk.timeout)
        assertEquals("rp", pk.relyingParty.id)
        assertEquals("uid", pk.user.id)
        assertEquals("a@b.com", pk.user.name)
        assertEquals(-7, pk.pubKeyCredParams.first().alg)
        assertEquals("required", pk.authenticatorSelection.userVerification)
        val req = net.lastRequest!!
        assertEquals(HttpMethod.POST, req.method)
        assertEquals("/passkey/register", req.path)
        val body = bodyOf(req)
        assertEquals("client-123", body.str("client_id"))
        assertEquals("db", body.str("realm"))
        val userProfile = body["user_profile"]!!.jsonObject
        assertEquals("a@b.com", userProfile.str("email"))
        assertTrue(!userProfile.containsKey("user_metadata"))
        assertEquals("gold", body["user_metadata"]!!.jsonObject.str("plan"))
    }

    @Test
    fun passkeySignupChallenge_malformed2xxBody_mapsToSerializationError_withoutThrowing() = runTest {
        val (impl, _) = restClient(Result.Success("""{"garbage":1}"""))

        val result = impl.passkeySignupChallenge(profile = SignupProfile(email = "a@b.com"))

        assertTrue(result is Result.Failure)
        val error = result.error
        assertTrue(error is AuthenticationError.Unknown)
        assertTrue(error.cause is TransportError.Serialization)
    }

    // --- options forwarding -------------------------------------------------

    @Test
    fun restEndpoint_forwardsOptions_headersParamsRetryPolicy() = runTest {
        val (impl, net) = restClient(Result.Success("""{"sub":"auth0|1"}"""))
        val policy = RetryPolicy(
            maxAttempts = 2,
            backoff = Backoff.Fixed(kotlin.time.Duration.ZERO),
            retryOn = { true },
        )

        impl.userInfo(
            accessToken = "at",
            options = RequestOptions(
                parameters = mapOf("extra" to "1"),
                headers = mapOf("X-H" to "v"),
                retryPolicy = policy,
            ),
        )

        val req = net.lastRequest!!
        assertEquals("v", req.headers["X-H"])
        assertEquals("1", req.query["extra"])
        assertSame(policy, net.lastRetryPolicy)
    }

    private fun publicKeyCredentials(): PublicKeyCredentials = PublicKeyCredentials(
        id = "cred-1",
        rawId = "raw-1",
        type = "public-key",
        response = AuthenticatorResponse(
            clientDataJSON = "cdj",
            authenticatorData = "ad",
            signature = "sig",
            userHandle = "uh",
        ),
    )
}
