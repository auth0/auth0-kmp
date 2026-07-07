package com.auth0.kmp.webauth.browser

import com.auth0.kmp.core.Auth0Account
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.webauth.error.WebAuthError

internal interface BrowserAgent {

    /**
     * Derives the redirect URI the SDK registers for [account] on this platform.
     *
     * @param account the tenant/application coordinates the redirect is built from.
     * @param scheme the scheme to use; when `null`, the platform default applies.
     * @return the full redirect callback URL.
     */
    fun defaultRedirectUri(account: Auth0Account, scheme: String?): String

    /**
     * Opens [url] in a browser and suspends until the flow returns to the app
     * via [callbackScheme], is cancelled, or fails.
     *
     * @param url the Auth0 URL to present (for example `/authorize` or
     *   `/v2/logout`).
     * @param callbackScheme the scheme the browser redirects back to.
     * @param ephemeral when `true`, the browser session shares no cookies or
     *   storage with the system browser.
     * @return the full redirect callback URL on success, or a [WebAuthError].
     */
    suspend fun launch(
        url: String,
        callbackScheme: String,
        ephemeral: Boolean,
    ): Result<String, WebAuthError>
}
