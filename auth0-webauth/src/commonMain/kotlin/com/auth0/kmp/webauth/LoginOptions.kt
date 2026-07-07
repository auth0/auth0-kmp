package com.auth0.kmp.webauth

/**
 * Options for a Web Auth (browser-based) login.
 *
 * @property scope the OAuth scopes to request; defaults to
 *   `openid profile email offline_access`.
 * @property audience the API identifier to request an access token for, when
 *   calling your own API.
 * @property connection the specific connection to authenticate with; when
 *   omitted, Auth0 presents its Universal Login connection selector.
 * @property organization the organization to log the user in to; the returned
 *   ID token's organization claim is validated against it.
 * @property prompt the OAuth `prompt` parameter, for example `login` to force
 *   re-authentication.
 * @property maxAge the maximum authentication age in seconds; the returned ID
 *   token's `auth_time` claim is validated against it.
 * @property redirectUri a full callback URL that overrides the one the SDK
 *   derives; when `null`, the SDK-generated redirect URI is used.
 * @property scheme the scheme of the SDK-derived redirect URI; when `null`, the
 *   platform default is used. Ignored when [redirectUri] is set.
 * @property ephemeral when `true`, the browser session shares no cookies or
 *   storage with the system browser.
 * @property extraParameters additional query parameters appended to the
 *   `/authorize` request.
 */
public data class LoginOptions(
    val scope: String = "openid profile email offline_access",
    val audience: String? = null,
    val connection: String? = null,
    val organization: String? = null,
    val prompt: String? = null,
    val maxAge: Long? = null,
    val redirectUri: String? = null,
    val scheme: String? = null,
    val ephemeral: Boolean = false,
    val extraParameters: Map<String, String> = emptyMap(),
)
