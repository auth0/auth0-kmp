package com.auth0.kmp.authentication.response

import com.auth0.kmp.authentication.model.DatabaseUser
import com.auth0.kmp.core.annotation.InternalAuth0Api
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@InternalAuth0Api
public data class DatabaseUserResponse(
    @SerialName("_id") val id: String,
    @SerialName("email") val email: String? = null,
    @SerialName("email_verified") val emailVerified: Boolean? = null,
    @SerialName("username") val username: String? = null,
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("phone_verified") val phoneVerified: Boolean? = null,
    @SerialName("given_name") val givenName: String? = null,
    @SerialName("family_name") val familyName: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("nickname") val nickname: String? = null,
    @SerialName("picture") val picture: String? = null,
)

@InternalAuth0Api
public fun DatabaseUserResponse.toDatabaseUser(): DatabaseUser =
    DatabaseUser(
        id = id,
        email = email,
        emailVerified = emailVerified,
        username = username,
        phoneNumber = phoneNumber,
        phoneVerified = phoneVerified,
        givenName = givenName,
        familyName = familyName,
        name = name,
        nickname = nickname,
        picture = picture,
    )
