package com.auth0.kmp.myaccount.response

import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.myaccount.model.MyAccountProfile
import com.auth0.kmp.myaccount.model.ProfileFieldAccess
import com.auth0.kmp.myaccount.model.ProfileFieldPolicy
import com.auth0.kmp.myaccount.model.ProfileFieldSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
@InternalAuth0Api
public data class MyAccountProfileResponse(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("given_name") val givenName: String? = null,
    @SerialName("family_name") val familyName: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("nickname") val nickname: String? = null,
    @SerialName("picture") val picture: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("email_verified") val emailVerified: Boolean? = null,
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("phone_verified") val phoneVerified: Boolean? = null,
    @SerialName("username") val username: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("profile_policy") val profilePolicy: Map<String, ProfileFieldPolicyResponse>? = null,
)

/**
 * `access` and `source` are kept as raw strings so that a value this SDK does not
 * know fails nothing: kotlinx.serialization throws on unknown enum values.
 */
@Serializable
@InternalAuth0Api
public data class ProfileFieldPolicyResponse(
    @SerialName("access") val access: String? = null,
    @SerialName("source") val source: String? = null,
    @SerialName("reason") val reason: String? = null,
)

@InternalAuth0Api
public fun MyAccountProfileResponse.toMyAccountProfile(): MyAccountProfile =
    MyAccountProfile(
        userId = userId,
        givenName = givenName,
        familyName = familyName,
        name = name,
        nickname = nickname,
        picture = picture,
        email = email,
        emailVerified = emailVerified,
        phoneNumber = phoneNumber,
        phoneVerified = phoneVerified,
        username = username,
        createdAt = createdAt?.let(Instant::parse),
        updatedAt = updatedAt?.let(Instant::parse),
        profilePolicy = profilePolicy?.mapValues { (_, policy) -> policy.toProfileFieldPolicy() },
    )

@InternalAuth0Api
public fun ProfileFieldPolicyResponse.toProfileFieldPolicy(): ProfileFieldPolicy =
    ProfileFieldPolicy(
        access = when (access) {
            "read_only" -> ProfileFieldAccess.READ_ONLY
            "read_write" -> ProfileFieldAccess.READ_WRITE
            else -> null
        },
        source = when (source) {
            "user" -> ProfileFieldSource.USER
            "idp" -> ProfileFieldSource.IDP
            "system" -> ProfileFieldSource.SYSTEM
            else -> null
        },
        reason = reason,
    )
