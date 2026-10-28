package com.auth0.kmp.myaccount.model

import kotlin.time.Instant

/**
 * The current user's profile, as returned by the My Account API profile endpoint.
 *
 * Every property is nullable: the server omits fields that are not set for the
 * user, and any field can be trimmed from the response with the `fields`
 * parameter of [com.auth0.kmp.myaccount.MyAccountClient.getUserProfile].
 *
 * [userId], [emailVerified] and [phoneVerified] are read-only and cannot be
 * changed with [UpdateUserProfileRequest]. The API does not return them yet, so
 * they are currently always `null`.
 *
 * @param userId the unique identifier of the user.
 * @param givenName the user's given (first) name.
 * @param familyName the user's family (last) name.
 * @param name the user's full name.
 * @param nickname the user's nickname.
 * @param picture the URL of the user's picture.
 * @param email the user's email address.
 * @param emailVerified whether the user's email address has been verified.
 * @param phoneNumber the user's phone number.
 * @param phoneVerified whether the user's phone number has been verified.
 * @param username the user's username.
 * @param createdAt when the user was created.
 * @param updatedAt when the user was last updated.
 * @param profilePolicy describes, per field, whether it can be edited by the user
 *   and why. Keyed by a JSON Pointer to the field, for example `/given_name` or
 *   `/email` — not by the plain field name. `null` when `profile_policy` is not
 *   among the requested fields.
 */
public data class MyAccountProfile(
    val userId: String? = null,
    val givenName: String? = null,
    val familyName: String? = null,
    val name: String? = null,
    val nickname: String? = null,
    val picture: String? = null,
    val email: String? = null,
    val emailVerified: Boolean? = null,
    val phoneNumber: String? = null,
    val phoneVerified: Boolean? = null,
    val username: String? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
    val profilePolicy: Map<String, ProfileFieldPolicy>? = null,
)

/**
 * Describes whether a profile field can be edited by the user and why.
 *
 * @param access whether the field is editable, or `null` if the server returned a
 *   value this SDK version does not know.
 * @param source where the field's value comes from, or `null` if the server
 *   returned a value this SDK version does not know.
 * @param reason the machine-readable reason a field is read-only, for example
 *   `managed_by_identity_provider`. Only set when [access] is
 *   [ProfileFieldAccess.READ_ONLY]. The server may add new values, so this is a
 *   free-form string rather than an enum.
 */
public data class ProfileFieldPolicy(
    val access: ProfileFieldAccess? = null,
    val source: ProfileFieldSource? = null,
    val reason: String? = null,
)

/**
 * Whether a profile field can be edited by the user.
 */
public enum class ProfileFieldAccess {
    /** The field cannot be changed by the user. */
    READ_ONLY,

    /** The field can be changed by the user. */
    READ_WRITE,
}

/**
 * Where a profile field's value comes from.
 */
public enum class ProfileFieldSource {
    /** The value was provided by the user. */
    USER,

    /** The value comes from an external identity provider. */
    IDP,

    /** The value is managed by Auth0. */
    SYSTEM,
}
