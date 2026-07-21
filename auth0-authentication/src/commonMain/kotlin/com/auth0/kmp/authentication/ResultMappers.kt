package com.auth0.kmp.authentication

import com.auth0.kmp.authentication.error.AuthenticationError
import com.auth0.kmp.authentication.error.toAuthenticationError
import com.auth0.kmp.core.error.TransportError
import com.auth0.kmp.core.model.Credentials
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.core.result.fold
import com.auth0.kmp.core.validation.IdTokenValidator

internal fun <T> Result<T, TransportError>.toAuthResult(): Result<T, AuthenticationError> =
    fold({ Result.Success(it) }, { Result.Failure(it.toAuthenticationError()) })

internal fun Result<Credentials, TransportError>.foldToCredentials(
    idTokenValidator: IdTokenValidator,
    validateIdToken: Boolean,
): Result<Credentials, AuthenticationError> = fold({ credentials ->
    if (validateIdToken) {
        val validationError = idTokenValidator.validate(credentials.idToken)
        if (validationError != null) {
            return@fold Result.Failure(AuthenticationError.IdTokenValidation(validationError))
        }
    }
    Result.Success(credentials)
}, { error ->
    Result.Failure(error.toAuthenticationError())
})
