package com.auth0.kmp.myaccount.model

import kotlin.time.Instant

/**
 * A TOTP authentication method.
 *
 * @param id unique identifier of the authentication method.
 * @param type type of the authentication method. Equals to `totp`.
 * @param usage the ways this authentication method may be used.
 * @param createdAt creation date of the authentication method.
 * @param confirmed whether the enrollment has been confirmed.
 * @param lastAuthAt the last time this authentication method was used, or `null` if never.
 * @param name a user-facing name for the authentication method, or `null` if unset.
 */
public data class TotpAuthenticationMethod(
    val id: String,
    val type: String,
    val usage: List<String>,
    val createdAt: Instant,
    val confirmed: Boolean?,
    val lastAuthAt: Instant?,
    val name: String?,
)

/**
 * A push-notification authentication method.
 *
 * @param id unique identifier of the authentication method.
 * @param type type of the authentication method. Equals to `push-notification`.
 * @param usage the ways this authentication method may be used.
 * @param createdAt creation date of the authentication method.
 * @param confirmed whether the enrollment has been confirmed.
 * @param lastAuthAt the last time this authentication method was used, or `null` if never.
 * @param name a user-facing name for the authentication method, or `null` if unset.
 */
public data class PushAuthenticationMethod(
    val id: String,
    val type: String,
    val usage: List<String>,
    val createdAt: Instant,
    val confirmed: Boolean?,
    val lastAuthAt: Instant?,
    val name: String?,
)

/**
 * A recovery-code authentication method.
 *
 * @param id unique identifier of the authentication method.
 * @param type type of the authentication method. Equals to `recovery-code`.
 * @param usage the ways this authentication method may be used.
 * @param createdAt creation date of the authentication method.
 * @param confirmed whether the enrollment has been confirmed.
 * @param lastAuthAt the last time this authentication method was used, or `null` if never.
 * @param name a user-facing name for the authentication method, or `null` if unset.
 */
public data class RecoveryCodeAuthenticationMethod(
    val id: String,
    val type: String,
    val usage: List<String>,
    val createdAt: Instant,
    val confirmed: Boolean?,
    val lastAuthAt: Instant?,
    val name: String?,
)

/**
 * An email authentication method.
 *
 * @param id unique identifier of the authentication method.
 * @param type type of the authentication method. Equals to `email`.
 * @param usage the ways this authentication method may be used.
 * @param createdAt creation date of the authentication method.
 * @param confirmed whether the enrollment has been confirmed.
 * @param lastAuthAt the last time this authentication method was used, or `null` if never.
 * @param name a user-facing name for the authentication method, or `null` if unset.
 * @param email the enrolled email address.
 */
public data class EmailAuthenticationMethod(
    val id: String,
    val type: String,
    val usage: List<String>,
    val createdAt: Instant,
    val confirmed: Boolean?,
    val lastAuthAt: Instant?,
    val name: String?,
    val email: String,
)

/**
 * A phone authentication method.
 *
 * @param id unique identifier of the authentication method.
 * @param type type of the authentication method. Equals to `phone`.
 * @param usage the ways this authentication method may be used.
 * @param createdAt creation date of the authentication method.
 * @param confirmed whether the enrollment has been confirmed.
 * @param lastAuthAt the last time this authentication method was used, or `null` if never.
 * @param name a user-facing name for the authentication method, or `null` if unset.
 * @param phoneNumber the enrolled phone number.
 * @param preferredAuthenticationMethod the preferred channel for delivering the
 *   one-time password, or `null` if unset.
 */
public data class PhoneAuthenticationMethod(
    val id: String,
    val type: String,
    val usage: List<String>,
    val createdAt: Instant,
    val confirmed: Boolean?,
    val lastAuthAt: Instant?,
    val name: String?,
    val phoneNumber: String,
    val preferredAuthenticationMethod: PhoneAuthenticationMethodType?,
)

/**
 * A password authentication method.
 *
 * @param id unique identifier of the authentication method.
 * @param type type of the authentication method. Equals to `password`.
 * @param usage the ways this authentication method may be used.
 * @param createdAt creation date of the authentication method.
 * @param identityUserId unique identifier of the user identity linked with the
 *   authentication method, or `null`.
 * @param lastPasswordReset the last time the password was reset, or `null` if never.
 */
public data class PasswordAuthenticationMethod(
    val id: String,
    val type: String,
    val usage: List<String>,
    val createdAt: Instant,
    val identityUserId: String?,
    val lastPasswordReset: Instant?,
)
