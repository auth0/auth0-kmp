package com.auth0.kmp.core.model

import kotlin.time.Instant

/**
 * The authenticated user's standard claims, as returned by the OpenID Connect
 * `/userinfo` endpoint.
 *
 * Fields are the Standard Claims defined by OpenID Connect Core 1.0, section
 * 5.1. Only [sub] is guaranteed to be present; every other claim is optional and
 * depends on the requested scopes and what the provider returns.
 *
 * @param sub subject — the stable, unique identifier for the user.
 * @param name full name in displayable form.
 * @param givenName given (first) name(s).
 * @param familyName surname / last name(s).
 * @param middleName middle name(s).
 * @param nickname casual name, may or may not match [givenName].
 * @param preferredUsername shorthand name the user wishes to be referred to by.
 * @param profile URL of the user's profile page.
 * @param picture URL of the user's profile picture.
 * @param website URL of the user's web page or blog.
 * @param email preferred email address.
 * @param emailVerified whether the email address has been verified.
 * @param gender the user's gender.
 * @param birthdate birthdate, typically `YYYY-MM-DD` (year `0000` may be omitted).
 * @param zoneinfo time zone, e.g. `Europe/Paris` or `America/Los_Angeles`.
 * @param locale locale, typically a BCP47 tag such as `en-US`.
 * @param phoneNumber preferred telephone number.
 * @param phoneNumberVerified whether the phone number has been verified.
 * @param address structured postal address.
 * @param updatedAt time the user's information was last updated.
 */
data class UserInfo(
    val sub: String,
    val name: String? = null,
    val givenName: String? = null,
    val familyName: String? = null,
    val middleName: String? = null,
    val nickname: String? = null,
    val preferredUsername: String? = null,
    val profile: String? = null,
    val picture: String? = null,
    val website: String? = null,
    val email: String? = null,
    val emailVerified: Boolean? = null,
    val gender: String? = null,
    val birthdate: String? = null,
    val zoneinfo: String? = null,
    val locale: String? = null,
    val phoneNumber: String? = null,
    val phoneNumberVerified: Boolean? = null,
    val address: Address? = null,
    val updatedAt: Instant? = null,
)
