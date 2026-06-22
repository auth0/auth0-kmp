package com.auth0.kmp.networking

import com.auth0.kmp.core.Auth0Account
import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.networking.transport.DefaultNetworkClient
import com.auth0.kmp.networking.transport.EndpointResolver
import com.auth0.kmp.networking.transport.buildHttpClient

/**
 * Creates a [NetworkClient] for the given [account].
 *
 * @param account the tenant/application coordinates requests are sent to.
 */
@InternalAuth0Api
public fun networkClient(account: Auth0Account): NetworkClient = DefaultNetworkClient(
    client = buildHttpClient(account.configuration),
    resolver = EndpointResolver(account),
)

/**
 * Returns the normalized HTTPS base URL for [account]'s domain, e.g.
 * `https://your-tenant.auth0.com/`.
 *
 * @param account the tenant/application coordinates whose domain is normalized.
 */
@InternalAuth0Api
public fun normalizedBaseUrl(account: Auth0Account): String =
    EndpointResolver(account).baseUrl
