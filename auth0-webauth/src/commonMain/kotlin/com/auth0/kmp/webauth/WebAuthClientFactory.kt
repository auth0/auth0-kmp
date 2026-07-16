package com.auth0.kmp.webauth

import com.auth0.kmp.core.Auth0Account
import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.dpop.DPoPRegistry
import com.auth0.kmp.core.token.tokenClient
import com.auth0.kmp.core.useragent.Auth0UserAgent
import com.auth0.kmp.core.useragent.UserAgent
import com.auth0.kmp.core.validation.IdTokenClaimsValidator
import com.auth0.kmp.networking.NetworkClient
import com.auth0.kmp.networking.networkClient
import com.auth0.kmp.networking.normalizedBaseUrl
import com.auth0.kmp.webauth.browser.createBrowserAgent
import com.auth0.kmp.webauth.jwks.DefaultJwksProvider
import com.auth0.kmp.webauth.transaction.InMemoryTransactionStore
import com.auth0.kmp.webauth.validation.Rs256IdTokenSignatureValidator
import kotlin.time.Clock

/**
 * Creates a [WebAuthClient] for the given [account] over an existing
 * [networkClient], so a caller that already owns transport for the account can
 * reuse it. The returned client borrows the transport and does not close it.
 *
 * @param account the tenant/application coordinates the login is performed against.
 * @param networkClient the transport requests are sent over.
 * @param userAgent identifies the client library in the `Auth0-Client` header;
 *   defaults to this SDK's identity.
 */
@OptIn(InternalAuth0Api::class)
@InternalAuth0Api
public fun webAuthClient(
    account: Auth0Account,
    networkClient: NetworkClient,
    userAgent: UserAgent = Auth0UserAgent.default(),
): WebAuthClient {
    val clock = Clock.System
    val collaborators = if (account.useDPoP) DPoPRegistry.Default.collaboratorsFor(account) else null
    return DefaultWebAuthClient(
        account = account,
        browser = createBrowserAgent(),
        store = InMemoryTransactionStore(),
        tokenClient = tokenClient(networkClient, clock),
        signatureValidator = Rs256IdTokenSignatureValidator(DefaultJwksProvider(networkClient)),
        claimsValidator = IdTokenClaimsValidator(
            issuer = normalizedBaseUrl(account),
            audience = account.clientId,
            clock = clock,
        ),
        proofGenerator = collaborators?.proofGenerator,
        keygenLock = collaborators?.keygenLock,
    )
}

/**
 * Creates a [WebAuthClient] for the given [account].
 *
 * @param account the tenant/application coordinates the login is performed against.
 * @param userAgent identifies the client library in the `Auth0-Client` header;
 *   defaults to this SDK's identity.
 */
@OptIn(InternalAuth0Api::class)
public fun webAuthClient(
    account: Auth0Account,
    userAgent: UserAgent = Auth0UserAgent.default(),
): WebAuthClient {
    val network = networkClient(account, userAgent)
    val client = webAuthClient(account, network, userAgent)
    return object : WebAuthClient by client {
        override fun close() {
            network.close()
        }
    }
}
