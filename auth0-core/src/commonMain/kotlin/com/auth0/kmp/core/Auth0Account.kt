package com.auth0.kmp.core

/**
 * Identifies the Auth0 tenant and application the SDK communicates with.
 *
 * @param clientId the Client ID of your application, from the Auth0 dashboard.
 * @param domain your Auth0 tenant domain, e.g. `your-tenant.us.auth0.com`.
 */
class Auth0Account(
    val clientId: String,
    val domain: String,
)
