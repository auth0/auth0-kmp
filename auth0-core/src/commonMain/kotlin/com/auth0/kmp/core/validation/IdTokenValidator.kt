package com.auth0.kmp.core.validation

import com.auth0.kmp.core.annotation.InternalAuth0Api

/**
 * Validates the ID token returned by a credentials-yielding authentication
 * operation.
 */
@InternalAuth0Api
public interface IdTokenValidator {

    /**
     * Validates [idToken].
     *
     * @param idToken the raw ID token string to validate.
     * @param context per-request inputs that enable the optional claim checks;
     *   a default context enables none of them.
     * @return `null` when the token is valid, or the [IdTokenValidationError]
     *   describing the first check that failed.
     */
    public fun validate(
        idToken: String,
        context: IdTokenValidationContext = IdTokenValidationContext(),
    ): IdTokenValidationError?
}
