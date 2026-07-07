package com.auth0.kmp.webauth

/**
 * Options for a Web Auth (browser-based) logout.
 *
 * @property returnTo a full URL the browser is redirected to after the session
 *   is cleared; when `null`, the SDK-derived redirect URI is used. The URL must
 *   be registered in the tenant's Allowed Logout URLs.
 * @property scheme the scheme of the SDK-derived return URL; when `null`, the
 *   platform default is used. Ignored when [returnTo] is set.
 * @property federated when `true`, also clears the session at the upstream
 *   identity provider, not just the Auth0 session.
 * @property extraParameters additional query parameters appended to the
 *   `/v2/logout` request.
 */
public data class LogoutOptions(
    val returnTo: String? = null,
    val scheme: String? = null,
    val federated: Boolean = false,
    val extraParameters: Map<String, String> = emptyMap(),
)
