package com.auth0.kmp.authentication.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A challenge issued by Auth0 for registering a new passkey during sign-up.
 *
 * @param authSession the opaque session token echoed back when completing the registration.
 * @param authParamsPublicKey the WebAuthn public-key options the authenticator
 *   needs to create a credential.
 */
@Serializable
public data class PasskeyRegistrationChallenge(
    @SerialName("auth_session") val authSession: String,
    @SerialName("authn_params_public_key") val authParamsPublicKey: AuthnParamsPublicKey,
)

/**
 * The WebAuthn public-key creation options carried by a registration
 * [PasskeyRegistrationChallenge].
 *
 * @param authenticatorSelection constraints on which authenticators may be used.
 * @param challenge the base64url-encoded challenge the authenticator must sign.
 * @param pubKeyCredParams the credential types and signing algorithms the server accepts.
 * @param relyingParty the relying party the credential is created for.
 * @param timeout the time, in milliseconds, the caller is given to respond.
 * @param user the user the credential is being created for.
 */
@Serializable
public data class AuthnParamsPublicKey(
    @SerialName("authenticatorSelection") val authenticatorSelection: AuthenticatorSelection,
    @SerialName("challenge") val challenge: String,
    @SerialName("pubKeyCredParams") val pubKeyCredParams: List<PubKeyCredParam>,
    @SerialName("rp") val relyingParty: RelyingParty,
    @SerialName("timeout") val timeout: Long,
    @SerialName("user") val user: PasskeyUser,
)

/**
 * Constraints on the authenticator used to create a passkey.
 *
 * @param residentKey whether a discoverable (resident) credential is required.
 * @param userVerification the user-verification requirement, e.g. `required`.
 */
@Serializable
public data class AuthenticatorSelection(
    @SerialName("residentKey") val residentKey: String,
    @SerialName("userVerification") val userVerification: String,
)

/**
 * A credential type and signing algorithm the server accepts.
 *
 * @param alg the COSE algorithm identifier.
 * @param type the credential type, e.g. `public-key`.
 */
@Serializable
public data class PubKeyCredParam(
    @SerialName("alg") val alg: Int,
    @SerialName("type") val type: String,
)

/**
 * The relying party a passkey is created for.
 *
 * @param id the relying-party identifier.
 * @param name the human-readable relying-party name.
 */
@Serializable
public data class RelyingParty(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
)

/**
 * The user a passkey is being created for.
 *
 * @param displayName the human-readable name shown to the user.
 * @param id the base64url-encoded user handle.
 * @param name the user's account name, e.g. their email.
 */
@Serializable
public data class PasskeyUser(
    @SerialName("displayName") val displayName: String,
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
)
