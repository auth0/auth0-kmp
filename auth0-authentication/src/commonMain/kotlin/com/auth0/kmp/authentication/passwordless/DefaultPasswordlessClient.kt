package com.auth0.kmp.authentication.passwordless

import com.auth0.kmp.authentication.error.AuthenticationError
import com.auth0.kmp.authentication.foldToCredentials
import com.auth0.kmp.authentication.jsonBody
import com.auth0.kmp.authentication.passwordless.model.DeliveryMethod
import com.auth0.kmp.authentication.passwordless.model.PasswordlessChallenge
import com.auth0.kmp.authentication.passwordless.model.PasswordlessType
import com.auth0.kmp.authentication.passwordless.response.PasswordlessChallengeResponse
import com.auth0.kmp.authentication.passwordless.response.toPasswordlessChallenge
import com.auth0.kmp.authentication.post
import com.auth0.kmp.authentication.request.PasswordlessChallengeGrant
import com.auth0.kmp.authentication.request.PasswordlessLoginGrant
import com.auth0.kmp.core.RequestOptions
import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.model.Credentials
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.core.token.TokenClient
import com.auth0.kmp.core.validation.IdTokenValidator
import com.auth0.kmp.networking.NetworkClient
import com.auth0.kmp.networking.transport.json
import kotlinx.serialization.json.put

@OptIn(InternalAuth0Api::class)
internal class DefaultPasswordlessClient(
    private val clientId: String,
    private val tokenClient: TokenClient,
    private val idTokenValidator: IdTokenValidator,
    private val networkClient: NetworkClient,
) : PasswordlessClient {

    override suspend fun startWithEmail(
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

        return networkClient.post("/passwordless/start", body, options) { }
    }

    override suspend fun startWithSms(
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

        return networkClient.post("/passwordless/start", body, options) { }
    }

    override suspend fun loginWithEmail(
        email: String,
        code: String,
        connection: String,
        audience: String?,
        scope: String,
        options: RequestOptions,
    ): Result<Credentials, AuthenticationError> =
        loginWithPasswordlessCode(email, code, connection, audience, scope, options)

    override suspend fun loginWithSms(
        phoneNumber: String,
        code: String,
        connection: String,
        audience: String?,
        scope: String,
        options: RequestOptions,
    ): Result<Credentials, AuthenticationError> =
        loginWithPasswordlessCode(phoneNumber, code, connection, audience, scope, options)

    private suspend fun loginWithPasswordlessCode(
        username: String,
        code: String,
        connection: String,
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
        if (connection.isBlank()) {
            return Result.Failure(AuthenticationError.InvalidInput("connection must not be blank"))
        }

        val grant = PasswordlessLoginGrant(
            username = username,
            otp = code,
            realm = connection,
            clientId = clientId,
            scope = scope,
            audience = audience,
            extraParameters = options.parameters,
        )

        return tokenClient.fetchToken(grant, options.headers, options.retryPolicy)
            .foldToCredentials(idTokenValidator, validateIdToken = true)
    }

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
            put("allow_signup", allowSignup)
        }

        return networkClient.post("/otp/challenge", body, options) {
            json.decodeFromString<PasswordlessChallengeResponse>(it).toPasswordlessChallenge()
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
            put("allow_signup", allowSignup)
        }

        return networkClient.post("/otp/challenge", body, options) {
            json.decodeFromString<PasswordlessChallengeResponse>(it).toPasswordlessChallenge()
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
}
