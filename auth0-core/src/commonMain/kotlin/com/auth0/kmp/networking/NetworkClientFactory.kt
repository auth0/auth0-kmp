package com.auth0.kmp.networking

import com.auth0.kmp.core.Auth0Account
import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.useragent.Auth0UserAgent
import com.auth0.kmp.core.useragent.UserAgent
import com.auth0.kmp.networking.transport.DefaultNetworkClient
import com.auth0.kmp.networking.transport.EndpointResolver
import com.auth0.kmp.networking.transport.buildHttpClient

/**
 * Creates a [NetworkClient] for the given [account].
 *
 * @param account the tenant/application coordinates requests are sent to.
 * @param userAgent identifies the client library in the `Auth0-Client` header of
 *   every request; defaults to this SDK's identity.
 */
@InternalAuth0Api
public fun networkClient(
    account: Auth0Account,
    userAgent: UserAgent = Auth0UserAgent.default(),
): NetworkClient {
    val configuration = account.configuration.copy(
        defaultHeaders = account.configuration.defaultHeaders +
            (userAgent.headerName to userAgent.value),
    )
    return DefaultNetworkClient(
        client = buildHttpClient(configuration),
        resolver = EndpointResolver(account),
    )
}

/**
 * Returns the normalized HTTPS base URL for [account]'s domain, e.g.
 * `https://your-tenant.auth0.com/`.
 *
 * @param account the tenant/application coordinates whose domain is normalized.
 */
@InternalAuth0Api
public fun normalizedBaseUrl(account: Auth0Account): String =
    EndpointResolver(account).baseUrl
