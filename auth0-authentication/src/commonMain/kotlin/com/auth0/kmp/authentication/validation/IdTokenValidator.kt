package com.auth0.kmp.authentication.validation

/**
 * Validates the ID token returned by a credentials-yielding authentication
 * operation.
 */
internal interface IdTokenValidator {

    /**
     * Validates [idToken].
     *
     * @param idToken the raw ID token string to validate.
     * @return `null` when the token is valid, or the [IdTokenValidationError]
     *   describing the first check that failed.
     */
    fun validate(idToken: String): IdTokenValidationError?
}
