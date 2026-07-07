package com.auth0.kmp.core.validation.model

import com.auth0.kmp.core.annotation.InternalAuth0Api

@InternalAuth0Api
public data class JwtClaims(
    val issuer: String?,
    val subject: String?,
    val audience: List<String>,
    val expiresAt: Long?,
    val issuedAt: Long?,
    val nonce: String?,
    val authorizedParty: String?,
    val authTime: Long?,
    val organizationId: String?,
    val organizationName: String?,
)
