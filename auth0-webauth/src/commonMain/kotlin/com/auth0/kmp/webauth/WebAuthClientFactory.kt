package com.auth0.kmp.webauth

import com.auth0.kmp.core.Auth0Account
import com.auth0.kmp.core.useragent.Auth0UserAgent
import com.auth0.kmp.core.useragent.UserAgent
import com.auth0.kmp.core.validation.IdTokenClaimsValidator
import com.auth0.kmp.networking.networkClient
import com.auth0.kmp.networking.normalizedBaseUrl
import com.auth0.kmp.webauth.browser.createBrowserAgent
import com.auth0.kmp.webauth.jwks.DefaultJwksProvider
import com.auth0.kmp.webauth.transaction.InMemoryTransactionStore
import com.auth0.kmp.webauth.validation.Rs256IdTokenSignatureValidator
import kotlin.time.Clock

/**
 * Creates a [WebAuthClient] for the given [account].
 *
 * @param account the tenant/application coordinates the login is performed against.
 * @param userAgent identifies the client library in the `Auth0-Client` header;
 *   defaults to this SDK's identity.
 */
public fun webAuthClient(
    account: Auth0Account,
    userAgent: UserAgent = Auth0UserAgent.default(),
): WebAuthClient {
    val clock = Clock.System
    val networkClient = networkClient(account, userAgent)
    return DefaultWebAuthClient(
        account = account,
        browser = createBrowserAgent(),
        store = InMemoryTransactionStore(),
        networkClient = networkClient,
        signatureValidator = Rs256IdTokenSignatureValidator(DefaultJwksProvider(networkClient)),
        claimsValidator = IdTokenClaimsValidator(
            issuer = normalizedBaseUrl(account),
            audience = account.clientId,
            clock = clock,
        ),
        clock = clock,
    )
}
