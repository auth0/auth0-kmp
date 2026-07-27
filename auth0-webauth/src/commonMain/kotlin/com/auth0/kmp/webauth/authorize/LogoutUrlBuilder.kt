package com.auth0.kmp.webauth.authorize

import com.auth0.kmp.core.Auth0Account
import com.auth0.kmp.networking.normalizedBaseUrl
import com.auth0.kmp.webauth.LogoutOptions
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments

private val LOGOUT_RESERVED_PARAMS = setOf("client_id", "returnTo", "federated")

internal fun buildLogoutUrl(
    account: Auth0Account,
    returnTo: String,
    options: LogoutOptions,
): String =
    URLBuilder(normalizedBaseUrl(account)).apply {
        appendPathSegments("v2", "logout")
        parameters.apply {
            append("client_id", account.clientId)
            append("returnTo", returnTo)
            if (options.federated) append("federated", "1")
            appendExtraParameters(options.extraParameters, LOGOUT_RESERVED_PARAMS)
        }
    }.buildString()
