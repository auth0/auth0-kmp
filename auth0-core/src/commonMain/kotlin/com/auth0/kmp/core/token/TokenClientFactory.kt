package com.auth0.kmp.core.token

import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.networking.NetworkClient
import kotlin.time.Clock

/**
 * Creates a [TokenClient] over an existing [networkClient], so a caller that
 * already owns transport for an account can reuse it.
 *
 * @param networkClient the transport token requests are sent over.
 * @param clock the time source used to compute credential expiry.
 */
@InternalAuth0Api
public fun tokenClient(
    networkClient: NetworkClient,
    clock: Clock = Clock.System,
): TokenClient = DefaultTokenClient(
    networkClient = networkClient,
    clock = clock,
)
