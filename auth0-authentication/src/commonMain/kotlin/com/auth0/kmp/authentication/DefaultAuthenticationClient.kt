package com.auth0.kmp.authentication

import com.auth0.kmp.authentication.error.AuthenticationError
import com.auth0.kmp.authentication.model.DatabaseUser
import com.auth0.kmp.authentication.model.PasskeyLoginChallenge
import com.auth0.kmp.authentication.model.PasskeyRegistrationChallenge
import com.auth0.kmp.authentication.model.PublicKeyCredentials
import com.auth0.kmp.authentication.model.SignupProfile
import com.auth0.kmp.authentication.model.UserProfile
import com.auth0.kmp.authentication.request.PasskeyGrant
import com.auth0.kmp.authentication.request.PasswordRealmGrant
import com.auth0.kmp.core.RequestOptions
import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.model.Credentials
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.core.token.RefreshTokenGrant
import com.auth0.kmp.core.token.TokenClient
import com.auth0.kmp.core.validation.IdTokenValidator
import com.auth0.kmp.networking.NetworkClient
import com.auth0.kmp.networking.request.HttpMethod
import com.auth0.kmp.networking.request.NetworkRequest
import com.auth0.kmp.networking.transport.json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

@OptIn(InternalAuth0Api::class)
internal class DefaultAuthenticationClient(
    private val clientId: String,
    private val tokenClient: TokenClient,
    private val idTokenValidator: IdTokenValidator,
    private val networkClient: NetworkClient,
) : AuthenticationClient {

    override suspend fun login(
        usernameOrEmail: String,
        password: String,
        realm: String,
        audience: String?,
        scope: String,
        options: RequestOptions,
    ): Result<Credentials, AuthenticationError> {
        if (usernameOrEmail.isBlank()) {
            return Result.Failure(AuthenticationError.InvalidInput("usernameOrEmail must not be blank"))
        }
        if (password.isBlank()) {
            return Result.Failure(AuthenticationError.InvalidInput("password must not be blank"))
        }
        if (realm.isBlank()) {
            return Result.Failure(AuthenticationError.InvalidInput("realm must not be blank"))
        }

        val grant = PasswordRealmGrant(
            usernameOrEmail = usernameOrEmail,
            password = password,
            realm = realm,
            clientId = clientId,
            scope = scope,
            audience = audience,
            extraParameters = options.parameters,
        )

        return tokenClient.fetchToken(grant, options.headers, options.retryPolicy)
            .foldToCredentials(idTokenValidator, validateIdToken = true)
    }

    override suspend fun createUser(
        profile: SignupProfile,
        password: String,
        connection: String,
        userMetadata: Map<String, String>,
        options: RequestOptions,
    ): Result<DatabaseUser, AuthenticationError> {
        if (profile.email.isNullOrBlank()) {
            return Result.Failure(AuthenticationError.InvalidInput("email must not be blank"))
        }
        if (password.isBlank()) {
            return Result.Failure(AuthenticationError.InvalidInput("password must not be blank"))
        }
        if (connection.isBlank()) {
            return Result.Failure(AuthenticationError.InvalidInput("connection must not be blank"))
        }

        val body = jsonBody(options) {
            json.encodeToJsonElement(profile).jsonObject.forEach { (key, value) -> put(key, value) }
            put("password", password)
            put("connection", connection)
            put("client_id", clientId)
            if (userMetadata.isNotEmpty()) put("user_metadata", json.encodeToJsonElement(userMetadata))
        }

        return post("/dbconnections/signup", body, options) {
            json.decodeFromString<DatabaseUser>(it)
        }
    }

    override suspend fun resetPassword(
        email: String,
        connection: String,
        options: RequestOptions,
    ): Result<Unit, AuthenticationError> {
        if (email.isBlank()) {
            return Result.Failure(AuthenticationError.InvalidInput("email must not be blank"))
        }
        if (connection.isBlank()) {
            return Result.Failure(AuthenticationError.InvalidInput("connection must not be blank"))
        }

        val body = jsonBody(options) {
            put("email", email)
            put("client_id", clientId)
            put("connection", connection)
        }

        return post("/dbconnections/change_password", body, options) { }
    }

    override suspend fun userInfo(
        accessToken: String,
        tokenType: String,
        options: RequestOptions,
    ): Result<UserProfile, AuthenticationError> {
        if (accessToken.isBlank()) {
            return Result.Failure(AuthenticationError.InvalidInput("accessToken must not be blank"))
        }
        if (tokenType.isBlank()) {
            return Result.Failure(AuthenticationError.InvalidInput("tokenType must not be blank"))
        }

        val request = NetworkRequest(
            method = HttpMethod.GET,
            path = "/userinfo",
            headers = options.headers + ("Authorization" to "$tokenType $accessToken"),
            query = options.parameters,
        )

        return networkClient.request(request, options.retryPolicy) {
            json.decodeFromString<UserProfile>(it)
        }.toAuthResult()
    }

    override suspend fun revoke(
        refreshToken: String,
        options: RequestOptions,
    ): Result<Unit, AuthenticationError> {
        if (refreshToken.isBlank()) {
            return Result.Failure(AuthenticationError.InvalidInput("refreshToken must not be blank"))
        }

        val body = jsonBody(options) {
            put("client_id", clientId)
            put("token", refreshToken)
        }

        return post("/oauth/revoke", body, options) { }
    }

    override suspend fun renew(
        refreshToken: String,
        audience: String?,
        scope: String?,
        options: RequestOptions,
    ): Result<Credentials, AuthenticationError> {
        if (refreshToken.isBlank()) {
            return Result.Failure(AuthenticationError.InvalidInput("refreshToken must not be blank"))
        }

        val grant = RefreshTokenGrant(
            refreshToken = refreshToken,
            clientId = clientId,
            audience = audience,
            scope = scope,
            extraParameters = options.parameters,
        )

        return tokenClient.fetchToken(grant, options.headers, options.retryPolicy)
            .foldToCredentials(idTokenValidator, validateIdToken = false)
    }

    override suspend fun passkeyLoginChallenge(
        realm: String?,
        organization: String?,
        options: RequestOptions,
    ): Result<PasskeyLoginChallenge, AuthenticationError> {
        val body = jsonBody(options) {
            put("client_id", clientId)
            realm?.let { put("realm", it) }
            organization?.let { put("organization", it) }
        }

        return post("/passkey/challenge", body, options) {
            json.decodeFromString<PasskeyLoginChallenge>(it)
        }
    }

    override suspend fun passkeySignupChallenge(
        profile: SignupProfile,
        userMetadata: Map<String, String>,
        realm: String?,
        organization: String?,
        options: RequestOptions,
    ): Result<PasskeyRegistrationChallenge, AuthenticationError> {
        val body = jsonBody(options) {
            put("client_id", clientId)
            put("user_profile", json.encodeToJsonElement(profile))
            if (userMetadata.isNotEmpty()) put("user_metadata", json.encodeToJsonElement(userMetadata))
            realm?.let { put("realm", it) }
            organization?.let { put("organization", it) }
        }

        return post("/passkey/register", body, options) {
            json.decodeFromString<PasskeyRegistrationChallenge>(it)
        }
    }

    override suspend fun loginWithPasskey(
        authSession: String,
        authResponse: PublicKeyCredentials,
        realm: String?,
        organization: String?,
        audience: String?,
        scope: String,
        options: RequestOptions,
    ): Result<Credentials, AuthenticationError> {
        if (authSession.isBlank()) {
            return Result.Failure(AuthenticationError.InvalidInput("authSession must not be blank"))
        }

        val grant = PasskeyGrant(
            authSession = authSession,
            authResponse = authResponse,
            clientId = clientId,
            realm = realm,
            organization = organization,
            scope = scope,
            audience = audience,
            extraParameters = options.parameters,
        )

        return tokenClient.fetchToken(grant, options.headers, options.retryPolicy)
            .foldToCredentials(idTokenValidator, validateIdToken = true)
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

    private suspend fun <T> post(
        path: String,
        body: String,
        options: RequestOptions,
        deserialize: (String) -> T,
    ): Result<T, AuthenticationError> {
        val request = NetworkRequest(
            method = HttpMethod.POST,
            path = path,
            headers = options.headers,
            body = body,
        )
        return networkClient.request(request, options.retryPolicy, deserialize).toAuthResult()
    }
}
