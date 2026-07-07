package com.auth0.kmp.core.validation.model

import com.auth0.kmp.core.annotation.InternalAuth0Api

@InternalAuth0Api
public data class JwtHeader(
    val algorithm: String?,
    val keyId: String?,
)
