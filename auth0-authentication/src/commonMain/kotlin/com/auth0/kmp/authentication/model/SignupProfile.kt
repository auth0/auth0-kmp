package com.auth0.kmp.authentication.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The structured profile of a user being registered, shared by database
 * sign-up and passkey sign-up.
 *
 * Each field is optional; whether it is required, allowed, or forbidden depends
 * on the attribute configuration of the target database connection.
 *
 * @param email the user's email address.
 * @param phoneNumber the user's phone number.
 * @param username the user's username.
 * @param name the user's display name.
 * @param givenName the user's given (first) name.
 * @param familyName the user's family (last) name.
 * @param nickname the user's preferred nickname.
 * @param picture a URL pointing to the user's profile picture.
 */
@Serializable
public data class SignupProfile(
    @SerialName("email") val email: String? = null,
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("username") val username: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("given_name") val givenName: String? = null,
    @SerialName("family_name") val familyName: String? = null,
    @SerialName("nickname") val nickname: String? = null,
    @SerialName("picture") val picture: String? = null,
)
