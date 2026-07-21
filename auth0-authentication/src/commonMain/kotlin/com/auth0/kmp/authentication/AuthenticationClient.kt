package com.auth0.kmp.authentication

import com.auth0.kmp.authentication.error.AuthenticationError
import com.auth0.kmp.authentication.model.DatabaseUser
import com.auth0.kmp.authentication.model.PasskeyLoginChallenge
import com.auth0.kmp.authentication.model.PasskeyRegistrationChallenge
import com.auth0.kmp.authentication.model.PublicKeyCredentials
import com.auth0.kmp.authentication.model.SignupProfile
import com.auth0.kmp.core.RequestOptions
import com.auth0.kmp.core.model.Credentials
import com.auth0.kmp.core.model.UserInfo
import com.auth0.kmp.core.result.Result

/**
 * Performs authentication operations against an Auth0 tenant.
 */
public interface AuthenticationClient : AutoCloseable {

    /**
     * Authenticates a user with their credentials against a database connection
     * using the password-realm grant.
     *
     * @param usernameOrEmail the user's username or email address.
     * @param password the user's password.
     * @param realm the name of the database connection to authenticate against.
     * @param audience the unique identifier of the API to request access to, or
     *   `null` to omit it.
     * @param scope the space-separated scopes to request.
     * @param options per-call transport options (extra parameters, headers, retry policy).
     * @return [Result.Success] with the issued [Credentials], or [Result.Failure]
     *   with the [AuthenticationError] that occurred.
     */
    public suspend fun login(
        usernameOrEmail: String,
        password: String,
        realm: String,
        audience: String? = null,
        scope: String = "openid profile email",
        options: RequestOptions = RequestOptions(),
    ): Result<Credentials, AuthenticationError>

    /**
     * Creates a user in a database connection.
     *
     * @param profile the structured profile of the user to create; its `email`
     *   must be present.
     * @param password the user's password.
     * @param connection the name of the database connection to create the user in.
     * @param userMetadata additional user metadata to store on the created user;
     *   empty to send none.
     * @param options per-call transport options (extra parameters, headers, retry policy).
     * @return [Result.Success] with the created [DatabaseUser], or [Result.Failure]
     *   with the [AuthenticationError] that occurred.
     */
    public suspend fun createUser(
        profile: SignupProfile,
        password: String,
        connection: String,
        userMetadata: Map<String, String> = emptyMap(),
        options: RequestOptions = RequestOptions(),
    ): Result<DatabaseUser, AuthenticationError>

    /**
     * Requests a password-reset email for a user in a database connection.
     *
     * @param email the email address of the user to reset the password for.
     * @param connection the name of the database connection the user belongs to.
     * @param options per-call transport options (extra parameters, headers, retry policy).
     * @return [Result.Success] on success, or [Result.Failure] with the
     *   [AuthenticationError] that occurred.
     */
    public suspend fun resetPassword(
        email: String,
        connection: String,
        options: RequestOptions = RequestOptions(),
    ): Result<Unit, AuthenticationError>

    /**
     * Retrieves the OIDC profile of the user associated with an access token.
     *
     * @param accessToken the access token issued for the user.
     * @param tokenType the token type to send in the `Authorization` header.
     * @param options per-call transport options (extra parameters, headers, retry policy).
     * @return [Result.Success] with the [UserInfo], or [Result.Failure] with the
     *   [AuthenticationError] that occurred.
     */
    public suspend fun userInfo(
        accessToken: String,
        tokenType: String = "Bearer",
        options: RequestOptions = RequestOptions(),
    ): Result<UserInfo, AuthenticationError>

    /**
     * Revokes a refresh token so it can no longer be used to obtain new tokens.
     *
     * @param refreshToken the refresh token to revoke.
     * @param options per-call transport options (extra parameters, headers, retry policy).
     * @return [Result.Success] on success, or [Result.Failure] with the
     *   [AuthenticationError] that occurred.
     */
    public suspend fun revoke(
        refreshToken: String,
        options: RequestOptions = RequestOptions(),
    ): Result<Unit, AuthenticationError>

    /**
     * Renews credentials using the refresh-token grant.
     *
     * @param refreshToken the refresh token to exchange for new credentials.
     * @param audience the unique identifier of the API to request access to, or
     *   `null` to omit it.
     * @param scope the space-separated scopes to request, or `null` to omit them.
     * @param options per-call transport options (extra parameters, headers, retry policy).
     * @return [Result.Success] with the renewed [Credentials], or [Result.Failure]
     *   with the [AuthenticationError] that occurred.
     */
    public suspend fun renew(
        refreshToken: String,
        audience: String? = null,
        scope: String? = null,
        options: RequestOptions = RequestOptions(),
    ): Result<Credentials, AuthenticationError>

    /**
     * Requests a challenge to sign in an existing user with a passkey.
     *
     * @param realm the name of the connection to authenticate against, or `null`.
     * @param organization the organization to authenticate within, or `null`.
     * @param options per-call transport options (extra parameters, headers, retry policy).
     * @return [Result.Success] with the [PasskeyLoginChallenge], or [Result.Failure]
     *   with the [AuthenticationError] that occurred.
     */
    public suspend fun passkeyLoginChallenge(
        realm: String? = null,
        organization: String? = null,
        options: RequestOptions = RequestOptions(),
    ): Result<PasskeyLoginChallenge, AuthenticationError>

    /**
     * Requests a challenge to register a new user with a passkey.
     *
     * @param profile the structured profile of the user to register.
     * @param userMetadata additional user metadata to store on the registered user;
     *   empty to send none.
     * @param realm the name of the connection to register the user in, or `null`.
     * @param organization the organization to register the user within, or `null`.
     * @param options per-call transport options (extra parameters, headers, retry policy).
     * @return [Result.Success] with the [PasskeyRegistrationChallenge], or
     *   [Result.Failure] with the [AuthenticationError] that occurred.
     */
    public suspend fun passkeySignupChallenge(
        profile: SignupProfile,
        userMetadata: Map<String, String> = emptyMap(),
        realm: String? = null,
        organization: String? = null,
        options: RequestOptions = RequestOptions(),
    ): Result<PasskeyRegistrationChallenge, AuthenticationError>

    /**
     * Logs a user in with a completed passkey ceremony.
     *
     * @param authSession the `auth_session` returned by [passkeyLoginChallenge].
     * @param authResponse the public-key credential produced by the WebAuthn ceremony.
     * @param realm the name of the connection to authenticate against, or `null`.
     * @param organization the organization to authenticate within, or `null`.
     * @param audience the API identifier to request an access token for, or `null`.
     * @param scope the requested scopes; includes `offline_access` by default so the
     *   tenant issues a refresh token.
     * @param options per-call transport options (extra parameters, headers, retry policy).
     * @return [Result.Success] with the issued [Credentials], or [Result.Failure]
     *   with the [AuthenticationError] that occurred.
     */
    public suspend fun loginWithPasskey(
        authSession: String,
        authResponse: PublicKeyCredentials,
        realm: String? = null,
        organization: String? = null,
        audience: String? = null,
        scope: String = "openid profile email offline_access",
        options: RequestOptions = RequestOptions(),
    ): Result<Credentials, AuthenticationError>

    /**
     * Releases the network transport backing this client.
     *
     * Call this only when the client was obtained from a standalone factory
     * (`authenticationClient(account)`). When obtained from `Auth0`, close it via
     * `Auth0.close()` instead.
     */
    override fun close() {}
}
