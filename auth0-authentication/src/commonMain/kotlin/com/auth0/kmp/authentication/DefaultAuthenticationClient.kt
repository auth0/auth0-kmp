package com.auth0.kmp.authentication

import com.auth0.kmp.authentication.error.AuthenticationError
import com.auth0.kmp.authentication.error.toAuthenticationError
import com.auth0.kmp.authentication.request.LoginRequest
import com.auth0.kmp.authentication.response.TokenResponse
import com.auth0.kmp.authentication.response.toCredentials
import com.auth0.kmp.authentication.validation.IdTokenValidator
import com.auth0.kmp.core.model.Credentials
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.networking.NetworkClient
import com.auth0.kmp.networking.request.HttpMethod
import com.auth0.kmp.networking.request.NetworkRequest
import com.auth0.kmp.networking.transport.json
import kotlin.time.Clock

internal class DefaultAuthenticationClient(
    private val clientId: String,
    private val networkClient: NetworkClient,
    private val idTokenValidator: IdTokenValidator,
    private val clock: Clock
) : AuthenticationClient {

    override suspend fun login(
        usernameOrEmail: String,
        password: String,
        realm: String,
        audience: String?,
        scope: String
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

        val body = LoginRequest(
            username = usernameOrEmail,
            password = password,
            realm = realm,
            clientId = clientId,
            scope = scope,
            audience = audience
        )

        val request = NetworkRequest(
            method = HttpMethod.POST,
            path = "/oauth/token",
            body = json.encodeToString(body)
        )

        return when (val result = networkClient.request(request) {
            json.decodeFromString<TokenResponse>(it)
        }) {
            is Result.Failure -> Result.Failure(result.error.toAuthenticationError())
            is Result.Success -> {
                val credentials = result.data.toCredentials(clock)
                val validationError = idTokenValidator.validate(credentials.idToken)
                if (validationError != null) {
                    Result.Failure(AuthenticationError.IdTokenValidation(validationError))
                } else {
                    Result.Success(credentials)
                }
            }

        }

    }

    override fun close() {
        networkClient.close()
    }
}