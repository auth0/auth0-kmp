package com.auth0.kmp.authentication

import com.auth0.kmp.authentication.error.AuthenticationError
import com.auth0.kmp.core.model.Credentials
import com.auth0.kmp.core.result.Result

/**
 * Performs authentication operations against an Auth0 tenant.
 *
 * Holds a network engine and its connection pool; call [close] to release them
 * when the client is no longer needed.
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
     * @return [Result.Success] with the issued [Credentials], or [Result.Failure]
     *   with the [AuthenticationError] that occurred.
     */
    public suspend fun login(
        usernameOrEmail: String,
        password: String,
        realm: String,
        audience: String? = null,
        scope: String = "openid profile email",
    ): Result<Credentials, AuthenticationError>
}
