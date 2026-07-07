package com.auth0.kmp.core.token

import com.auth0.kmp.core.Auth0Account
import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.useragent.Auth0UserAgent
import com.auth0.kmp.core.useragent.UserAgent
import com.auth0.kmp.networking.networkClient
import kotlin.time.Clock

/**
 * Creates a [TokenClient] for the given [account].
 *
 * @param account the tenant/application coordinates the token request is sent to.
 * @param userAgent identifies the client library in the `Auth0-Client` header;
 *   defaults to this SDK's identity.
 */
@InternalAuth0Api
public fun tokenClient(
    account: Auth0Account,
    userAgent: UserAgent = Auth0UserAgent.default(),
): TokenClient = DefaultTokenClient(
    networkClient = networkClient(account, userAgent),
    clock = Clock.System,
)