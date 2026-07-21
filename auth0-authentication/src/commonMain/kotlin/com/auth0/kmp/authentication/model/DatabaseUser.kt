package com.auth0.kmp.authentication.model

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
public data class DatabaseUser(
    val id: String,
    val email: String? = null,
    val emailVerified: Boolean? = null,
    val username: String? = null,
    val phoneNumber: String? = null,
    val phoneVerified: Boolean? = null,
    val givenName: String? = null,
    val familyName: String? = null,
    val name: String? = null,
    val nickname: String? = null,
    val picture: String? = null,
)
