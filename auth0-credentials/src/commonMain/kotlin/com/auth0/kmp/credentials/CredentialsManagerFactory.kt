package com.auth0.kmp.credentials

import com.auth0.kmp.core.Auth0Account
import com.auth0.kmp.core.credentials.CredentialsManager
import com.auth0.kmp.core.token.tokenClient
import kotlin.time.Clock

/**
 * Creates a [CredentialsManager] for the given [account].
 *
 * @param account the tenant/application coordinates renewals are sent to, and
 *   the source of the default [storeKey].
 * @param storeKey the key credentials are stored under; defaults to a value
 *   scoped to the account's client ID.
 * @param storage the secure store to persist credentials in; defaults to the
 *   platform secure store.
 */
public fun credentialsManager(
    account: Auth0Account,
    storeKey: String = "credentials_${account.clientId}",
    storage: Storage = createStorage(),
): CredentialsManager = DefaultCredentialsManager(
    clientId = account.clientId,
    tokenClient = tokenClient(account),
    storage = storage,
    storeKey = storeKey,
    clock = Clock.System,
)

/** Returns the platform's default secure [Storage] implementation. */
internal expect fun createStorage(): Storage
