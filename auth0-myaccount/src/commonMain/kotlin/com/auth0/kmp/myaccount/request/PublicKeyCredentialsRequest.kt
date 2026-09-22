package com.auth0.kmp.myaccount.request

import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.myaccount.model.AuthenticatorResponse
import com.auth0.kmp.myaccount.model.ClientExtensionResults
import com.auth0.kmp.myaccount.model.PublicKeyCredentials
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@InternalAuth0Api
public data class PublicKeyCredentialsRequest(
    @SerialName("id") val id: String,
    @SerialName("rawId") val rawId: String,
    @SerialName("type") val type: String,
    @SerialName("response") val response: AuthenticatorResponseRequest,
    @SerialName("authenticatorAttachment") val authenticatorAttachment: String? = null,
    @SerialName("clientExtensionResults") val clientExtensionResults: ClientExtensionResultsRequest? = null,
)

@Serializable
@InternalAuth0Api
public data class AuthenticatorResponseRequest(
    @SerialName("clientDataJSON") val clientDataJSON: String,
    @SerialName("attestationObject") val attestationObject: String? = null,
    @SerialName("authenticatorData") val authenticatorData: String? = null,
    @SerialName("signature") val signature: String? = null,
    @SerialName("userHandle") val userHandle: String? = null,
    @SerialName("transports") val transports: List<String>? = null,
)

@Serializable
@InternalAuth0Api
public data class ClientExtensionResultsRequest(
    @SerialName("credProps") val credProps: CredPropsRequest,
)

@Serializable
@InternalAuth0Api
public data class CredPropsRequest(
    @SerialName("rk") val rk: Boolean,
)

@InternalAuth0Api
public fun PublicKeyCredentials.toPublicKeyCredentialsRequest(): PublicKeyCredentialsRequest =
    PublicKeyCredentialsRequest(
        id = id,
        rawId = rawId,
        type = type,
        response = response.toAuthenticatorResponseRequest(),
        authenticatorAttachment = authenticatorAttachment,
        clientExtensionResults = clientExtensionResults?.toClientExtensionResultsRequest(),
    )

@InternalAuth0Api
public fun AuthenticatorResponse.toAuthenticatorResponseRequest(): AuthenticatorResponseRequest =
    AuthenticatorResponseRequest(
        clientDataJSON = clientDataJSON,
        attestationObject = attestationObject,
        authenticatorData = authenticatorData,
        signature = signature,
        userHandle = userHandle,
        transports = transports,
    )

@InternalAuth0Api
public fun ClientExtensionResults.toClientExtensionResultsRequest(): ClientExtensionResultsRequest =
    ClientExtensionResultsRequest(credProps = CredPropsRequest(rk = credProps.rk))

/**
 * Wire DTO for the `authn_response` sent to the enrollment-verification endpoint.
 *
 * Uses [AttestationResponseRequest] for the inner `response` object so that
 * assertion-only fields (`authenticatorData`, `signature`, `userHandle`) are
 * structurally absent from the serialized JSON. The enrollment endpoint enforces
 * `additionalProperties: false` and will reject any extra keys.
 */
@Serializable
@InternalAuth0Api
public data class PasskeyEnrollmentCredentialRequest(
    @SerialName("id") val id: String,
    @SerialName("rawId") val rawId: String,
    @SerialName("type") val type: String,
    @SerialName("response") val response: AttestationResponseRequest,
    @SerialName("authenticatorAttachment") val authenticatorAttachment: String? = null,
    @SerialName("clientExtensionResults") val clientExtensionResults: ClientExtensionResultsRequest? = null,
)

/**
 * Wire DTO for the `response` object inside an attestation (enrollment) credential.
 *
 * Contains only the fields defined by the WebAuthn `AuthenticatorAttestationResponse`:
 * `clientDataJSON`, `attestationObject`, and `transports`. Assertion-only fields
 * (`authenticatorData`, `signature`, `userHandle`) are intentionally absent.
 */
@Serializable
@InternalAuth0Api
public data class AttestationResponseRequest(
    @SerialName("clientDataJSON") val clientDataJSON: String,
    @SerialName("attestationObject") val attestationObject: String? = null,
    @SerialName("transports") val transports: List<String>? = null,
)

/**
 * Maps a [PublicKeyCredentials] credential to the enrollment-specific wire DTO,
 * retaining only the attestation fields required by the verification endpoint.
 */
@InternalAuth0Api
public fun PublicKeyCredentials.toPasskeyEnrollmentCredentialRequest(): PasskeyEnrollmentCredentialRequest =
    PasskeyEnrollmentCredentialRequest(
        id = id,
        rawId = rawId,
        type = type,
        response = AttestationResponseRequest(
            clientDataJSON = response.clientDataJSON,
            attestationObject = response.attestationObject,
            transports = response.transports,
        ),
        authenticatorAttachment = authenticatorAttachment,
        clientExtensionResults = clientExtensionResults?.toClientExtensionResultsRequest(),
    )
