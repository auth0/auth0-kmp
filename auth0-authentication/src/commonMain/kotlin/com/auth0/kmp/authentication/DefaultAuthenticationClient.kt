package com.auth0.kmp.authentication

import com.auth0.kmp.authentication.error.AuthenticationError
import com.auth0.kmp.authentication.error.toAuthenticationError
import com.auth0.kmp.authentication.request.PasswordRealmGrant
import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.model.Credentials
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.core.result.fold
import com.auth0.kmp.core.token.TokenClient
import com.auth0.kmp.core.validation.IdTokenValidator

@OptIn(InternalAuth0Api::class)
internal class DefaultAuthenticationClient(
    private val clientId: String,
    private val tokenClient: TokenClient,
    private val idTokenValidator: IdTokenValidator,
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

        val grant = PasswordRealmGrant(
            usernameOrEmail = usernameOrEmail,
            password = password,
            realm = realm,
            clientId = clientId,
            scope = scope,
            audience = audience,
        )

        return tokenClient.fetchToken(grant).fold({ data ->
            val validationError = idTokenValidator.validate(data.idToken)
            if (validationError != null) {
                Result.Failure(AuthenticationError.IdTokenValidation(validationError))
            } else {
                Result.Success(data)
            }
        }, { error ->
            Result.Failure(error.toAuthenticationError())
        })
    }
}
