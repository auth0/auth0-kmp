package com.auth0.kmp.authentication.request

import com.auth0.kmp.authentication.model.AuthenticatorResponse
import com.auth0.kmp.authentication.model.ClientExtensionResults
import com.auth0.kmp.authentication.model.CredProps
import com.auth0.kmp.authentication.model.PublicKeyCredentials
import com.auth0.kmp.core.annotation.InternalAuth0Api
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
