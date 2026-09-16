package com.auth0.kmp.myaccount

import com.auth0.kmp.core.RequestOptions
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.myaccount.error.MyAccountError
import com.auth0.kmp.myaccount.model.PasskeyAuthenticationMethod
import com.auth0.kmp.myaccount.model.PasskeyEnrollmentChallenge
import com.auth0.kmp.myaccount.model.PublicKeyCredentials


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
     * Releases the network transport backing this client.
     *
     * Call this only when the client was obtained from a standalone factory
     * (`myAccountClient(account, accessToken)`). When obtained from `Auth0`, close
     * it via `Auth0.close()` instead.
     */
    override fun close() {}
}
