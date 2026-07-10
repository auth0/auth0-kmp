package com.auth0.kmp.credentials

import com.auth0.kmp.core.Auth0Account
import com.auth0.kmp.core.credentials.CredentialsManager
import com.auth0.kmp.core.token.tokenClient
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
public fun credentialsManager(
    account: Auth0Account,
    storeKey: String,
    storage: Storage,
): CredentialsManager = DefaultCredentialsManager(
    clientId = account.clientId,
    tokenClient = tokenClient(account),
    storage = storage,
    storeKey = storeKey,
    clock = Clock.System,
)

/** Returns the platform's default secure [Storage] implementation. */
internal expect fun createStorage(): Storage
