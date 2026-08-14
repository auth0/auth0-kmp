package com.auth0.kmp

import com.auth0.kmp.authentication.AuthenticationClient
import com.auth0.kmp.authentication.authenticationClient
import com.auth0.kmp.core.Auth0Account
import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.credentials.CredentialsManager
import com.auth0.kmp.credentials.Storage
import com.auth0.kmp.credentials.credentialsManager
import com.auth0.kmp.credentials.defaultCredentialsStoreKey
import com.auth0.kmp.networking.NetworkClient
import com.auth0.kmp.networking.networkClient
import com.auth0.kmp.webauth.WebAuthClient
import com.auth0.kmp.webauth.webAuthClient

/**
 * Entry point to the Auth0 SDK for a single [Auth0Account].
 *
 * Owns one network transport shared by [webAuth], [authentication], and the
 * credentials managers from [credentials]. [webAuth] and [authentication] are
 * cached and created on first access; [credentials] returns a new manager on
 * each call. Call [close] to release the shared transport when this instance is
 * no longer needed.
 */
@OptIn(InternalAuth0Api::class)
public class Auth0 internal constructor(
    private val networkClient: NetworkClient,
    private val defaultStoreKey: String,
    private val buildWebAuth: (NetworkClient) -> WebAuthClient,
    private val buildAuthentication: (NetworkClient) -> AuthenticationClient,
    private val buildCredentials: (NetworkClient, storeKey: String, storage: Storage?) -> CredentialsManager,
) : AutoCloseable {

    /**
     * Creates an SDK entry point for the given account.
     *
     * @param account the tenant/application coordinates every client communicates with.
     */
    public constructor(account: Auth0Account) : this(
        networkClient = networkClient(account),
        defaultStoreKey = defaultCredentialsStoreKey(account),
        buildWebAuth = { network -> webAuthClient(account, network) },
        buildAuthentication = { network -> authenticationClient(account, network) },
        buildCredentials = { network, storeKey, storage ->
            if (storage == null) credentialsManager(account, network, storeKey)
            else credentialsManager(account, network, storeKey, storage)
        },
    )

    /** Performs browser-based (Web Auth) login and logout. */
    public val webAuth: WebAuthClient by lazy { buildWebAuth(networkClient) }

    /** Performs authentication operations such as database login. */
    public val authentication: AuthenticationClient by lazy { buildAuthentication(networkClient) }

    /**
     * Creates a credentials manager backed by the shared transport, persisting
     * under [storeKey] in the platform's default secure storage.
     *
     * Unlike [webAuth] and [authentication], this returns a new manager on every
     * call; it is not cached. All managers for the same [storeKey] read and write
     * the same underlying store.
     *
     * @param storeKey the key credentials are stored under; defaults to a key
     *   scoped to the account's client ID.
     */
    public fun credentials(
        storeKey: String = defaultStoreKey,
    ): CredentialsManager =
        buildCredentials(networkClient, storeKey, null)

    /**
     * Creates a credentials manager backed by the shared transport, persisting
     * under [storeKey] in the supplied [storage].
     *
     * Unlike [webAuth] and [authentication], this returns a new manager on every
     * call; it is not cached. All managers for the same [storeKey] read and write
     * the same underlying store.
     *
     * @param storeKey the key credentials are stored under.
     * @param storage the secure store to persist credentials in.
     */
    public fun credentials(
        storeKey: String,
        storage: Storage,
    ): CredentialsManager =
        buildCredentials(networkClient, storeKey, storage)

    /** Releases the shared network transport. Clients must not be used afterwards. */
    override fun close() {
        networkClient.close()
    }
}
