package com.auth0.kmp.authentication

import com.auth0.kmp.authentication.error.AuthenticationError
import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.error.TransportError
import com.auth0.kmp.core.model.Credentials
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.core.token.TokenClient
import com.auth0.kmp.core.token.TokenGrant
import com.auth0.kmp.core.validation.IdTokenValidationContext
import com.auth0.kmp.core.validation.IdTokenValidationError
import com.auth0.kmp.core.validation.IdTokenValidator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

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

    override suspend fun fetchToken(
        grant: TokenGrant,
        headers: Map<String, String>,
    ): Result<Credentials, TransportError> {
        lastGrant = grant
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
private fun client(
    outcome: Result<Credentials, TransportError>,
    validator: FakeIdTokenValidator = FakeIdTokenValidator(verdict = null),
    tokenClient: FakeTokenClient = FakeTokenClient(outcome),
): Pair<DefaultAuthenticationClient, Pair<FakeTokenClient, FakeIdTokenValidator>> {
    val impl = DefaultAuthenticationClient(
        clientId = "client-123",
        tokenClient = tokenClient,
        idTokenValidator = validator,
    )
    return impl to (tokenClient to validator)
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
        assertEquals("http://auth0.com/oauth/grant-type/password-realm", params["grant_type"])
        assertEquals("client-123", params["client_id"])
        assertEquals("user", params["username"])
        assertEquals("pw", params["password"])
        assertEquals("db", params["realm"])
        assertEquals("openid", params["scope"])
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

        assertEquals("https://api", deps.first.lastGrant!!.parameters["audience"])
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
}
