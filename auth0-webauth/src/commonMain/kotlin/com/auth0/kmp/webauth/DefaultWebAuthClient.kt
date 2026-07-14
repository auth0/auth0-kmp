package com.auth0.kmp.webauth

import com.auth0.kmp.core.Auth0Account
import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.dpop.DPoPProofGenerator
import com.auth0.kmp.core.token.TokenClient
import com.auth0.kmp.core.model.Credentials
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.core.validation.IdTokenValidationContext
import com.auth0.kmp.core.validation.IdTokenValidator
import com.auth0.kmp.networking.NetworkClient
import com.auth0.kmp.webauth.authorize.buildAuthorizeUrl
import com.auth0.kmp.webauth.authorize.buildLogoutUrl
import com.auth0.kmp.webauth.browser.BrowserAgent
import com.auth0.kmp.webauth.error.WebAuthError
import com.auth0.kmp.webauth.error.toWebAuthError
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.auth0.kmp.webauth.internal.randomUrlSafeString
import com.auth0.kmp.webauth.pkce.Pkce
import com.auth0.kmp.webauth.request.CodeExchangeGrant
import com.auth0.kmp.webauth.transaction.AuthorizeTransaction
import com.auth0.kmp.webauth.transaction.TransactionStore
import com.auth0.kmp.webauth.validation.IdTokenSignatureValidator
import io.ktor.http.Url

@OptIn(InternalAuth0Api::class)
internal class DefaultWebAuthClient(
    private val account: Auth0Account,
    private val browser: BrowserAgent,
    private val store: TransactionStore,
    private val tokenClient: TokenClient,
    private val networkClient: NetworkClient,
    private val signatureValidator: IdTokenSignatureValidator,
    private val claimsValidator: IdTokenValidator,
    private val proofGenerator: DPoPProofGenerator? = null,
    private val keygenLock: Mutex? = null,
) : WebAuthClient {

    override suspend fun login(options: LoginOptions): Result<Credentials, WebAuthError> {
        if (store.hasActiveTransaction()) {
            return Result.Failure(WebAuthError.TransactionActiveAlready)
        }

        val redirectUri = options.redirectUri ?: browser.defaultRedirectUri(account, options.scheme)
        val callbackScheme = redirectUri.substringBefore("://")
        val transaction = AuthorizeTransaction(
            state = randomUrlSafeString(),
            nonce = randomUrlSafeString(),
            pkce = Pkce.generate(),
            redirectUri = redirectUri,
        )
        store.save(transaction)

        val dpopJkt: String? = if (proofGenerator != null && keygenLock != null) {
            when (val jkt = keygenLock.withLock { proofGenerator.jkt() }) {
                is Result.Success -> jkt.data
                is Result.Failure -> {
                    store.clear(transaction.state)
                    return Result.Failure(jkt.error.toWebAuthError())
                }
            }
        } else {
            null
        }

        val authorizeUrl = buildAuthorizeUrl(account, transaction, options, dpopJkt)

        return when (val launch = browser.launch(authorizeUrl, callbackScheme, options.ephemeral)) {
            is Result.Failure -> {
                store.clear(transaction.state)
                launch
            }
            is Result.Success -> {
                store.clear(transaction.state)
                handleRedirect(launch.data, transaction, options)
            }
        }
    }

    override suspend fun logout(options: LogoutOptions): Result<Unit, WebAuthError> {
        val returnTo = options.returnTo ?: browser.defaultRedirectUri(account, options.scheme)
        val callbackScheme = returnTo.substringBefore("://")
        val logoutUrl = buildLogoutUrl(account, returnTo, options)

        return when (val launch = browser.launch(logoutUrl, callbackScheme, ephemeral = false)) {
            is Result.Failure -> launch
            is Result.Success -> Result.Success(Unit)
        }
    }

    private suspend fun handleRedirect(
        redirectUrl: String,
        transaction: AuthorizeTransaction,
        options: LoginOptions,
    ): Result<Credentials, WebAuthError> {
        val params = Url(redirectUrl).parameters

        params["error"]?.let { code ->
            return Result.Failure(
                WebAuthError.AuthorizationError(code, params["error_description"] ?: code),
            )
        }

        if (params["state"] != transaction.state) {
            return Result.Failure(WebAuthError.InvalidState)
        }

        val code = params["code"]
            ?: return Result.Failure(WebAuthError.BrowserError("Redirect did not contain an authorization code"))

        val grant = CodeExchangeGrant(
            code = code,
            codeVerifier = transaction.pkce.codeVerifier,
            redirectUri = transaction.redirectUri,
            clientId = account.clientId,
        )

        return when (val result = tokenClient.fetchToken(grant)) {
            is Result.Failure -> Result.Failure(result.error.toWebAuthError())
            is Result.Success -> validate(result.data, transaction, options)
        }
    }

    private suspend fun validate(
        credentials: Credentials,
        transaction: AuthorizeTransaction,
        options: LoginOptions,
    ): Result<Credentials, WebAuthError> {
        signatureValidator.verify(credentials.idToken)?.let {
            return Result.Failure(WebAuthError.IdTokenValidation(it))
        }
        val context = IdTokenValidationContext(
            nonce = transaction.nonce,
            maxAge = options.maxAge,
            organization = options.organization,
        )
        claimsValidator.validate(credentials.idToken, context)?.let {
            return Result.Failure(WebAuthError.IdTokenValidation(it))
        }
        return Result.Success(credentials)
    }

    override fun cancel() {
        store.current()?.let { store.clear(it.state) }
    }

    override fun close() {
        networkClient.close()
    }
}
