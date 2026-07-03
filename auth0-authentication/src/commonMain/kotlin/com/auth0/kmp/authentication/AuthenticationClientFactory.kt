package com.auth0.kmp.authentication

import com.auth0.kmp.core.Auth0Account
import com.auth0.kmp.core.useragent.Auth0UserAgent
import com.auth0.kmp.core.useragent.UserAgent
import com.auth0.kmp.core.validation.IdTokenClaimsValidator
import com.auth0.kmp.networking.networkClient
import com.auth0.kmp.networking.normalizedBaseUrl
import kotlin.time.Clock

/**
 * Creates an [AuthenticationClient] for the given [account].
 *
 * @param account the tenant/application coordinates requests are sent to.
 * @param userAgent identifies the client library in the `Auth0-Client` header;
 *   defaults to this SDK's identity.
 */
public fun authenticationClient(
    account: Auth0Account,
    userAgent: UserAgent = Auth0UserAgent.default(),
): AuthenticationClient {
    val clock = Clock.System
    return DefaultAuthenticationClient(
        clientId = account.clientId,
        networkClient = networkClient(account, userAgent),
        idTokenValidator = IdTokenClaimsValidator(
            issuer = normalizedBaseUrl(account),
            audience = account.clientId,
            clock = clock,
        ),
        clock = clock,
    )
}
