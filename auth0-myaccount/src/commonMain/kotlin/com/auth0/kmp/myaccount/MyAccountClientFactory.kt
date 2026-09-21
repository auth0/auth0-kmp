package com.auth0.kmp.myaccount

import com.auth0.kmp.core.Auth0Account
import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.useragent.Auth0UserAgent
import com.auth0.kmp.core.useragent.UserAgent
import com.auth0.kmp.networking.NetworkClient
import com.auth0.kmp.networking.networkClient

/**
 * Creates a [MyAccountClient] for the given [account] and [accessToken], reusing
 * the supplied [networkClient].
 *
 * The returned client does not own [networkClient]; closing it does not close the
 * transport. Use this overload when sharing a transport across clients.
 *
 * @param account the tenant/application coordinates requests are sent to.
 * @param accessToken the My Account API access token authorizing the requests.
 * @param networkClient the transport requests are sent over.
 */
@OptIn(InternalAuth0Api::class)
@InternalAuth0Api
public fun myAccountClient(
    account: Auth0Account,
    accessToken: String,
    networkClient: NetworkClient,
): MyAccountClient =
    DefaultMyAccountClient(
        accessToken = accessToken,
        useDPoP = account.useDPoP,
        networkClient = networkClient,
    )

/**
 * Creates a [MyAccountClient] for the given [account] and [accessToken].
 *
 * The returned client owns its transport; call [MyAccountClient.close] when it is
 * no longer needed.
 *
 * @param account the tenant/application coordinates requests are sent to.
 * @param accessToken the My Account API access token authorizing the requests.
 * @param userAgent identifies the client library in the `Auth0-Client` header;
 *   defaults to this SDK's identity.
 */
@OptIn(InternalAuth0Api::class)
public fun myAccountClient(
    account: Auth0Account,
    accessToken: String,
    userAgent: UserAgent = Auth0UserAgent.default(),
): MyAccountClient {
    val network = networkClient(account, userAgent)
    val client = myAccountClient(account, accessToken, network)
    return object : MyAccountClient by client {
        override fun close() {
            network.close()
        }
    }
}
