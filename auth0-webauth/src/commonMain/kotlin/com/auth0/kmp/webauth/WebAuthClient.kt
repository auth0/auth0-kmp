package com.auth0.kmp.webauth

import com.auth0.kmp.core.model.Credentials
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.webauth.error.WebAuthError

/**
 * Performs browser-based (Web Auth) login against Auth0 Universal Login.
 */
public interface WebAuthClient : AutoCloseable {

    /**
     * Launches a browser-based login and suspends until it completes.
     *
     * @param options the login parameters; defaults request `openid profile
     *   email offline_access` with an SDK-derived redirect URI.
     * @return the [Credentials] on success, or a [WebAuthError] describing the
     *   failure.
     */
    public suspend fun login(options: LoginOptions = LoginOptions()): Result<Credentials, WebAuthError>

    /**
     * Launches a browser-based logout and suspends until it completes.
     *
     * @param options the logout parameters; defaults to a non-federated logout
     *   with an SDK-derived return URL.
     * @return [Unit] on success, or a [WebAuthError] describing the failure.
     */
    public suspend fun logout(options: LogoutOptions = LogoutOptions()): Result<Unit, WebAuthError>

    /** Cancels an in-progress login, if any. */
    public fun cancel()

    /**
     * Releases the network transport backing this client.
     *
     * Call this only when the client was obtained from a standalone factory
     * (`webAuthClient(account)`). When obtained from `Auth0`, close it via `Auth0.close()` instead;
     */
    override fun close() {}
}
