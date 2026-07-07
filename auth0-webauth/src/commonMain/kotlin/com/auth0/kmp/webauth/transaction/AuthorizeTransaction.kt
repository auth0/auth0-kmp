package com.auth0.kmp.webauth.transaction

import com.auth0.kmp.webauth.pkce.Pkce

internal data class AuthorizeTransaction(
    val state: String,
    val nonce: String,
    val pkce: Pkce,
    val redirectUri: String,
)
