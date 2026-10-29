package com.auth0.kmp.myaccount

import com.auth0.kmp.core.RequestOptions
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.myaccount.error.MyAccountError
import com.auth0.kmp.myaccount.model.EmailAuthenticationMethod
import com.auth0.kmp.myaccount.model.EmailEnrollmentChallenge
import com.auth0.kmp.myaccount.model.MyAccountProfile
import com.auth0.kmp.myaccount.model.PasskeyAuthenticationMethod
import com.auth0.kmp.myaccount.model.PasskeyEnrollmentChallenge
import com.auth0.kmp.myaccount.model.PasswordAuthenticationMethod
import com.auth0.kmp.myaccount.model.PasswordEnrollmentChallenge
import com.auth0.kmp.myaccount.model.PhoneAuthenticationMethod
import com.auth0.kmp.myaccount.model.PhoneAuthenticationMethodType
import com.auth0.kmp.myaccount.model.PhoneEnrollmentChallenge
import com.auth0.kmp.myaccount.model.PublicKeyCredentials
import com.auth0.kmp.myaccount.model.PushAuthenticationMethod
import com.auth0.kmp.myaccount.model.PushEnrollmentChallenge
import com.auth0.kmp.myaccount.model.RecoveryCodeAuthenticationMethod
import com.auth0.kmp.myaccount.model.RecoveryCodeEnrollmentChallenge
import com.auth0.kmp.myaccount.model.TotpAuthenticationMethod
import com.auth0.kmp.myaccount.model.TotpEnrollmentChallenge
import com.auth0.kmp.myaccount.model.UpdateUserProfileRequest


public interface MyAccountClient : AutoCloseable {

    /**
     * Requests a challenge for enrolling a new passkey. This is the first part of
     * the enrollment flow.
     *
     * You can specify an optional user identity identifier and an optional database
     * connection name. If a connection name is not specified, your tenant's default
     * directory will be used.
     *
     * ## Scopes Required
     *
     * `create:me:authentication_methods`
     *
     * ## Usage
     *
     * ```kotlin
     * when (val result = auth0.myAccount(accessToken).passkeyEnrollmentChallenge()) {
     *     is Result.Success -> {
     *         // Run the platform passkey-creation ceremony with
     *         // result.data.authParamsPublicKey, then call verifyPasskeyEnrollment().
     *     }
     *     is Result.Failure -> { /* result.error is a MyAccountError */ }
     * }
     * ```
     *
     * Run the platform WebAuthn ceremony with the returned
     * [PasskeyEnrollmentChallenge.authParamsPublicKey], then call
     * [verifyPasskeyEnrollment] with the created credential and this challenge to
     * complete the enrollment.
     *
     * @param userIdentityId the identity to enroll the passkey against, or `null`
     *   to use the identity the access token was issued for.
     * @param connection the name of the connection to enroll the passkey in, or `null`.
     * @param options per-call transport options (extra parameters, headers, retry policy).
     * @return [Result.Success] with the [PasskeyEnrollmentChallenge], or [Result.Failure]
     *   with the [MyAccountError] that occurred.
     */
    public suspend fun passkeyEnrollmentChallenge(
        userIdentityId: String? = null,
        connection: String? = null,
        options: RequestOptions = RequestOptions(),
    ): Result<PasskeyEnrollmentChallenge, MyAccountError>

    /**
     * Enrolls a new passkey credential. This is the last part of the enrollment
     * flow.
     *
     * ## Scopes Required
     *
     * `create:me:authentication_methods`
     *
     * ## Usage
     *
     * ```kotlin
     * // After obtaining the credential from the platform WebAuthn ceremony.
     * when (val result = auth0.myAccount(accessToken).verifyPasskeyEnrollment(credential, challenge)) {
     *     is Result.Success -> { /* result.data is the enrolled PasskeyAuthenticationMethod */ }
     *     is Result.Failure -> { /* result.error is a MyAccountError */ }
     * }
     * ```
     *
     * @param credential the public-key credential produced by the WebAuthn ceremony.
     * @param challenge the challenge returned by [passkeyEnrollmentChallenge].
     * @param options per-call transport options (extra parameters, headers, retry policy).
     * @return [Result.Success] with the enrolled [PasskeyAuthenticationMethod], or
     *   [Result.Failure] with the [MyAccountError] that occurred.
     */
    public suspend fun verifyPasskeyEnrollment(
        credential: PublicKeyCredentials,
        challenge: PasskeyEnrollmentChallenge,
        options: RequestOptions = RequestOptions(),
    ): Result<PasskeyAuthenticationMethod, MyAccountError>

    /**
     * Requests a challenge for enrolling a TOTP authenticator. This is the first
     * part of the enrollment flow.
     *
     * ## Scopes Required
     *
     * `create:me:authentication_methods`
     *
     * ## Usage
     *
     * ```kotlin
     * when (val result = auth0.myAccount(accessToken).totpEnrollmentChallenge()) {
     *     is Result.Success -> {
     *         // Show result.data.barcodeUri as a QR code (or result.data.manualInputCode),
     *         // then call verifyTotpEnrollment() with the code from the authenticator app.
     *     }
     *     is Result.Failure -> { /* result.error is a MyAccountError */ }
     * }
     * ```
     *
     * @param options per-call transport options (extra parameters, headers, retry policy).
     * @return [Result.Success] with the [TotpEnrollmentChallenge], or [Result.Failure]
     *   with the [MyAccountError] that occurred.
     */
    public suspend fun totpEnrollmentChallenge(
        options: RequestOptions = RequestOptions(),
    ): Result<TotpEnrollmentChallenge, MyAccountError>

    /**
     * Verifies a TOTP enrollment with the one-time code from the authenticator app.
     * This is the last part of the enrollment flow.
     *
     * ## Scopes Required
     *
     * `create:me:authentication_methods`
     *
     * @param authenticationMethodId the [TotpEnrollmentChallenge.authenticationMethodId]
     *   from the challenge step.
     * @param authSession the [TotpEnrollmentChallenge.authSession] from the challenge step.
     * @param otpCode the one-time code from the authenticator app.
     * @param options per-call transport options (extra parameters, headers, retry policy).
     * @return [Result.Success] with the enrolled [TotpAuthenticationMethod], or
     *   [Result.Failure] with the [MyAccountError] that occurred.
     */
    public suspend fun verifyTotpEnrollment(
        authenticationMethodId: String,
        authSession: String,
        otpCode: String,
        options: RequestOptions = RequestOptions(),
    ): Result<TotpAuthenticationMethod, MyAccountError>

    /**
     * Requests a challenge for enrolling push notifications. This is the first
     * part of the enrollment flow.
     *
     * ## Scopes Required
     *
     * `create:me:authentication_methods`
     *
     * ## Usage
     *
     * ```kotlin
     * when (val result = auth0.myAccount(accessToken).pushNotificationEnrollmentChallenge()) {
     *     is Result.Success -> {
     *         // Show result.data.barcodeUri as a QR code for the Guardian app to scan,
     *         // then, once the user has approved on their device, call
     *         // verifyPushNotificationEnrollment().
     *     }
     *     is Result.Failure -> { /* result.error is a MyAccountError */ }
     * }
     * ```
     *
     * @param options per-call transport options (extra parameters, headers, retry policy).
     * @return [Result.Success] with the [PushEnrollmentChallenge], or [Result.Failure]
     *   with the [MyAccountError] that occurred.
     */
    public suspend fun pushNotificationEnrollmentChallenge(
        options: RequestOptions = RequestOptions(),
    ): Result<PushEnrollmentChallenge, MyAccountError>

    /**
     * Verifies a push-notification enrollment once the user has approved it on
     * their device. This is the last part of the enrollment flow.
     *
     * ## Scopes Required
     *
     * `create:me:authentication_methods`
     *
     * @param authenticationMethodId the [PushEnrollmentChallenge.authenticationMethodId]
     *   from the challenge step.
     * @param authSession the [PushEnrollmentChallenge.authSession] from the challenge step.
     * @param options per-call transport options (extra parameters, headers, retry policy).
     * @return [Result.Success] with the enrolled [PushAuthenticationMethod], or
     *   [Result.Failure] with the [MyAccountError] that occurred.
     */
    public suspend fun verifyPushNotificationEnrollment(
        authenticationMethodId: String,
        authSession: String,
        options: RequestOptions = RequestOptions(),
    ): Result<PushAuthenticationMethod, MyAccountError>

    /**
     * Requests a challenge for enrolling an email address. This is the first part
     * of the enrollment flow; a one-time code is sent to the email address.
     *
     * ## Scopes Required
     *
     * `create:me:authentication_methods`
     *
     * ## Usage
     *
     * ```kotlin
     * when (val result = auth0.myAccount(accessToken).emailEnrollmentChallenge("jane@example.com")) {
     *     is Result.Success -> {
     *         // Prompt the user for the code emailed to them, then call
     *         // verifyEmailEnrollment().
     *     }
     *     is Result.Failure -> { /* result.error is a MyAccountError */ }
     * }
     * ```
     *
     * @param email the email address to enroll.
     * @param options per-call transport options (extra parameters, headers, retry policy).
     * @return [Result.Success] with the [EmailEnrollmentChallenge], or [Result.Failure]
     *   with the [MyAccountError] that occurred.
     */
    public suspend fun emailEnrollmentChallenge(
        email: String,
        options: RequestOptions = RequestOptions(),
    ): Result<EmailEnrollmentChallenge, MyAccountError>

    /**
     * Verifies an email enrollment with the one-time code sent to the email
     * address. This is the last part of the enrollment flow.
     *
     * ## Scopes Required
     *
     * `create:me:authentication_methods`
     *
     * @param authenticationMethodId the [EmailEnrollmentChallenge.authenticationMethodId]
     *   from the challenge step.
     * @param authSession the [EmailEnrollmentChallenge.authSession] from the challenge step.
     * @param otpCode the one-time code sent to the email address.
     * @param options per-call transport options (extra parameters, headers, retry policy).
     * @return [Result.Success] with the enrolled [EmailAuthenticationMethod], or
     *   [Result.Failure] with the [MyAccountError] that occurred.
     */
    public suspend fun verifyEmailEnrollment(
        authenticationMethodId: String,
        authSession: String,
        otpCode: String,
        options: RequestOptions = RequestOptions(),
    ): Result<EmailAuthenticationMethod, MyAccountError>

    /**
     * Requests a challenge for enrolling a phone number. This is the first part of
     * the enrollment flow; a one-time code is sent to the phone number.
     *
     * ## Scopes Required
     *
     * `create:me:authentication_methods`
     *
     * ## Usage
     *
     * ```kotlin
     * when (val result = auth0.myAccount(accessToken).phoneEnrollmentChallenge("+15551234567")) {
     *     is Result.Success -> {
     *         // Prompt the user for the code sent to them, then call
     *         // verifyPhoneEnrollment().
     *     }
     *     is Result.Failure -> { /* result.error is a MyAccountError */ }
     * }
     * ```
     *
     * @param phoneNumber the phone number to enroll, in E.164 format.
     * @param preferredAuthenticationMethod the channel used to deliver the one-time
     *   password, or `null` to use the tenant default.
     * @param options per-call transport options (extra parameters, headers, retry policy).
     * @return [Result.Success] with the [PhoneEnrollmentChallenge], or [Result.Failure]
     *   with the [MyAccountError] that occurred.
     */
    public suspend fun phoneEnrollmentChallenge(
        phoneNumber: String,
        preferredAuthenticationMethod: PhoneAuthenticationMethodType? = null,
        options: RequestOptions = RequestOptions(),
    ): Result<PhoneEnrollmentChallenge, MyAccountError>

    /**
     * Verifies a phone enrollment with the one-time code sent to the phone number.
     * This is the last part of the enrollment flow.
     *
     * ## Scopes Required
     *
     * `create:me:authentication_methods`
     *
     * @param authenticationMethodId the [PhoneEnrollmentChallenge.authenticationMethodId]
     *   from the challenge step.
     * @param authSession the [PhoneEnrollmentChallenge.authSession] from the challenge step.
     * @param otpCode the one-time code sent to the phone number.
     * @param options per-call transport options (extra parameters, headers, retry policy).
     * @return [Result.Success] with the enrolled [PhoneAuthenticationMethod], or
     *   [Result.Failure] with the [MyAccountError] that occurred.
     */
    public suspend fun verifyPhoneEnrollment(
        authenticationMethodId: String,
        authSession: String,
        otpCode: String,
        options: RequestOptions = RequestOptions(),
    ): Result<PhoneAuthenticationMethod, MyAccountError>

    /**
     * Requests a challenge for enrolling a recovery code. This is the first part
     * of the enrollment flow; the recovery code to store is returned in the challenge.
     *
     * ## Scopes Required
     *
     * `create:me:authentication_methods`
     *
     * ## Usage
     *
     * ```kotlin
     * when (val result = auth0.myAccount(accessToken).recoveryCodeEnrollmentChallenge()) {
     *     is Result.Success -> {
     *         // Present result.data.recoveryCode for the user to store securely,
     *         // then call verifyRecoveryCodeEnrollment().
     *     }
     *     is Result.Failure -> { /* result.error is a MyAccountError */ }
     * }
     * ```
     *
     * @param options per-call transport options (extra parameters, headers, retry policy).
     * @return [Result.Success] with the [RecoveryCodeEnrollmentChallenge], or
     *   [Result.Failure] with the [MyAccountError] that occurred.
     */
    public suspend fun recoveryCodeEnrollmentChallenge(
        options: RequestOptions = RequestOptions(),
    ): Result<RecoveryCodeEnrollmentChallenge, MyAccountError>

    /**
     * Verifies a recovery-code enrollment. This is the last part of the enrollment
     * flow.
     *
     * ## Scopes Required
     *
     * `create:me:authentication_methods`
     *
     * @param authenticationMethodId the [RecoveryCodeEnrollmentChallenge.authenticationMethodId]
     *   from the challenge step.
     * @param authSession the [RecoveryCodeEnrollmentChallenge.authSession] from the challenge step.
     * @param options per-call transport options (extra parameters, headers, retry policy).
     * @return [Result.Success] with the enrolled [RecoveryCodeAuthenticationMethod], or
     *   [Result.Failure] with the [MyAccountError] that occurred.
     */
    public suspend fun verifyRecoveryCodeEnrollment(
        authenticationMethodId: String,
        authSession: String,
        options: RequestOptions = RequestOptions(),
    ): Result<RecoveryCodeAuthenticationMethod, MyAccountError>

    /**
     * Requests a challenge for enrolling a password. This is the first part of the
     * enrollment flow; the password policy the new password must satisfy is
     * returned in the challenge.
     *
     * ## Scopes Required
     *
     * `create:me:authentication_methods`
     *
     * ## Usage
     *
     * ```kotlin
     * when (val result = auth0.myAccount(accessToken).passwordEnrollmentChallenge()) {
     *     is Result.Success -> {
     *         // Collect a new password satisfying result.data.passwordPolicy,
     *         // then call verifyPasswordEnrollment().
     *     }
     *     is Result.Failure -> { /* result.error is a MyAccountError */ }
     * }
     * ```
     *
     * @param userIdentityId the identity to enroll the password against, or `null`
     *   to use the identity the access token was issued for.
     * @param connection the name of the connection to enroll the password in, or `null`.
     * @param options per-call transport options (extra parameters, headers, retry policy).
     * @return [Result.Success] with the [PasswordEnrollmentChallenge], or [Result.Failure]
     *   with the [MyAccountError] that occurred.
     */
    public suspend fun passwordEnrollmentChallenge(
        userIdentityId: String? = null,
        connection: String? = null,
        options: RequestOptions = RequestOptions(),
    ): Result<PasswordEnrollmentChallenge, MyAccountError>

    /**
     * Verifies a password enrollment by setting the new password. This is the last
     * part of the enrollment flow.
     *
     * ## Scopes Required
     *
     * `create:me:authentication_methods`
     *
     * @param authenticationMethodId the [PasswordEnrollmentChallenge.authenticationMethodId]
     *   from the challenge step.
     * @param authSession the [PasswordEnrollmentChallenge.authSession] from the challenge step.
     * @param newPassword the new password to set, satisfying the challenge's password policy.
     * @param options per-call transport options (extra parameters, headers, retry policy).
     * @return [Result.Success] with the enrolled [PasswordAuthenticationMethod], or
     *   [Result.Failure] with the [MyAccountError] that occurred.
     */
    public suspend fun verifyPasswordEnrollment(
        authenticationMethodId: String,
        authSession: String,
        newPassword: String,
        options: RequestOptions = RequestOptions(),
    ): Result<PasswordAuthenticationMethod, MyAccountError>

    /**
     * Retrieves the current user's profile.
     *
     * Use [fields] to choose which properties the server returns. When [fields] is
     * set and does not contain `profile_policy`, the response carries no policy and
     * [MyAccountProfile.profilePolicy] is `null`.
     *
     * [MyAccountProfile.profilePolicy] is keyed by a JSON Pointer to the field —
     * for example `/given_name` or `/email` — not by the plain field name.
     *
     * The access token must be issued for the My Account audience
     * (`https://YOUR_DOMAIN/me/`). The profile endpoints are rate limited per user,
     * with `GET` and `PATCH` counted separately.
     *
     * ## Scopes Required
     *
     * `read:me:profile`
     *
     * ## Usage
     *
     * ```kotlin
     * val myAccount = auth0.myAccount(accessToken)
     *
     * // The whole profile, including the per-field policy.
     * when (val result = myAccount.getUserProfile()) {
     *     is Result.Success -> { /* result.data is a MyAccountProfile */ }
     *     is Result.Failure -> { /* result.error is a MyAccountError */ }
     * }
     *
     * // Only some fields, plus the policy.
     * myAccount.getUserProfile(fields = listOf("given_name", "picture", "profile_policy"))
     *
     * // Everything except some fields.
     * myAccount.getUserProfile(fields = listOf("picture"), includeFields = false)
     * ```
     *
     * @param fields the fields to include in (or, with `includeFields = false`,
     *   exclude from) the response, for example `listOf("given_name", "profile_policy")`.
     *   Sent as one comma-separated value; nothing is sent when `null` or empty.
     * @param includeFields whether [fields] are included (`true`) or excluded
     *   (`false`). Only sent when [fields] is not empty; the server defaults to
     *   inclusion.
     * @param options per-call transport options (extra parameters, headers, retry policy).
     * @return [Result.Success] with the [MyAccountProfile], or [Result.Failure] with
     *   the [MyAccountError] that occurred. A feature that is not enabled for the
     *   tenant, or a scope that was not granted, surfaces as a `403`
     *   [MyAccountError.ApiError].
     */
    public suspend fun getUserProfile(
        fields: List<String>? = null,
        includeFields: Boolean? = null,
        options: RequestOptions = RequestOptions(),
    ): Result<MyAccountProfile, MyAccountError>

    /**
     * Updates the current user's profile.
     *
     * Only the non-null properties of [request] are sent. A request with nothing
     * set is valid and returns the current profile. The result is the full updated
     * profile, in the same shape as [getUserProfile].
     *
     * Changing a read-only field fails with a `400` [MyAccountError.ApiError] whose
     * [MyAccountError.ApiError.validationErrors] point at the offending field, for
     * example `/given_name`. An invalid value (such as a `picture` that is not a
     * URL) fails the same way.
     *
     * The access token must be issued for the My Account audience
     * (`https://YOUR_DOMAIN/me/`). The profile endpoints are rate limited per user,
     * with `GET` and `PATCH` counted separately.
     *
     * ## Scopes Required
     *
     * `update:me:profile`
     *
     * ## Usage
     *
     * ```kotlin
     * val request = UpdateUserProfileRequest(givenName = "Jane", nickname = "jane")
     * when (val result = auth0.myAccount(accessToken).updateUserProfile(request)) {
     *     is Result.Success -> { /* result.data is the updated MyAccountProfile */ }
     *     is Result.Failure -> { /* result.error is a MyAccountError */ }
     * }
     * ```
     *
     * @param request the properties to change; `null` properties are left unchanged.
     * @param options per-call transport options (extra parameters, headers, retry policy).
     * @return [Result.Success] with the updated [MyAccountProfile], or [Result.Failure]
     *   with the [MyAccountError] that occurred.
     */
    public suspend fun updateUserProfile(
        request: UpdateUserProfileRequest,
        options: RequestOptions = RequestOptions(),
    ): Result<MyAccountProfile, MyAccountError>

    /**
     * Releases the network transport backing this client.
     *
     * Call this only when the client was obtained from a standalone factory
     * (`myAccountClient(account, accessToken)`). When obtained from `Auth0`, close
     * it via `Auth0.close()` instead.
     */
    override fun close() {}
}
