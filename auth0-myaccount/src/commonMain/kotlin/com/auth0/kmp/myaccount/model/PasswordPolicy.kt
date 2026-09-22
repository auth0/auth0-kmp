package com.auth0.kmp.myaccount.model

/**
 * The password policy a tenant enforces for a new password.
 *
 * @param complexity the character and length requirements.
 * @param profileData whether the password may contain the user's profile data.
 * @param history whether previously used passwords are disallowed.
 * @param dictionary whether common dictionary passwords are disallowed.
 */
public data class PasswordPolicy(
    val complexity: PasswordComplexity,
    val profileData: PasswordProfileData,
    val history: PasswordHistory,
    val dictionary: PasswordDictionary,
)

/**
 * The character and length requirements of a [PasswordPolicy].
 *
 * @param minLength the minimum number of characters.
 * @param characterTypes the character classes the password may draw from
 *   (for example `lowercase`, `uppercase`, `number`, `special`).
 * @param characterTypeRule how many character classes are required
 *   (for example `three_of_four`).
 * @param identicalCharacters how repeated identical characters are handled.
 * @param sequentialCharacters how sequential characters are handled.
 * @param maxLengthExceeded how a password longer than the maximum is handled.
 */
public data class PasswordComplexity(
    val minLength: Int,
    val characterTypes: List<String>,
    val characterTypeRule: String,
    val identicalCharacters: String,
    val sequentialCharacters: String,
    val maxLengthExceeded: String,
)

/**
 * Whether a [PasswordPolicy] disallows passwords containing the user's profile data.
 *
 * @param active whether the rule is enforced.
 * @param blockedFields the profile fields whose values may not appear in the password.
 */
public data class PasswordProfileData(
    val active: Boolean,
    val blockedFields: List<String>,
)

/**
 * Whether a [PasswordPolicy] disallows previously used passwords.
 *
 * @param active whether the rule is enforced.
 * @param size how many previous passwords are remembered and disallowed.
 */
public data class PasswordHistory(
    val active: Boolean,
    val size: Int,
)

/**
 * Whether a [PasswordPolicy] disallows common dictionary passwords.
 *
 * @param active whether the rule is enforced.
 * @param default the identifier of the dictionary in use (for example `en_10k`).
 */
public data class PasswordDictionary(
    val active: Boolean,
    val default: String,
)
