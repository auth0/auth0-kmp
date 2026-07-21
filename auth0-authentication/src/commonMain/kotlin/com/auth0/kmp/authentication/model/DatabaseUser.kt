package com.auth0.kmp.authentication.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A user created in a database connection via sign-up.
 *
 * Only [id] is guaranteed to be present. Every other field is returned only
 * when the connection is configured to capture it and a value was supplied.
 *
 * @param id the unique identifier of the created user.
 * @param email the email address the user was created with.
 * @param emailVerified whether the user's email address has been verified.
 * @param username the user's username.
 * @param phoneNumber the user's phone number.
 * @param phoneVerified whether the user's phone number has been verified.
 * @param givenName the user's given (first) name.
 * @param familyName the user's family (last) name.
 * @param name the user's display name.
 * @param nickname the user's preferred nickname.
 * @param picture a URL pointing to the user's profile picture.
 */
@Serializable
public data class DatabaseUser(
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
