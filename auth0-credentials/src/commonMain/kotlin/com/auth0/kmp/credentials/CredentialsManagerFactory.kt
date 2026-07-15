package com.auth0.kmp.credentials

import com.auth0.kmp.core.Auth0Account
import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.credentials.CredentialsManager
import com.auth0.kmp.core.dpop.DPoPRegistry
import com.auth0.kmp.core.token.tokenClient
import com.auth0.kmp.core.useragent.Auth0UserAgent
import com.auth0.kmp.networking.NetworkClient
import com.auth0.kmp.networking.networkClient
import kotlin.time.Clock

/**
 * Creates a [CredentialsManager] for the given [account], using a store key
 * scoped to the account's client ID and the platform secure storage.
 *
 * @param account the tenant/application coordinates renewals are sent to.
 */
public fun credentialsManager(
    account: Auth0Account,
): CredentialsManager =
    credentialsManager(account, "credentials_${account.clientId}", createStorage())

/**
 * Creates a [CredentialsManager] for the given [account] and [storeKey], using
 * the platform secure storage.
 *
 * @param account the tenant/application coordinates renewals are sent to.
 * @param storeKey the key credentials are stored under.
 */
public fun credentialsManager(
    account: Auth0Account,
    storeKey: String,
): CredentialsManager =
    credentialsManager(account, storeKey, createStorage())

/**
 * Creates a [CredentialsManager] for the given [account], persisting credentials
 * under [storeKey] in the supplied [storage].
 *
 * @param account the tenant/application coordinates renewals are sent to.
 * @param storeKey the key credentials are stored under.
 * @param storage the secure store to persist credentials in.
 */
@OptIn(InternalAuth0Api::class)
public fun credentialsManager(
    account: Auth0Account,
    storeKey: String,
    storage: Storage,
): CredentialsManager {
    val network = networkClient(account, Auth0UserAgent.default())
    val client = credentialsManager(account, network, storeKey, storage)
    return object : CredentialsManager by client {
        override fun close() {
            network.close()
        }
    }
}

/**
 * Creates a [CredentialsManager] for the given [account] over an existing
 * [networkClient], so a caller that already owns transport for the account can
 * reuse it. The returned manager borrows the transport and does not close it.
 *
 * @param account the tenant/application coordinates renewals are sent to.
 * @param networkClient the transport renewals are sent over.
 * @param storeKey the key credentials are stored under.
 * @param storage the secure store to persist credentials in.
 */
@OptIn(InternalAuth0Api::class)
@InternalAuth0Api
public fun credentialsManager(
    account: Auth0Account,
    networkClient: NetworkClient,
    storeKey: String = "credentials_${account.clientId}",
    storage: Storage = createStorage(),
): CredentialsManager {
    val collaborators = DPoPRegistry.Default.collaboratorsFor(account)
    return DefaultCredentialsManager(
        clientId = account.clientId,
        tokenClient = tokenClient(networkClient, Clock.System),
        storage = storage,
        storeKey = storeKey,
        clock = Clock.System,
        proofGenerator = collaborators.proofGenerator,
        useDPoP = account.useDPoP,
    )
}

/** Returns the platform's default secure [Storage] implementation. */
internal expect fun createStorage(): Storage
