package com.auth0.kmp.webauth.validation

import com.auth0.kmp.core.validation.IdTokenValidationError

/**
 * Verifies the cryptographic signature of an ID token obtained through the Web
 * Auth flow.
 */
internal interface IdTokenSignatureValidator {

    /**
     * Verifies the signature of [idToken].
     *
     * @param idToken the raw ID token string to verify.
     * @return `null` when the signature is valid, or the
     *   [IdTokenValidationError] describing why verification failed.
     */
    suspend fun verify(idToken: String): IdTokenValidationError?
}
