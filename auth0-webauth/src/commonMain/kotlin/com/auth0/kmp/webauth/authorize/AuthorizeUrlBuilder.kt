package com.auth0.kmp.webauth.authorize

import com.auth0.kmp.core.Auth0Account
import com.auth0.kmp.networking.normalizedBaseUrl
import com.auth0.kmp.webauth.LoginOptions
import com.auth0.kmp.webauth.transaction.AuthorizeTransaction
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments

internal fun buildAuthorizeUrl(
    account: Auth0Account,
    transaction: AuthorizeTransaction,
    options: LoginOptions,
): String =
    URLBuilder(normalizedBaseUrl(account)).apply {
        appendPathSegments("authorize")
        parameters.apply {
            append("response_type", "code")
            append("client_id", account.clientId)
            append("redirect_uri", transaction.redirectUri)
            append("scope", options.scope)
            append("state", transaction.state)
            append("nonce", transaction.nonce)
            append("code_challenge", transaction.pkce.codeChallenge)
            append("code_challenge_method", transaction.pkce.codeChallengeMethod)
            options.audience?.let { append("audience", it) }
            options.connection?.let { append("connection", it) }
            options.organization?.let { append("organization", it) }
            options.prompt?.let { append("prompt", it) }
            options.maxAge?.let { append("max_age", it.toString()) }
            options.extraParameters.forEach { (key, value) -> append(key, value) }
        }
    }.buildString()
