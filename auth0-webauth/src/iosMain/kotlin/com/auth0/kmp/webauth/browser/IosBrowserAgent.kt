package com.auth0.kmp.webauth.browser

import com.auth0.kmp.core.Auth0Account
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.webauth.error.WebAuthError
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.AuthenticationServices.ASWebAuthenticationSessionCallback
import platform.AuthenticationServices.ASWebAuthenticationSessionErrorCodeCanceledLogin
import platform.Foundation.NSBundle
import platform.Foundation.NSError
import platform.Foundation.NSOperatingSystemVersion
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class, ExperimentalForeignApi::class)
internal class IosBrowserAgent : BrowserAgent {

    private var activeSession: ASWebAuthenticationSession? = null


    override fun defaultRedirectUri(
        account: Auth0Account,
        scheme: String?
    ): String {
        val bundleId = NSBundle.mainBundle.bundleIdentifier ?: ""
        val effectiveScheme = scheme ?: bundleId
        return "$effectiveScheme://${account.domain}/ios/$bundleId/callback"
    }

    override suspend fun launch(
        url: String,
        callbackScheme: String,
        ephemeral: Boolean
    ): Result<String, WebAuthError> = suspendCancellableCoroutine { continuation ->
        val resumed = AtomicBoolean(false)

        val nsUrl = NSURL.URLWithString(url) ?: run {
            continuation.resume(Result.Failure(WebAuthError.BrowserError("Invalid URL: $url")))
            return@suspendCancellableCoroutine
        }

        val completion: (NSURL?, NSError?) -> Unit = { callbackUrl, error ->
            resumeOnce(resumed, continuation, mapResult(callbackUrl, error))
            activeSession = null
        }

        val session = if (isAtLeastIos17_4()) {
            ASWebAuthenticationSession(
                nsUrl, ASWebAuthenticationSessionCallback.callbackWithCustomScheme(callbackScheme),
                completion
            )
        } else {
            ASWebAuthenticationSession(nsUrl, callbackScheme, completion)
        }

        session.prefersEphemeralWebBrowserSession = ephemeral
        session.presentationContextProvider = PresentationContextProvider()
        this.activeSession = session

        continuation.invokeOnCancellation {
            session.cancel()
            activeSession = null
        }
        dispatch_async(dispatch_get_main_queue()) {
            session.start()
        }
    }


    private fun isAtLeastIos17_4(): Boolean {
        val target = cValue<NSOperatingSystemVersion>() {
            majorVersion = 17; minorVersion = 4; patchVersion = 0
        }
        return NSProcessInfo.processInfo.isOperatingSystemAtLeastVersion(target)
    }

    internal fun mapResult(callbackUrl: NSURL?, error: NSError?): Result<String, WebAuthError> {
        if (callbackUrl != null) {
            val absolute = callbackUrl.absoluteString
                ?: return Result.Failure(WebAuthError.BrowserError("Callback URL had no absolute string"))
            return Result.Success(absolute)
        }

        if (error != null && error.code == ASWebAuthenticationSessionErrorCodeCanceledLogin && error.userInfo.isEmpty())
            return Result.Failure(WebAuthError.UserCancelled)

        if (error != null)
            return Result.Failure(WebAuthError.BrowserError(error.localizedDescription))

        return Result.Failure(WebAuthError.BrowserError("ASWebAuthenticationSession returned no URL and no error"))
    }
}

/**
 * Resumes [continuation] with [result] only on the first call for a given
 * [guard], so that a completion handler invoked more than once (or after the
 * coroutine was already cancelled) never resumes an already-resumed continuation.
 */
@OptIn(ExperimentalAtomicApi::class)
internal fun resumeOnce(
    guard: AtomicBoolean,
    continuation: CancellableContinuation<Result<String, WebAuthError>>,
    result: Result<String, WebAuthError>,
) {
    if (guard.compareAndSet(false, true)) {
        continuation.resume(result)
    }
}

private class PresentationContextProvider :
    NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(
        session: ASWebAuthenticationSession,
    ): ASPresentationAnchor =
        UIApplication.sharedApplication.keyWindow ?: ASPresentationAnchor()
}