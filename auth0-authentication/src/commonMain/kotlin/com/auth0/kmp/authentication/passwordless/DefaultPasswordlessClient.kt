package com.auth0.kmp.authentication.passwordless

import com.auth0.kmp.authentication.error.AuthenticationError
import com.auth0.kmp.authentication.foldToCredentials
import com.auth0.kmp.authentication.model.DeliveryMethod
import com.auth0.kmp.authentication.model.PasswordlessChallenge
import com.auth0.kmp.authentication.model.PasswordlessType
import com.auth0.kmp.authentication.request.PasswordlessChallengeGrant
import com.auth0.kmp.authentication.request.PasswordlessLoginGrant
import com.auth0.kmp.authentication.toAuthResult
import com.auth0.kmp.core.RequestOptions
import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.model.Credentials
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.core.token.TokenClient
import com.auth0.kmp.core.validation.IdTokenValidator
import com.auth0.kmp.networking.NetworkClient
import com.auth0.kmp.networking.request.HttpMethod
import com.auth0.kmp.networking.request.NetworkRequest
import com.auth0.kmp.networking.transport.json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(InternalAuth0Api::class)
internal class DefaultPasswordlessClient(
    private val clientId: String,
    private val tokenClient: TokenClient,
    private val idTokenValidator: IdTokenValidator,
    private val networkClient: NetworkClient,
) : PasswordlessClient {

    // ─── Classic passwordless flow ──────────────────────────────────────────

    override suspend fun passwordlessWithEmail(
        email: String,
        type: PasswordlessType,
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
            put("client_id", clientId)
            put("connection", connection)
            put("email", email)
            put("send", type.value)
        }

        return post("/passwordless/start", body, options) { }
    }

    override suspend fun passwordlessWithSMS(
        phoneNumber: String,
        type: PasswordlessType,
        connection: String,
        options: RequestOptions,
    ): Result<Unit, AuthenticationError> {
        if (phoneNumber.isBlank()) {
            return Result.Failure(AuthenticationError.InvalidInput("phoneNumber must not be blank"))
        }
        if (connection.isBlank()) {
            return Result.Failure(AuthenticationError.InvalidInput("connection must not be blank"))
        }

        val body = jsonBody(options) {
            put("client_id", clientId)
            put("connection", connection)
            put("phone_number", phoneNumber)
            put("send", type.value)
        }

        return post("/passwordless/start", body, options) { }
    }

    override suspend fun loginWithEmail(
        email: String,
        code: String,
        realm: String,
        audience: String?,
        scope: String,
        options: RequestOptions,
    ): Result<Credentials, AuthenticationError> =
        loginWithPasswordlessCode(email, code, realm, audience, scope, options)

    override suspend fun loginWithPhoneNumber(
        phoneNumber: String,
        code: String,
        realm: String,
        audience: String?,
        scope: String,
        options: RequestOptions,
    ): Result<Credentials, AuthenticationError> =
        loginWithPasswordlessCode(phoneNumber, code, realm, audience, scope, options)

    private suspend fun loginWithPasswordlessCode(
        username: String,
        code: String,
        realm: String,
        audience: String?,
        scope: String,
        options: RequestOptions,
    ): Result<Credentials, AuthenticationError> {
        if (username.isBlank()) {
            return Result.Failure(AuthenticationError.InvalidInput("username must not be blank"))
        }
        if (code.isBlank()) {
            return Result.Failure(AuthenticationError.InvalidInput("code must not be blank"))
        }
        if (realm.isBlank()) {
            return Result.Failure(AuthenticationError.InvalidInput("realm must not be blank"))
        }

        val grant = PasswordlessLoginGrant(
            username = username,
            otp = code,
            realm = realm,
            clientId = clientId,
            scope = scope,
            audience = audience,
            extraParameters = options.parameters,
        )

        return tokenClient.fetchToken(grant, options.headers, options.retryPolicy)
            .foldToCredentials(idTokenValidator, validateIdToken = true)
    }

    // ─── DB-connection OTP flow (Early Access) ──────────────────────────────

    override suspend fun challengeWithEmail(
        email: String,
        connection: String,
        allowSignup: Boolean,
        options: RequestOptions,
    ): Result<PasswordlessChallenge, AuthenticationError> {
        if (email.isBlank()) {
            return Result.Failure(AuthenticationError.InvalidInput("email must not be blank"))
        }
        if (connection.isBlank()) {
            return Result.Failure(AuthenticationError.InvalidInput("connection must not be blank"))
        }

        val body = jsonBody(options) {
            put("client_id", clientId)
            put("connection", connection)
            put("email", email)
            put("allow_signup", allowSignup.toString())
        }

        return post("/otp/challenge", body, options) {
            json.decodeFromString<PasswordlessChallenge>(it)
        }
    }

    override suspend fun challengeWithPhoneNumber(
        phoneNumber: String,
        connection: String,
        deliveryMethod: DeliveryMethod,
        allowSignup: Boolean,
        options: RequestOptions,
    ): Result<PasswordlessChallenge, AuthenticationError> {
        if (phoneNumber.isBlank()) {
            return Result.Failure(AuthenticationError.InvalidInput("phoneNumber must not be blank"))
        }
        if (connection.isBlank()) {
            return Result.Failure(AuthenticationError.InvalidInput("connection must not be blank"))
        }

        val body = jsonBody(options) {
            put("client_id", clientId)
            put("connection", connection)
            put("phone_number", phoneNumber)
            put("delivery_method", deliveryMethod.value)
            put("allow_signup", allowSignup.toString())
        }

        return post("/otp/challenge", body, options) {
            json.decodeFromString<PasswordlessChallenge>(it)
        }
    }

    override suspend fun loginWithOTP(
        challenge: PasswordlessChallenge,
        otp: String,
        audience: String?,
        scope: String,
        options: RequestOptions,
    ): Result<Credentials, AuthenticationError> {
        if (otp.isBlank()) {
            return Result.Failure(AuthenticationError.InvalidInput("otp must not be blank"))
        }

        val grant = PasswordlessChallengeGrant(
            authSession = challenge.authSession,
            otp = otp,
            clientId = clientId,
            scope = scope,
            audience = audience,
            extraParameters = options.parameters,
        )

        return tokenClient.fetchToken(grant, options.headers, options.retryPolicy)
            .foldToCredentials(idTokenValidator, validateIdToken = true)
    }

    // ─── Shared helpers ─────────────────────────────────────────────────────

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
