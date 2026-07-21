package com.auth0.kmp.core.model

import kotlinx.serialization.json.JsonElement
import kotlin.time.Instant

/**
 * The authenticated user's profile, built from the OIDC standard claims returned
 * by the `/userinfo` endpoint.
 *
 * Standard OIDC claims are exposed as typed properties. Any non-standard or
 * namespaced claim is collected into [customClaims], keyed by its claim name.
 *
 * @param sub the subject identifier — the unique, stable id of the user.
 * @param name the user's full name.
 * @param givenName the user's given (first) name.
 * @param familyName the user's family (last) name.
 * @param middleName the user's middle name.
 * @param nickname the user's casual name.
 * @param preferredUsername the name the user wishes to be referred to by.
 * @param profile a URL of the user's profile page.
 * @param picture a URL of the user's profile picture.
 * @param website a URL of the user's website.
 * @param email the user's preferred email address.
 * @param emailVerified whether the user's email address has been verified.
 * @param gender the user's gender.
 * @param birthdate the user's birthday.
 * @param zoneinfo the user's time zone.
 * @param locale the user's locale.
 * @param phoneNumber the user's preferred telephone number.
 * @param phoneNumberVerified whether the user's phone number has been verified.
 * @param address the user's postal address.
 * @param updatedAt the time the user's information was last updated.
 * @param customClaims non-standard or namespaced claims not covered by a typed
 *   property, keyed by claim name.
 */
public data class UserInfo(
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
    val customClaims: Map<String, JsonElement> = emptyMap(),
)
