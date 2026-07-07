package com.auth0.kmp.webauth.browser

import com.auth0.kmp.core.Auth0Account
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.webauth.error.WebAuthError
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSError
import platform.Foundation.NSURL
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private typealias RedirectResult = Result<String, WebAuthError>

private const val CANCELED_LOGIN_CODE = 1L

private fun nsError(code: Long, userInfo: Map<Any?, *>? = null): NSError =
    NSError.errorWithDomain(domain = "test.domain", code = code, userInfo = userInfo)

class IosBrowserAgentTest {

    private val agent = IosBrowserAgent()


    @Test
    fun mapResult_withCallbackUrl_returnsSuccessWithAbsoluteString() {
        val url = NSURL.URLWithString("myapp://cb?code=abc&state=xyz")
        val result = agent.mapResult(url, null)
        assertEquals(Result.Success("myapp://cb?code=abc&state=xyz"), result)
    }

    @Test
    fun mapResult_canceledLoginWithEmptyUserInfo_returnsUserCancelled() {
        val result = agent.mapResult(null, nsError(CANCELED_LOGIN_CODE))
        assertEquals(Result.Failure(WebAuthError.UserCancelled), result)
    }

    @Test
    fun mapResult_canceledLoginWithNonEmptyUserInfo_returnsBrowserError() {
        val result = agent.mapResult(null, nsError(CANCELED_LOGIN_CODE, mapOf<Any?, Any?>("k" to "v")))
        assertTrue(result is Result.Failure && result.error is WebAuthError.BrowserError)
    }

    @Test
    fun mapResult_otherError_returnsBrowserError() {
        val result = agent.mapResult(null, nsError(code = 2L))
        assertTrue(result is Result.Failure && result.error is WebAuthError.BrowserError)
    }

    @Test
    fun mapResult_noUrlNoError_returnsBrowserError() {
        val result = agent.mapResult(null, null)
        assertEquals(
            Result.Failure(
                WebAuthError.BrowserError("ASWebAuthenticationSession returned no URL and no error"),
            ),
            result,
        )
    }


    @Test
    fun defaultRedirectUri_withExplicitScheme_hasExpectedStructure() {
        val account = Auth0Account(clientId = "cid", domain = "tenant.auth0.com")
        val uri = agent.defaultRedirectUri(account, scheme = "myscheme")
        assertTrue(uri.startsWith("myscheme://tenant.auth0.com/ios/"), "was: $uri")
        assertTrue(uri.endsWith("/callback"), "was: $uri")
    }


    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun resumeOnce_doubleInvocation_deliversFirstOnly_andDoesNotCrash() = runTest {
        val guard = AtomicBoolean(false)
        lateinit var cont: CancellableContinuation<RedirectResult>
        val parked = async(start = CoroutineStart.UNDISPATCHED) {
            suspendCancellableCoroutine<RedirectResult> { cont = it }
        }

        resumeOnce(guard, cont, Result.Success("first"))
        // Second delivery (e.g. OS invokes completion again) must be dropped,
        // not throw "IllegalStateException: Already resumed".
        resumeOnce(guard, cont, Result.Failure(WebAuthError.UserCancelled))

        assertEquals(Result.Success("first"), parked.await())
    }

    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun resumeOnce_afterCancellation_isDropped_andDoesNotCrash() = runTest {
        val guard = AtomicBoolean(false)
        lateinit var cont: CancellableContinuation<RedirectResult>
        val parked = async(start = CoroutineStart.UNDISPATCHED) {
            suspendCancellableCoroutine<RedirectResult> { cont = it }
        }

        parked.cancel()
        // Late completion callback after the coroutine was already cancelled:
        // must not throw.
        resumeOnce(guard, cont, Result.Failure(WebAuthError.UserCancelled))

        assertTrue(parked.isCancelled)
    }
}
