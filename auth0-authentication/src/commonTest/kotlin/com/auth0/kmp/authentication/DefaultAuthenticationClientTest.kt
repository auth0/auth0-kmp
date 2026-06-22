package com.auth0.kmp.authentication

import com.auth0.kmp.authentication.error.AuthenticationError
import com.auth0.kmp.authentication.validation.IdTokenValidationError
import com.auth0.kmp.authentication.validation.IdTokenValidator
import com.auth0.kmp.core.error.TransportError
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.networking.NetworkClient
import com.auth0.kmp.networking.request.HttpMethod
import com.auth0.kmp.networking.request.NetworkRequest
import com.auth0.kmp.networking.retry.RetryPolicy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

private const val ID_TOKEN = "header.payload.signature"

private fun tokenJson(
    accessToken: String = "access-abc",
    idToken: String = ID_TOKEN,
    tokenType: String = "Bearer",
    expiresIn: Long = 3600,
    refreshToken: String? = "refresh-xyz",
    scope: String? = "openid profile email",
): String = buildString {
    append("{")
    append(""""access_token":"$accessToken",""")
    append(""""id_token":"$idToken",""")
    append(""""token_type":"$tokenType",""")
    append(""""expires_in":$expiresIn""")
    if (refreshToken != null) append(""","refresh_token":"$refreshToken"""")
    if (scope != null) append(""","scope":"$scope"""")
    append("}")
}

private class FakeNetworkClient(
    private val outcome: Result<String, TransportError>,
) : NetworkClient {
    var lastRequest: NetworkRequest? = null
    var closed = false
        private set

    override suspend fun <T> request(
        request: NetworkRequest,
        retryPolicy: RetryPolicy,
        deserialize: (String) -> T,
    ): Result<T, TransportError> {
        lastRequest = request
        return when (outcome) {
            is Result.Success -> Result.Success(deserialize(outcome.data))
            is Result.Failure -> outcome
        }
    }

    override fun close() {
        closed = true
    }
}

private class FakeIdTokenValidator(
    private val verdict: IdTokenValidationError?,
) : IdTokenValidator {
    var lastIdToken: String? = null

    override fun validate(idToken: String): IdTokenValidationError? {
        lastIdToken = idToken
        return verdict
    }
}

private class FixedClock(private val at: Instant) : Clock {
    override fun now(): Instant = at
}

private fun client(
    outcome: Result<String, TransportError>,
    validator: FakeIdTokenValidator = FakeIdTokenValidator(verdict = null),
    clock: Clock = FixedClock(Instant.fromEpochSeconds(1_000)),
    networkClient: FakeNetworkClient = FakeNetworkClient(outcome),
): Pair<DefaultAuthenticationClient, Triple<FakeNetworkClient, FakeIdTokenValidator, Clock>> {
    val impl = DefaultAuthenticationClient(
        clientId = "client-123",
        networkClient = networkClient,
        idTokenValidator = validator,
        clock = clock,
    )
    return impl to Triple(networkClient, validator, clock)
}

class DefaultAuthenticationClientTest {

    @Test
    fun blankUsername_failsWithInvalidInput_andSendsNoRequest() = runTest {
        val (impl, deps) = client(Result.Success(tokenJson()))

        val result = impl.login(usernameOrEmail = " ", password = "pw", realm = "db")

        assertTrue(result is Result.Failure && result.error is AuthenticationError.InvalidInput)
        assertNull(deps.first.lastRequest)
    }

    @Test
    fun blankPassword_failsWithInvalidInput() = runTest {
        val (impl, _) = client(Result.Success(tokenJson()))

        val result = impl.login(usernameOrEmail = "user", password = "", realm = "db")

        assertTrue(result is Result.Failure && result.error is AuthenticationError.InvalidInput)
    }

    @Test
    fun blankRealm_failsWithInvalidInput() = runTest {
        val (impl, _) = client(Result.Success(tokenJson()))

        val result = impl.login(usernameOrEmail = "user", password = "pw", realm = "")

        assertTrue(result is Result.Failure && result.error is AuthenticationError.InvalidInput)
    }

    @Test
    fun success_returnsCredentials_withMappedFields() = runTest {
        val (impl, _) = client(
            outcome = Result.Success(tokenJson(expiresIn = 3600)),
            clock = FixedClock(Instant.fromEpochSeconds(1_000)),
        )

        val result = impl.login(usernameOrEmail = "user", password = "pw", realm = "db")

        assertTrue(result is Result.Success)
        val credentials = result.data
        assertEquals("access-abc", credentials.accessToken)
        assertEquals(ID_TOKEN, credentials.idToken)
        assertEquals("Bearer", credentials.tokenType)
        assertEquals("refresh-xyz", credentials.refreshToken)
        assertEquals("openid profile email", credentials.scope)
        assertEquals(Instant.fromEpochSeconds(1_000 + 3600), credentials.expiresAt)
    }

    @Test
    fun success_validatesTheReturnedIdToken() = runTest {
        val validator = FakeIdTokenValidator(verdict = null)
        val (impl, _) = client(outcome = Result.Success(tokenJson()), validator = validator)

        impl.login(usernameOrEmail = "user", password = "pw", realm = "db")

        assertEquals(ID_TOKEN, validator.lastIdToken)
    }

    @Test
    fun success_sendsPasswordRealmRequest() = runTest {
        val (impl, deps) = client(Result.Success(tokenJson()))

        impl.login(usernameOrEmail = "user", password = "pw", realm = "db", scope = "openid")

        val request = deps.first.lastRequest!!
        assertEquals(HttpMethod.POST, request.method)
        assertEquals("/oauth/token", request.path)
        val body = request.body!!
        assertTrue(body.contains(""""client_id":"client-123""""))
        assertTrue(body.contains("http://auth0.com/oauth/grant-type/password-realm"))
        assertTrue(body.contains(""""username":"user""""))
        assertTrue(body.contains(""""realm":"db""""))
        assertTrue(body.contains(""""scope":"openid""""))
        assertTrue(!body.contains("audience"))
    }

    @Test
    fun audienceIncludedInBody_whenProvided() = runTest {
        val (impl, deps) = client(Result.Success(tokenJson()))

        impl.login(
            usernameOrEmail = "user",
            password = "pw",
            realm = "db",
            audience = "https://api",
        )

        assertTrue(deps.first.lastRequest!!.body!!.contains(""""audience":"https://api""""))
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
            outcome = Result.Success(tokenJson()),
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
    fun close_closesNetworkClient() {
        val (impl, deps) = client(Result.Success(tokenJson()))

        impl.close()

        assertTrue(deps.first.closed)
    }
}
