package com.auth0.kmp.authentication.passwordless

import com.auth0.kmp.authentication.error.AuthenticationError
import com.auth0.kmp.authentication.model.DeliveryMethod
import com.auth0.kmp.authentication.model.PasswordlessChallenge
import com.auth0.kmp.authentication.model.PasswordlessType
import com.auth0.kmp.core.RequestOptions
import com.auth0.kmp.core.model.Credentials
import com.auth0.kmp.core.result.Result

/**
 * Performs passwordless authentication operations against an Auth0 tenant.
 *
 * Obtain an instance via [com.auth0.kmp.authentication.AuthenticationClient.passwordlessClient].
 *
 * Two distinct flows are supported:
 *
 * **Classic passwordless flow** — uses an email or SMS connection. Start with
 * [passwordlessWithEmail] / [passwordlessWithSMS], then complete with
 * [loginWithEmail] / [loginWithPhoneNumber].
 *
 * **DB-connection OTP flow (Early Access)** — uses a database connection with email/phone OTP
 * enabled. Start with [challengeWithEmail] / [challengeWithPhoneNumber], then complete with
 * [loginWithOTP].
 */
public interface PasswordlessClient {

    // ─── Classic passwordless flow ──────────────────────────────────────────

    /**
     * Starts a classic passwordless flow that delivers a one-time code or link to an
     * email address via `POST /passwordless/start`.
     *
     * Complete the flow with [loginWithEmail] once the user provides the code.
     *
     * @param email the email address to send the code or link to.
     * @param type how the code or link is delivered; defaults to [PasswordlessType.CODE].
     * @param connection the name of the passwordless email connection; defaults to `"email"`.
     * @param options per-call transport options (extra parameters, headers, retry policy).
     * @return [Result.Success] on success, or [Result.Failure] with the
     *   [AuthenticationError] that occurred.
     */
    public suspend fun passwordlessWithEmail(
        email: String,
        type: PasswordlessType = PasswordlessType.CODE,
        connection: String = "email",
        options: RequestOptions = RequestOptions(),
    ): Result<Unit, AuthenticationError>

    /**
     * Starts a classic passwordless flow that delivers a one-time code or link to a
     * phone number over SMS via `POST /passwordless/start`.
     *
     * Complete the flow with [loginWithPhoneNumber] once the user provides the code.
     *
     * @param phoneNumber the phone number in E.164 format to send the code or link to.
     * @param type how the code or link is delivered; defaults to [PasswordlessType.CODE].
     * @param connection the name of the passwordless SMS connection; defaults to `"sms"`.
     * @param options per-call transport options (extra parameters, headers, retry policy).
     * @return [Result.Success] on success, or [Result.Failure] with the
     *   [AuthenticationError] that occurred.
     */
    public suspend fun passwordlessWithSMS(
        phoneNumber: String,
        type: PasswordlessType = PasswordlessType.CODE,
        connection: String = "sms",
        options: RequestOptions = RequestOptions(),
    ): Result<Unit, AuthenticationError>

    /**
     * Completes a classic email passwordless flow by exchanging the one-time code for
     * credentials.
     *
     * @param email the email address the code was sent to.
     * @param code the one-time code the user received.
     * @param realm the passwordless email connection to authenticate against; defaults to `"email"`.
     * @param audience the API identifier to request an access token for, or `null` to omit it.
     * @param scope the space-separated scopes to request.
     * @param options per-call transport options (extra parameters, headers, retry policy).
     * @return [Result.Success] with the issued [Credentials], or [Result.Failure] with the
     *   [AuthenticationError] that occurred.
     */
    public suspend fun loginWithEmail(
        email: String,
        code: String,
        realm: String = "email",
        audience: String? = null,
        scope: String = "openid profile email",
        options: RequestOptions = RequestOptions(),
    ): Result<Credentials, AuthenticationError>

    /**
     * Completes a classic SMS passwordless flow by exchanging the one-time code for
     * credentials.
     *
     * @param phoneNumber the phone number, in E.164 format, the code was sent to.
     * @param code the one-time code the user received.
     * @param realm the passwordless SMS connection to authenticate against; defaults to `"sms"`.
     * @param audience the API identifier to request an access token for, or `null` to omit it.
     * @param scope the space-separated scopes to request.
     * @param options per-call transport options (extra parameters, headers, retry policy).
     * @return [Result.Success] with the issued [Credentials], or [Result.Failure] with the
     *   [AuthenticationError] that occurred.
     */
    public suspend fun loginWithPhoneNumber(
        phoneNumber: String,
        code: String,
        realm: String = "sms",
        audience: String? = null,
        scope: String = "openid profile email",
        options: RequestOptions = RequestOptions(),
    ): Result<Credentials, AuthenticationError>

    // ─── DB-connection OTP flow (Early Access) ──────────────────────────────

    /**
     * Issues an OTP challenge to an email address for a database connection
     * (`POST /otp/challenge`).
     *
     * This is the first step of the DB-connection passwordless flow (Early Access). On success
     * a [PasswordlessChallenge] is returned — pass it to [loginWithOTP] together with the
     * one-time code the user receives.
     *
     * Requires the tenant to have the DB-connection OTP feature enabled (currently Early Access;
     * contact Auth0 support to enable it).
     *
     * @param email the email address to send the one-time code to.
     * @param connection the database connection with `email_otp` enabled.
     * @param allowSignup whether to allow sign-up if the user does not exist; defaults to `false`.
     * @param options per-call transport options (extra parameters, headers, retry policy).
     * @return [Result.Success] with the [PasswordlessChallenge], or [Result.Failure] with the
     *   [AuthenticationError] that occurred.
     */
    public suspend fun challengeWithEmail(
        email: String,
        connection: String = "Username-Password-Authentication",
        allowSignup: Boolean = false,
        options: RequestOptions = RequestOptions(),
    ): Result<PasswordlessChallenge, AuthenticationError>

    /**
     * Issues an OTP challenge to a phone number for a database connection
     * (`POST /otp/challenge`).
     *
     * This is the first step of the DB-connection passwordless flow (Early Access). On success
     * a [PasswordlessChallenge] is returned — pass it to [loginWithOTP] together with the
     * one-time code the user receives.
     *
     * Requires the tenant to have the DB-connection OTP feature enabled (currently Early Access;
     * contact Auth0 support to enable it).
     *
     * @param phoneNumber the phone number in E.164 format to send the one-time code to.
     * @param connection the database connection with `phone_otp` enabled.
     * @param deliveryMethod how the code is delivered; defaults to [DeliveryMethod.TEXT].
     * @param allowSignup whether to allow sign-up if the user does not exist; defaults to `false`.
     * @param options per-call transport options (extra parameters, headers, retry policy).
     * @return [Result.Success] with the [PasswordlessChallenge], or [Result.Failure] with the
     *   [AuthenticationError] that occurred.
     */
    public suspend fun challengeWithPhoneNumber(
        phoneNumber: String,
        connection: String = "Username-Password-Authentication",
        deliveryMethod: DeliveryMethod = DeliveryMethod.TEXT,
        allowSignup: Boolean = false,
        options: RequestOptions = RequestOptions(),
    ): Result<PasswordlessChallenge, AuthenticationError>

    /**
     * Completes the DB-connection OTP flow by verifying the one-time code and obtaining
     * credentials.
     *
     * Exchanges the [challenge] returned by [challengeWithEmail] or [challengeWithPhoneNumber]
     * together with the [otp] the user received for [Credentials] via `POST /oauth/token`.
     *
     * @param challenge the challenge from a prior [challengeWithEmail] or
     *   [challengeWithPhoneNumber] call.
     * @param otp the one-time code the user received via email, SMS, or voice call.
     * @param audience the API identifier to request an access token for, or `null` to omit it.
     * @param scope the space-separated scopes to request.
     * @param options per-call transport options (extra parameters, headers, retry policy).
     * @return [Result.Success] with the issued [Credentials], or [Result.Failure] with the
     *   [AuthenticationError] that occurred.
     */
    public suspend fun loginWithOTP(
        challenge: PasswordlessChallenge,
        otp: String,
        audience: String? = null,
        scope: String = "openid profile email",
        options: RequestOptions = RequestOptions(),
    ): Result<Credentials, AuthenticationError>
}
