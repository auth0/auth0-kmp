package com.auth0.kmp.webauth.browser

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.browser.auth.AuthTabIntent
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.auth0.kmp.core.logging.Auth0Log
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.webauth.error.WebAuthError

/**
 * Translucent, SDK-internal Activity that presents the authorize/logout URL in a
 * browser and relays the redirect result back through [WebAuthResultBridge].
 *
 * Prefers an Auth Tab (redirect delivered via the Activity Result API). When the
 * browser does not support Auth Tab, falls back to a plain Custom Tab whose
 * custom-scheme redirect is trampolined back here by [RedirectActivity].
 *
 * Started only by [AndroidBrowserAgent].
 */
internal class WebAuthActivity : ComponentActivity() {

    private var intentLaunched = false

    private val launcher = AuthTabIntent.registerActivityResultLauncher(this) { result ->
        WebAuthResultBridge.resolve(result.toWebAuthResult())
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            intentLaunched = savedInstanceState.getBoolean(EXTRA_INTENT_LAUNCHED, false)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(EXTRA_INTENT_LAUNCHED, intentLaunched)
    }

    override fun onResume() {
        super.onResume()
        // Auth Tab delivers its result via the ActivityResult callback above,
        // which finishes this Activity. Skip the resume path so we don't treat
        // the missing redirect data as a cancellation and deliver a spurious
        // second result.
        if (isFinishing) return

        if (!intentLaunched) {
            intentLaunched = true
            launchBrowser()
            return
        }

        // Re-entered after a Custom Tab: RedirectActivity trampolined the redirect
        // here (intent.data set), or the user backed out (intent.data null).
        val redirectData = intent?.data
        val result = if (redirectData != null) {
            Result.Success(redirectData.toString())
        } else {
            Result.Failure(WebAuthError.UserCancelled)
        }
        WebAuthResultBridge.resolve(result)
        finish()
    }

    private fun launchBrowser() {
        val authorizeUrl = intent.getStringExtra(EXTRA_AUTHORIZE_URL)
        val scheme = intent.getStringExtra(EXTRA_CALLBACK_SCHEME)
        val ephemeral = intent.getBooleanExtra(EXTRA_EPHEMERAL, false)

        if (authorizeUrl == null || scheme == null) {
            Auth0Log.e(TAG, "Missing browser launch parameters; cannot start authorization")
            WebAuthResultBridge.resolve(
                Result.Failure(WebAuthError.BrowserError("Missing browser launch parameters."))
            )
            finish()
            return
        }

        val browserPackage = CustomTabsClient.getPackageName(this, emptyList())
        val authTabSupported = browserPackage != null &&
                CustomTabsClient.isAuthTabSupported(this, browserPackage)

        Auth0Log.d(TAG, "Launching browser via ${if (authTabSupported) "Auth Tab" else "Custom Tab (fallback)"}")

        if (authTabSupported) {
            AuthTabIntent.Builder()
                .setEphemeralBrowsingEnabled(ephemeral)
                .build()
                .launch(launcher, authorizeUrl.toUri(), scheme)
        } else {
            CustomTabsIntent.Builder()
                .setEphemeralBrowsingEnabled(ephemeral)
                .build()
                .launchUrl(this, authorizeUrl.toUri())
        }
    }

    private fun AuthTabIntent.AuthResult.toWebAuthResult(): Result<String, WebAuthError> =
        when (resultCode) {
            AuthTabIntent.RESULT_OK -> {
                val uri = resultUri
                if (uri != null) {
                    Result.Success(uri.toString())
                } else {
                    Result.Failure(WebAuthError.BrowserError("Auth Tab returned no redirect URL."))
                }
            }

            AuthTabIntent.RESULT_CANCELED ->
                Result.Failure(WebAuthError.UserCancelled)

            AuthTabIntent.RESULT_VERIFICATION_FAILED ->
                Result.Failure(WebAuthError.BrowserError("Redirect verification failed."))

            AuthTabIntent.RESULT_VERIFICATION_TIMED_OUT ->
                Result.Failure(WebAuthError.BrowserError("Redirect verification timed out."))

            else ->
                Result.Failure(WebAuthError.BrowserError("Auth Tab returned an unknown result."))
        }

    internal companion object {
        private const val TAG = "Auth0.WebAuth"
        private const val EXTRA_INTENT_LAUNCHED = "com.auth0.kmp.webauth.INTENT_LAUNCHED"
        const val EXTRA_AUTHORIZE_URL: String = "com.auth0.kmp.webauth.AUTHORIZE_URL"
        const val EXTRA_CALLBACK_SCHEME: String = "com.auth0.kmp.webauth.CALLBACK_SCHEME"
        const val EXTRA_EPHEMERAL: String = "com.auth0.kmp.webauth.EPHEMERAL"
    }
}
