package com.auth0.kmp.authentication

import com.auth0.kmp.authentication.validation.ClaimsIdTokenValidator
import com.auth0.kmp.core.Auth0Account
import com.auth0.kmp.networking.networkClient
import com.auth0.kmp.networking.normalizedBaseUrl
import kotlin.time.Clock

/**
 * Creates an [AuthenticationClient] for the given [account].
 *
 * @param account the tenant/application coordinates requests are sent to.
 */
public fun authenticationClient(account: Auth0Account): AuthenticationClient {
    val clock = Clock.System
    return DefaultAuthenticationClient(
        clientId = account.clientId,
        networkClient = networkClient(account),
        idTokenValidator = ClaimsIdTokenValidator(
            issuer = normalizedBaseUrl(account),
            audience = account.clientId,
            clock = clock,
        ),
        clock = clock,
    )
}
