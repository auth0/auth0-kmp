package com.auth0.kmp.core.credentials

import com.auth0.kmp.core.model.Credentials
import com.auth0.kmp.core.result.Result

/**
 * Securely stores [Credentials] on-device and returns them on demand, renewing
 * an expired or soon-to-expire access token via the refresh token first.
 */
public interface CredentialsManager {

    /**
     * Stores [credentials], replacing any previously stored credentials.
     *
     * This method is Not thread-safe
     *
     * @param credentials the credentials to persist.
     * @return [Result.Success] on success, or a [CredentialsManagerError] on
     *   a storage failure.
     */
    public suspend fun saveCredentials(credentials: Credentials): Result<Unit, CredentialsManagerError>

    /**
     * Removes any stored credentials.
     *
     * This method is Not thread-safe
     */
    public suspend fun clearCredentials(): Result<Unit, CredentialsManagerError>

    /**
     * Returns whether valid credentials are stored.
     *
     * @param minTtl the minimum remaining lifetime, in seconds, the access token
     *   must have to be considered valid.
     */
    public suspend fun hasValidCredentials(minTtl: Long = 0): Boolean

    /**
     * Returns the stored credentials, renewing them first when required.
     *
     * @param scope the scopes to request on renewal; a value different from the
     *   stored scope forces a renewal.
     * @param minTtl the minimum remaining lifetime, in seconds, the returned
     *   access token must have; a shorter-lived token triggers a renewal.
     * @param parameters extra `/oauth/token` form parameters for the renewal.
     * @param headers extra HTTP headers for the renewal request.
     * @param forceRefresh when true, always renews even if the stored token is valid.
     * @return [Result.Success] with the credentials, or a [CredentialsManagerError].
     */
    public suspend fun getCredentials(
        scope: String? = null,
        minTtl: Int = 30,
        parameters: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        forceRefresh: Boolean = false,
    ): Result<Credentials, CredentialsManagerError>
}
