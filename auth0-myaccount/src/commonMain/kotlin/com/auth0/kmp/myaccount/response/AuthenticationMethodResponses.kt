package com.auth0.kmp.myaccount.response

import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.myaccount.model.EmailAuthenticationMethod
import com.auth0.kmp.myaccount.model.PasswordAuthenticationMethod
import com.auth0.kmp.myaccount.model.PhoneAuthenticationMethod
import com.auth0.kmp.myaccount.model.PhoneAuthenticationMethodType
import com.auth0.kmp.myaccount.model.PushAuthenticationMethod
import com.auth0.kmp.myaccount.model.RecoveryCodeAuthenticationMethod
import com.auth0.kmp.myaccount.model.TotpAuthenticationMethod
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
@InternalAuth0Api
public data class TotpAuthenticationMethodResponse(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("usage") val usage: List<String> = emptyList(),
    @SerialName("confirmed") val confirmed: Boolean? = null,
    @SerialName("last_auth_at") val lastAuthAt: String? = null,
    @SerialName("name") val name: String? = null,
)

@Serializable
@InternalAuth0Api
public data class PushAuthenticationMethodResponse(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("usage") val usage: List<String> = emptyList(),
    @SerialName("confirmed") val confirmed: Boolean? = null,
    @SerialName("last_auth_at") val lastAuthAt: String? = null,
    @SerialName("name") val name: String? = null,
)

@Serializable
@InternalAuth0Api
public data class RecoveryCodeAuthenticationMethodResponse(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("usage") val usage: List<String> = emptyList(),
    @SerialName("confirmed") val confirmed: Boolean? = null,
    @SerialName("last_auth_at") val lastAuthAt: String? = null,
    @SerialName("name") val name: String? = null,
)

@Serializable
@InternalAuth0Api
public data class EmailAuthenticationMethodResponse(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("usage") val usage: List<String> = emptyList(),
    @SerialName("confirmed") val confirmed: Boolean? = null,
    @SerialName("last_auth_at") val lastAuthAt: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("email") val email: String,
)

@Serializable
@InternalAuth0Api
public data class PhoneAuthenticationMethodResponse(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("usage") val usage: List<String> = emptyList(),
    @SerialName("confirmed") val confirmed: Boolean? = null,
    @SerialName("last_auth_at") val lastAuthAt: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("preferred_authentication_method")
    val preferredAuthenticationMethod: PhoneAuthenticationMethodTypeResponse? = null,
)

@Serializable
@InternalAuth0Api
public data class PasswordAuthenticationMethodResponse(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("usage") val usage: List<String> = emptyList(),
    @SerialName("identity_user_id") val identityUserId: String? = null,
    @SerialName("last_password_reset") val lastPasswordReset: String? = null,
)

@Serializable
@InternalAuth0Api
public enum class PhoneAuthenticationMethodTypeResponse {
    @SerialName("sms")
    SMS,

    @SerialName("voice")
    VOICE,
}

@InternalAuth0Api
public fun TotpAuthenticationMethodResponse.toTotpAuthenticationMethod(): TotpAuthenticationMethod =
    TotpAuthenticationMethod(
        id = id,
        type = type,
        usage = usage,
        createdAt = Instant.parse(createdAt),
        confirmed = confirmed,
        lastAuthAt = lastAuthAt?.let { Instant.parse(it) },
        name = name,
    )

@InternalAuth0Api
public fun PushAuthenticationMethodResponse.toPushAuthenticationMethod(): PushAuthenticationMethod =
    PushAuthenticationMethod(
        id = id,
        type = type,
        usage = usage,
        createdAt = Instant.parse(createdAt),
        confirmed = confirmed,
        lastAuthAt = lastAuthAt?.let { Instant.parse(it) },
        name = name,
    )

@InternalAuth0Api
public fun RecoveryCodeAuthenticationMethodResponse.toRecoveryCodeAuthenticationMethod(): RecoveryCodeAuthenticationMethod =
    RecoveryCodeAuthenticationMethod(
        id = id,
        type = type,
        usage = usage,
        createdAt = Instant.parse(createdAt),
        confirmed = confirmed,
        lastAuthAt = lastAuthAt?.let { Instant.parse(it) },
        name = name,
    )

@InternalAuth0Api
public fun EmailAuthenticationMethodResponse.toEmailAuthenticationMethod(): EmailAuthenticationMethod =
    EmailAuthenticationMethod(
        id = id,
        type = type,
        usage = usage,
        createdAt = Instant.parse(createdAt),
        confirmed = confirmed,
        lastAuthAt = lastAuthAt?.let { Instant.parse(it) },
        name = name,
        email = email,
    )

@InternalAuth0Api
public fun PhoneAuthenticationMethodResponse.toPhoneAuthenticationMethod(): PhoneAuthenticationMethod =
    PhoneAuthenticationMethod(
        id = id,
        type = type,
        usage = usage,
        createdAt = Instant.parse(createdAt),
        confirmed = confirmed,
        lastAuthAt = lastAuthAt?.let { Instant.parse(it) },
        name = name,
        phoneNumber = phoneNumber,
        preferredAuthenticationMethod = preferredAuthenticationMethod?.toPhoneAuthenticationMethodType(),
    )

@InternalAuth0Api
public fun PasswordAuthenticationMethodResponse.toPasswordAuthenticationMethod(): PasswordAuthenticationMethod =
    PasswordAuthenticationMethod(
        id = id,
        type = type,
        usage = usage,
        createdAt = Instant.parse(createdAt),
        identityUserId = identityUserId,
        lastPasswordReset = lastPasswordReset?.let { Instant.parse(it) },
    )

@InternalAuth0Api
public fun PhoneAuthenticationMethodTypeResponse.toPhoneAuthenticationMethodType(): PhoneAuthenticationMethodType =
    when (this) {
        PhoneAuthenticationMethodTypeResponse.SMS -> PhoneAuthenticationMethodType.SMS
        PhoneAuthenticationMethodTypeResponse.VOICE -> PhoneAuthenticationMethodType.VOICE
    }
