package com.auth0.kmp.authentication.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
@Serializable
public data class PublicKeyCredentials(
    @SerialName("id") val id: String,
    @SerialName("rawId") val rawId: String,
    @SerialName("type") val type: String,
    @SerialName("response") val response: AuthenticatorResponse,
    @SerialName("authenticatorAttachment") val authenticatorAttachment: String? = null,
    @SerialName("clientExtensionResults") val clientExtensionResults: ClientExtensionResults? = null,
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
@Serializable
public data class AuthenticatorResponse(
    @SerialName("clientDataJSON") val clientDataJSON: String,
    @SerialName("attestationObject") val attestationObject: String? = null,
    @SerialName("authenticatorData") val authenticatorData: String? = null,
    @SerialName("signature") val signature: String? = null,
    @SerialName("userHandle") val userHandle: String? = null,
    @SerialName("transports") val transports: List<String>? = null,
)

/**
 * The results of the WebAuthn client extensions requested during a ceremony.
 *
 * @param credProps the credential-properties extension result.
 */
@Serializable
public data class ClientExtensionResults(
    @SerialName("credProps") val credProps: CredProps,
)

/**
 * The credential-properties (`credProps`) extension result.
 *
 * @param rk whether a discoverable (resident) credential was created.
 */
@Serializable
public data class CredProps(
    @SerialName("rk") val rk: Boolean,
)
