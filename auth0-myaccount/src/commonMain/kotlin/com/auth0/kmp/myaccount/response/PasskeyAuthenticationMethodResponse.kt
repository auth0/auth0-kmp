package com.auth0.kmp.myaccount.response

import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.myaccount.model.PasskeyAuthenticationMethod
import com.auth0.kmp.myaccount.model.PasskeyCredential
import com.auth0.kmp.myaccount.model.CredentialDeviceType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
@InternalAuth0Api
public data class PasskeyAuthenticationMethodResponse(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("usage") val usage: List<String> = emptyList(),
    @SerialName("identity_user_id") val identityUserId: String? = null,
    @SerialName("user_agent") val userAgent: String? = null,
    @SerialName("aaguid") val aaguid: String,
    @SerialName("relying_party_id") val relyingPartyId: String,
    @SerialName("key_id") val keyId: String,
    @SerialName("public_key") val publicKey: String,
    @SerialName("user_handle") val userHandle: String,
    @SerialName("credential_device_type") val credentialDeviceType: CredentialDeviceTypeResponse,
    @SerialName("credential_backed_up") val credentialBackedUp: Boolean,
    @SerialName("transports") val transports: List<String>? = null,
)

@Serializable
@InternalAuth0Api
public enum class CredentialDeviceTypeResponse {
    @SerialName("single_device")
    SINGLE_DEVICE,

    @SerialName("multi_device")
    MULTI_DEVICE,
}

@InternalAuth0Api
public fun PasskeyAuthenticationMethodResponse.toPasskeyAuthenticationMethod(): PasskeyAuthenticationMethod =
    PasskeyAuthenticationMethod(
        id = id,
        type = type,
        userIdentityId = identityUserId,
        userAgent = userAgent,
        credential = PasskeyCredential(
            keyId = keyId,
            publicKey = publicKey,
            userHandle = userHandle,
            deviceType = credentialDeviceType.toCredentialDeviceType(),
            isBackedUp = credentialBackedUp,
            transports = transports,
        ),
        createdAt = Instant.parse(createdAt),
        aaguid = aaguid,
        relyingPartyId = relyingPartyId,
        usage = usage,
    )

@InternalAuth0Api
public fun CredentialDeviceTypeResponse.toCredentialDeviceType(): CredentialDeviceType =
    when (this) {
        CredentialDeviceTypeResponse.SINGLE_DEVICE -> CredentialDeviceType.SINGLE_DEVICE
        CredentialDeviceTypeResponse.MULTI_DEVICE -> CredentialDeviceType.MULTI_DEVICE
    }
