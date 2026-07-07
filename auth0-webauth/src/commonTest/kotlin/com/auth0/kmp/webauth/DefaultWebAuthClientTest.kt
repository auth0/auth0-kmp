package com.auth0.kmp.webauth

import com.auth0.kmp.core.Auth0Account
import com.auth0.kmp.core.error.TransportError
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.core.validation.IdTokenValidationContext
import com.auth0.kmp.core.validation.IdTokenValidationError
import com.auth0.kmp.core.validation.IdTokenValidator
import com.auth0.kmp.networking.NetworkClient
import com.auth0.kmp.networking.request.NetworkRequest
import com.auth0.kmp.networking.retry.RetryPolicy
import com.auth0.kmp.webauth.browser.BrowserAgent
import com.auth0.kmp.webauth.error.WebAuthError
import com.auth0.kmp.webauth.pkce.Pkce
import com.auth0.kmp.webauth.transaction.AuthorizeTransaction
import com.auth0.kmp.webauth.transaction.InMemoryTransactionStore
import com.auth0.kmp.webauth.transaction.TransactionStore
import com.auth0.kmp.webauth.validation.IdTokenSignatureValidator
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

private const val ID_TOKEN = "header.payload.signature"
private const val REDIRECT_URI = "myapp://test.auth0.com/android/com.example/callback"
private const val RETURN_TO = "myapp://test.auth0.com/android/com.example/callback"

private fun tokenJson(idToken: String = ID_TOKEN): String =
    """{"access_token":"access-abc","id_token":"$idToken","token_type":"Bearer","expires_in":3600,"refresh_token":"refresh-xyz","scope":"openid profile email offline_access"}"""

/** Builds a success redirect that echoes the transaction's state (parsed from the authorize URL). */
private fun redirectSuccess(code: String = "the-code"): (String) -> Result<String, WebAuthError> =
    { state -> Result.Success("$REDIRECT_URI?code=$code&state=$state") }

private class FakeBrowserAgent(
    private val defaultRedirect: String = REDIRECT_URI,
    private val onLaunch: (state: String) -> Result<String, WebAuthError> = redirectSuccess(),
) : BrowserAgent {
    var launchedUrl: String? = null
    var launchedScheme: String? = null
    var launchedEphemeral: Boolean? = null
    var defaultRedirectAccount: Auth0Account? = null
    var defaultRedirectScheme: String? = null

    override fun defaultRedirectUri(account: Auth0Account, scheme: String?): String {
        defaultRedirectAccount = account
        defaultRedirectScheme = scheme
        return defaultRedirect
    }

    override suspend fun launch(
        url: String,
        callbackScheme: String,
        ephemeral: Boolean,
    ): Result<String, WebAuthError> {
        launchedUrl = url
        launchedScheme = callbackScheme
        launchedEphemeral = ephemeral
        return onLaunch(Url(url).parameters["state"] ?: "")
    }
}

private class FakeNetworkClient(
    private val outcome: Result<String, TransportError>,
) : NetworkClient {
    var lastRequest: NetworkRequest? = null
    var closed = false
        private set

    override suspend fun <T> request(
        request: NetworkRequest,
        retryPolicy: RetryPolicy,
        deserialize: (String) -> T,
    ): Result<T, TransportError> {
        lastRequest = request
        return when (outcome) {
            is Result.Success -> Result.Success(deserialize(outcome.data))
            is Result.Failure -> outcome
        }
    }

    override fun close() {
        closed = true
    }
}

private class FakeSignatureValidator(
    private val verdict: IdTokenValidationError?,
) : IdTokenSignatureValidator {
    var lastIdToken: String? = null
    override suspend fun verify(idToken: String): IdTokenValidationError? {
        lastIdToken = idToken
        return verdict
    }
}

private class FakeClaimsValidator(
    private val verdict: IdTokenValidationError?,
) : IdTokenValidator {
    var lastIdToken: String? = null
    var lastContext: IdTokenValidationContext? = null
    override fun validate(idToken: String, context: IdTokenValidationContext): IdTokenValidationError? {
        lastIdToken = idToken
        lastContext = context
        return verdict
    }
}

private class FixedClock(private val at: Instant) : Clock {
    override fun now(): Instant = at
}

private class Fixture(
    val client: DefaultWebAuthClient,
    val browser: FakeBrowserAgent,
    val network: FakeNetworkClient,
    val signature: FakeSignatureValidator,
    val claims: FakeClaimsValidator,
    val store: TransactionStore,
)

private fun fixture(
    browser: FakeBrowserAgent = FakeBrowserAgent(),
    networkOutcome: Result<String, TransportError> = Result.Success(tokenJson()),
    signatureVerdict: IdTokenValidationError? = null,
    claimsVerdict: IdTokenValidationError? = null,
    store: TransactionStore = InMemoryTransactionStore(),
): Fixture {
    val network = FakeNetworkClient(networkOutcome)
    val signature = FakeSignatureValidator(signatureVerdict)
    val claims = FakeClaimsValidator(claimsVerdict)
    val client = DefaultWebAuthClient(
        account = Auth0Account(clientId = "cid", domain = "test.auth0.com"),
        browser = browser,
        store = store,
        networkClient = network,
        signatureValidator = signature,
        claimsValidator = claims,
        clock = FixedClock(Instant.fromEpochSeconds(1_000)),
    )
    return Fixture(client, browser, network, signature, claims, store)
}

class DefaultWebAuthClientTest {

    @Test
    fun success_returnsCredentials() = runTest {
        val f = fixture()
        val result = f.client.login()
        assertTrue(result is Result.Success)
        assertEquals("access-abc", result.data.accessToken)
        assertEquals(ID_TOKEN, result.data.idToken)
    }

    @Test
    fun success_buildsAuthorizeUrlWithRedirectPkceStateNonce() = runTest {
        val f = fixture()
        f.client.login()
        val url = f.browser.launchedUrl!!
        assertTrue(url.contains("client_id=cid"), url)
        assertTrue(url.contains("code_challenge="), url)
        assertTrue(url.contains("code_challenge_method=S256"), url)
        assertTrue(url.contains("state="), url)
        assertTrue(url.contains("nonce="), url)
        assertTrue(url.contains("redirect_uri="), url)
    }

    @Test
    fun success_usesOptionsRedirectUriOverride() = runTest {
        val f = fixture()
        f.client.login(LoginOptions(redirectUri = "https://custom.example/cb"))
        assertTrue(f.browser.launchedUrl!!.contains("redirect_uri=https%3A%2F%2Fcustom.example%2Fcb"))
        assertNull(f.browser.defaultRedirectAccount) // platform default not consulted
        assertEquals("https", f.browser.launchedScheme)
    }

    @Test
    fun success_usesDefaultRedirectUriWhenNoOverride_passesSchemeToPlatform() = runTest {
        val f = fixture()
        f.client.login(LoginOptions(scheme = "myapp"))
        assertEquals("myapp", f.browser.defaultRedirectScheme)
        assertEquals("cid", f.browser.defaultRedirectAccount!!.clientId)
    }

    @Test
    fun success_passesDerivedCallbackSchemeToLaunch() = runTest {
        val f = fixture()
        f.client.login()
        assertEquals("myapp", f.browser.launchedScheme) // scheme of REDIRECT_URI
    }

    @Test
    fun success_passesEphemeralToLaunch() = runTest {
        val f = fixture()
        f.client.login(LoginOptions(ephemeral = true))
        assertEquals(true, f.browser.launchedEphemeral)
    }

    @Test
    fun login_whileActiveTransaction_failsTransactionActiveAlready() = runTest {
        val store = InMemoryTransactionStore()
        store.save(AuthorizeTransaction("s", "n", Pkce.generate(), REDIRECT_URI))
        val f = fixture(store = store)
        val result = f.client.login()
        assertEquals(Result.Failure(WebAuthError.TransactionActiveAlready), result)
        assertNull(f.browser.launchedUrl) // browser never launched
    }

    @Test
    fun browserCancelled_failsUserCancelled() = runTest {
        val f = fixture(browser = FakeBrowserAgent(onLaunch = { Result.Failure(WebAuthError.UserCancelled) }))
        assertEquals(Result.Failure(WebAuthError.UserCancelled), f.client.login())
    }

    @Test
    fun browserError_mapsToBrowserError() = runTest {
        val f = fixture(browser = FakeBrowserAgent(onLaunch = { Result.Failure(WebAuthError.BrowserError("boom")) }))
        assertEquals(Result.Failure(WebAuthError.BrowserError("boom")), f.client.login())
    }

    @Test
    fun redirectCarriesError_failsAuthorizationError() = runTest {
        val f = fixture(
            browser = FakeBrowserAgent(onLaunch = {
                Result.Success("$REDIRECT_URI?error=access_denied&error_description=denied")
            }),
        )
        val result = f.client.login()
        assertEquals(
            Result.Failure(WebAuthError.AuthorizationError("access_denied", "denied")),
            result,
        )
    }

    @Test
    fun stateMismatch_failsInvalidState() = runTest {
        val f = fixture(
            browser = FakeBrowserAgent(onLaunch = { Result.Success("$REDIRECT_URI?code=c&state=wrong") }),
        )
        assertEquals(Result.Failure(WebAuthError.InvalidState), f.client.login())
    }

    @Test
    fun redirectMissingCode_failsBrowserError() = runTest {
        val f = fixture(
            browser = FakeBrowserAgent(onLaunch = { state -> Result.Success("$REDIRECT_URI?state=$state") }),
        )
        val result = f.client.login()
        assertTrue(result is Result.Failure && result.error is WebAuthError.BrowserError)
    }

    @Test
    fun codeExchangeNetworkFailure_mapsToWebAuthErrorNetwork() = runTest {
        val f = fixture(networkOutcome = Result.Failure(TransportError.NoInternet))
        assertEquals(Result.Failure(WebAuthError.Network(TransportError.NoInternet)), f.client.login())
    }

    @Test
    fun codeExchangeServerError_mapsToApiError() = runTest {
        val server = TransportError.Server(403, """{"error":"invalid_grant","error_description":"bad"}""")
        val f = fixture(networkOutcome = Result.Failure(server))
        assertEquals(
            Result.Failure(WebAuthError.ApiError("invalid_grant", "bad", 403)),
            f.client.login(),
        )
    }

    @Test
    fun signatureInvalid_failsIdTokenValidation() = runTest {
        val f = fixture(signatureVerdict = IdTokenValidationError.InvalidSignature)
        assertEquals(
            Result.Failure(WebAuthError.IdTokenValidation(IdTokenValidationError.InvalidSignature)),
            f.client.login(),
        )
    }

    @Test
    fun claimsInvalid_failsIdTokenValidation() = runTest {
        val f = fixture(claimsVerdict = IdTokenValidationError.InvalidNonce)
        assertEquals(
            Result.Failure(WebAuthError.IdTokenValidation(IdTokenValidationError.InvalidNonce)),
            f.client.login(),
        )
    }

    @Test
    fun claimsValidator_receivesNonceFromTransaction_maxAgeAndOrgFromOptions() = runTest {
        val f = fixture()
        f.client.login(LoginOptions(maxAge = 120, organization = "org_1"))
        val urlNonce = Url(f.browser.launchedUrl!!).parameters["nonce"]
        assertEquals(urlNonce, f.claims.lastContext!!.nonce)
        assertEquals(120, f.claims.lastContext!!.maxAge)
        assertEquals("org_1", f.claims.lastContext!!.organization)
    }

    @Test
    fun transactionCleared_afterSuccess() = runTest {
        val f = fixture()
        f.client.login()
        assertFalse(f.store.hasActiveTransaction())
    }

    @Test
    fun transactionCleared_afterFailure() = runTest {
        val f = fixture(signatureVerdict = IdTokenValidationError.InvalidSignature)
        f.client.login()
        assertFalse(f.store.hasActiveTransaction())
    }

    @Test
    fun close_closesNetworkClient() {
        val f = fixture()
        f.client.close()
        assertTrue(f.network.closed)
    }

    @Test
    fun logout_success_returnsUnit() = runTest {
        val f = fixture(browser = FakeBrowserAgent(onLaunch = { Result.Success(RETURN_TO) }))
        assertEquals(Result.Success(Unit), f.client.logout())
    }

    @Test
    fun logout_buildsLogoutUrlWithClientIdAndReturnTo() = runTest {
        val f = fixture(browser = FakeBrowserAgent(onLaunch = { Result.Success(RETURN_TO) }))
        f.client.logout()
        val url = f.browser.launchedUrl!!
        assertTrue(url.contains("/v2/logout"), url)
        assertTrue(url.contains("client_id=cid"), url)
        assertTrue(url.contains("returnTo="), url)
    }

    @Test
    fun logout_usesReturnToOverride_notPlatformDefault() = runTest {
        val f = fixture(browser = FakeBrowserAgent(onLaunch = { Result.Success(RETURN_TO) }))
        f.client.logout(LogoutOptions(returnTo = "https://custom.example/bye"))
        assertTrue(f.browser.launchedUrl!!.contains("returnTo=https%3A%2F%2Fcustom.example%2Fbye"))
        assertNull(f.browser.defaultRedirectAccount) // platform default not consulted
        assertEquals("https", f.browser.launchedScheme)
    }

    @Test
    fun logout_usesDefaultReturnToWhenNoOverride_passesSchemeToPlatform() = runTest {
        val f = fixture(browser = FakeBrowserAgent(onLaunch = { Result.Success(RETURN_TO) }))
        f.client.logout(LogoutOptions(scheme = "myapp"))
        assertEquals("myapp", f.browser.defaultRedirectScheme)
        assertEquals("cid", f.browser.defaultRedirectAccount!!.clientId)
    }

    @Test
    fun logout_passesDerivedCallbackSchemeToLaunch() = runTest {
        val f = fixture(browser = FakeBrowserAgent(onLaunch = { Result.Success(RETURN_TO) }))
        f.client.logout()
        assertEquals("myapp", f.browser.launchedScheme) // scheme of RETURN_TO
    }

    @Test
    fun logout_launchesNonEphemeral() = runTest {
        val f = fixture(browser = FakeBrowserAgent(onLaunch = { Result.Success(RETURN_TO) }))
        f.client.logout()
        assertEquals(false, f.browser.launchedEphemeral)
    }

    @Test
    fun logout_federatedTrue_appendsFederated() = runTest {
        val f = fixture(browser = FakeBrowserAgent(onLaunch = { Result.Success(RETURN_TO) }))
        f.client.logout(LogoutOptions(federated = true))
        assertTrue(f.browser.launchedUrl!!.contains("federated=1"), f.browser.launchedUrl!!)
    }

    @Test
    fun logout_browserCancelled_failsUserCancelled() = runTest {
        val f = fixture(browser = FakeBrowserAgent(onLaunch = { Result.Failure(WebAuthError.UserCancelled) }))
        assertEquals(Result.Failure(WebAuthError.UserCancelled), f.client.logout())
    }

    @Test
    fun logout_browserError_mapsToBrowserError() = runTest {
        val f = fixture(browser = FakeBrowserAgent(onLaunch = { Result.Failure(WebAuthError.BrowserError("boom")) }))
        assertEquals(Result.Failure(WebAuthError.BrowserError("boom")), f.client.logout())
    }

    @Test
    fun logout_doesNotTouchNetworkClient() = runTest {
        val f = fixture(browser = FakeBrowserAgent(onLaunch = { Result.Success(RETURN_TO) }))
        f.client.logout()
        assertNull(f.network.lastRequest)
    }

    @Test
    fun logout_doesNotConsultTransactionStore() = runTest {
        val store = InMemoryTransactionStore()
        store.save(AuthorizeTransaction("s", "n", Pkce.generate(), REDIRECT_URI))
        val f = fixture(browser = FakeBrowserAgent(onLaunch = { Result.Success(RETURN_TO) }), store = store)
        assertEquals(Result.Success(Unit), f.client.logout())
    }

    @Test
    fun cancel_clearsActiveTransaction() {
        val store = InMemoryTransactionStore()
        store.save(AuthorizeTransaction("s", "n", Pkce.generate(), REDIRECT_URI))
        val f = fixture(store = store)
        f.client.cancel()
        assertFalse(f.store.hasActiveTransaction())
    }

    @Test
    fun cancel_noActiveTransaction_isNoOp() {
        val f = fixture()
        f.client.cancel() // must not throw
        assertFalse(f.store.hasActiveTransaction())
    }

    @Test
    fun signatureFail_shortCircuitsBeforeClaims() = runTest {
        val f = fixture(
            signatureVerdict = IdTokenValidationError.InvalidSignature,
            claimsVerdict = null, // claims would "pass" if reached
        )
        assertEquals(
            Result.Failure(WebAuthError.IdTokenValidation(IdTokenValidationError.InvalidSignature)),
            f.client.login(),
        )
        assertNull(f.claims.lastIdToken)              // proves claims validator never ran
        assertEquals(ID_TOKEN, f.signature.lastIdToken) // proves signature saw the token
    }
}
