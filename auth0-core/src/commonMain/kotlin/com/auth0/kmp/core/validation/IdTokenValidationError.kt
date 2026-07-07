package com.auth0.kmp.core.validation

import com.auth0.kmp.core.error.Auth0Error

/**
 * A failure produced while validating the claims of an ID token.
 */
public sealed interface IdTokenValidationError : Auth0Error {

    /** The ID token could not be decoded as a JWT. */
    public data object CannotDecode : IdTokenValidationError

    /** The `iss` claim is missing or does not match the expected issuer. */
    public data object InvalidIssuer : IdTokenValidationError

    /** The `sub` claim is missing. */
    public data object MissingSubject : IdTokenValidationError

    /** The `aud` claim is missing or does not include the expected audience. */
    public data object InvalidAudience : IdTokenValidationError

    /** The `exp` claim is missing, or the token has expired. */
    public data object Expired : IdTokenValidationError

    /** The `iat` claim is missing. */
    public data object MissingIssuedAt : IdTokenValidationError

    /** The `nonce` claim is missing or does not match the expected nonce. */
    public data object InvalidNonce : IdTokenValidationError

    /** The `azp` claim is missing or does not match the expected authorized party. */
    public data object InvalidAuthorizedParty : IdTokenValidationError

    /** The `auth_time` claim is missing, or the maximum authentication age was exceeded. */
    public data object AuthTimeExceeded : IdTokenValidationError

    /** The `org_id` or `org_name` claim is missing or does not match the expected organization. */
    public data object InvalidOrganization : IdTokenValidationError

    /** The token header declares a signing algorithm other than the expected `RS256`. */
    public data object UnsupportedAlgorithm : IdTokenValidationError

    /** No public key matching the token's `kid` could be found in the JWKS. */
    public data object PublicKeyNotFound : IdTokenValidationError

    /** The token signature did not verify against the resolved public key. */
    public data object InvalidSignature : IdTokenValidationError
}
