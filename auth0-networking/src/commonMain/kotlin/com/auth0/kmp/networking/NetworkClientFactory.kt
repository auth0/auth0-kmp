package com.auth0.kmp.networking

import com.auth0.kmp.core.Auth0Account
import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.networking.transport.DefaultNetworkClient
import com.auth0.kmp.networking.transport.EndpointResolver
import com.auth0.kmp.networking.transport.buildHttpClient

/**
 * Creates a [NetworkClient] for the given [account], configured by [configuration].
 *
 * @param account the tenant/application coordinates requests are sent to.
 * @param configuration the network transport settings.
 */
@InternalAuth0Api
public fun networkClient(
    account: Auth0Account,
    configuration: NetworkingConfiguration = NetworkingConfiguration(),
): NetworkClient = DefaultNetworkClient(
    client = buildHttpClient(configuration),
    resolver = EndpointResolver(account),
)
