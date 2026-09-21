package com.auth0.kmp.authentication.model

/**
 * The result of a WebAuthn ceremony run by the caller, sent back to Auth0 to
 * complete a passkey sign-in or registration.
 *
 * The caller obtains this from the platform authenticator; the SDK does not run
 * the ceremony itself. Fields that apply to only one ceremony (registration vs.
 * assertion) are left `null` for the other.
 *
 * @param id the base64url-encoded credential identifier.
 * @param rawId the base64url-encoded raw credential identifier.
 * @param type the credential type, e.g. `public-key`.
 * @param response the authenticator's response for this ceremony.
 * @param authenticatorAttachment how the authenticator is attached, e.g. `platform`.
 * @param clientExtensionResults the results of any requested client extensions.
 */
public data class PublicKeyCredentials(
    val id: String,
    val rawId: String,
    val type: String,
    val response: AuthenticatorResponse,
    val authenticatorAttachment: String? = null,
    val clientExtensionResults: ClientExtensionResults? = null,
)

/**
 * The authenticator's response within a [PublicKeyCredentials].
 *
 * @param clientDataJSON the base64url-encoded client data collected during the ceremony.
 * @param attestationObject the base64url-encoded attestation object (registration only).
 * @param authenticatorData the base64url-encoded authenticator data (assertion only).
 * @param signature the base64url-encoded assertion signature (assertion only).
 * @param userHandle the base64url-encoded user handle (assertion only).
 * @param transports the transports the authenticator supports (registration only).
 */
public data class AuthenticatorResponse(
    val clientDataJSON: String,
    val attestationObject: String? = null,
    val authenticatorData: String? = null,
    val signature: String? = null,
    val userHandle: String? = null,
    val transports: List<String>? = null,
)

/**
 * The results of the WebAuthn client extensions requested during a ceremony.
 *
 * @param credProps the credential-properties extension result.
 */
public data class ClientExtensionResults(
    val credProps: CredProps,
)

/**
 * The credential-properties (`credProps`) extension result.
 *
 * @param rk whether a discoverable (resident) credential was created.
 */
public data class CredProps(
    val rk: Boolean,
)
