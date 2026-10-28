package com.auth0.kmp.myaccount.model

/**
 * The profile properties to change with
 * [com.auth0.kmp.myaccount.MyAccountClient.updateUserProfile].
 *
 * Only properties that are non-null are sent; `null` leaves the stored value
 * unchanged. A request with nothing set is valid and returns the current profile.
 *
 * @param givenName the new given (first) name.
 * @param familyName the new family (last) name.
 * @param name the new full name.
 * @param nickname the new nickname.
 * @param picture the new picture URL.
 */
public data class UpdateUserProfileRequest(
    val givenName: String? = null,
    val familyName: String? = null,
    val name: String? = null,
    val nickname: String? = null,
    val picture: String? = null,
)
