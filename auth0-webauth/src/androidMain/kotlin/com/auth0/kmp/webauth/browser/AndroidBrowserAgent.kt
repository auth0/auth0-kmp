package com.auth0.kmp.webauth.browser

import android.content.Intent
import com.auth0.kmp.core.Auth0Account
import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.context.ApplicationContextHolder
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.webauth.error.WebAuthError
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Android [BrowserAgent] that presents the `/authorize` URL in an Auth Tab and
 * captures the redirect via the SDK-internal [WebAuthActivity].
 */
@OptIn(InternalAuth0Api::class)
internal class AndroidBrowserAgent : BrowserAgent {

    private val applicationContext get() = ApplicationContextHolder.context

    override fun defaultRedirectUri(account: Auth0Account, scheme: String?): String =
        buildAndroidRedirectUri(account, scheme, applicationContext.packageName)

    override suspend fun launch(
        url: String,
        callbackScheme: String,
        ephemeral: Boolean,
    ): Result<String, WebAuthError> = suspendCancellableCoroutine { continuation ->
        if (!WebAuthResultBridge.register(continuation)) {
            continuation.resume(Result.Failure(WebAuthError.TransactionActiveAlready))
            return@suspendCancellableCoroutine
        }

        continuation.invokeOnCancellation {
            WebAuthResultBridge.clear()
        }

        val intent = Intent(applicationContext, WebAuthActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(WebAuthActivity.EXTRA_AUTHORIZE_URL, url)
            putExtra(WebAuthActivity.EXTRA_CALLBACK_SCHEME, callbackScheme)
            putExtra(WebAuthActivity.EXTRA_EPHEMERAL, ephemeral)
        }
        applicationContext.startActivity(intent)
    }


    /**
     * Assembles the SDK-derived redirect URI, defaulting the scheme to [packageName]
     * when none is supplied.
     */
    internal fun buildAndroidRedirectUri(
        account: Auth0Account,
        scheme: String?,
        packageName: String,
    ): String {
        val effectiveScheme = scheme ?: packageName
        return "$effectiveScheme://${account.domain}/android/$packageName/callback"
    }
}
