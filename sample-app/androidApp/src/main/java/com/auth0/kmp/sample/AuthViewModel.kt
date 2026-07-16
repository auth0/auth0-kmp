package com.auth0.kmp.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auth0.kmp.authentication.authenticationClient
import com.auth0.kmp.core.Auth0Account
import com.auth0.kmp.core.NetworkingConfiguration
import com.auth0.kmp.core.RequestOptions
import com.auth0.kmp.core.error.Auth0Error
import com.auth0.kmp.core.model.Credentials
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.credentials.credentialsManager
import com.auth0.kmp.webauth.LoginOptions
import com.auth0.kmp.webauth.LogoutOptions
import com.auth0.kmp.webauth.webAuthClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LoginUiState {
    // Initial state while the app checks storage for a saved session; the Splash
    // screen is shown until this resolves to Success (restored) or Idle (none).
    data object Restoring : LoginUiState
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class Success(val credentials: Credentials) : LoginUiState
    data class Failure(val error: Auth0Error) : LoginUiState
}

private enum class LoginMethod { Embedded, WebAuth }

class AuthViewModel(domain: String, clientId: String) : ViewModel() {

    // Whether the tenant config was supplied. When false we never build the SDK
    // client, so the app can still render its screens instead of crashing on a
    // blank domain (the SDK validates and throws eagerly, by design).
    val isConfigured: Boolean = domain.isNotBlank() && clientId.isNotBlank()

    private val account = if (isConfigured) {
        Auth0Account(
            clientId = clientId,
            domain = domain,
            configuration = NetworkingConfiguration(enableLogging = true),
        )
    } else {
        null
    }

    private val audience = "https://firstresourceserver/"
    private val client = account?.let { authenticationClient(it) }
    private val webClient = account?.let { webAuthClient(it) }
    private val credentialsManager = account?.let { credentialsManager(it) }

    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Restoring)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    // Tracks how the current session was established, so logout only performs the
    // browser round-trip for Web Auth sessions (embedded login holds no SSO cookie).
    private var loginMethod: LoginMethod? = null

    // Checks storage for a saved session on launch. The chooser is shown right
    // away so cold start never blocks on a token round-trip; the restore then
    // runs in the background. A fresh token promotes to the logged-in screen via
    // a local decrypt (no network); an expired-but-refreshable token is renewed
    // off the launch path and then promotes. Anything unrestorable (no
    // credentials, or an expired token with no refresh token) simply stays on
    // the chooser — that is not an error to surface, the user just signs in.
    fun restoreSession() {
        val credentialsManager = credentialsManager
        if (credentialsManager == null) {
            _state.value = LoginUiState.Idle
            return
        }
        _state.value = LoginUiState.Idle
        viewModelScope.launch {
            val result = credentialsManager.getCredentials()
            if (result is Result.Success) {
                _state.value = LoginUiState.Success(result.data)
            }
        }
    }

    fun login(
        email: String,
        password: String,
        realm: String,
        options: RequestOptions = RequestOptions(),
    ) {
        val client = client ?: return
        _state.value = LoginUiState.Loading
        viewModelScope.launch {
            // Request offline_access so the tenant issues a refresh token, letting
            // getCredentials() renew an expired access token on a later launch.
            val result = client.login(
                usernameOrEmail = email,
                password = password,
                realm = realm,
                audience = audience,
                scope = "openid profile email offline_access",
                options = options,
            )
            _state.value = when (result) {
                is Result.Success -> {
                    loginMethod = LoginMethod.Embedded
                    credentialsManager?.saveCredentials(result.data)
                    LoginUiState.Success(result.data)
                }

                is Result.Failure -> LoginUiState.Failure(result.error)
            }
        }
    }

    fun webLogin() {
        val webClient = webClient ?: return
        _state.value = LoginUiState.Loading
        viewModelScope.launch {
            _state.value = when (val result = webClient.login(LoginOptions(audience = audience))) {
                is Result.Success -> {
                    loginMethod = LoginMethod.WebAuth
                    credentialsManager?.saveCredentials(result.data)
                    LoginUiState.Success(result.data)
                }

                is Result.Failure -> LoginUiState.Failure(result.error)
            }
        }
    }

    fun logout() {
        val webClient = webClient
        if (loginMethod == LoginMethod.WebAuth && webClient != null) {
            // Keep the current Success state (credentials stay on screen) during the
            // browser round-trip; only clear the view once logout actually succeeds.
            viewModelScope.launch {
                when (webClient.logout(LogoutOptions())) {
                    is Result.Success -> {
                        credentialsManager?.clearCredentials()
                        loginMethod = null
                        _state.value = LoginUiState.Idle
                    }
                    // Logout did not complete (cancelled/failed): leave the user on
                    // the Welcome screen since the session was not cleared.
                    is Result.Failure -> Unit
                }
            }
        } else {
            // Embedded (or restored) session: no SSO cookie to clear in the browser,
            // so just drop the stored credentials locally.
            loginMethod = null
            viewModelScope.launch {
                credentialsManager?.clearCredentials()
                _state.value = LoginUiState.Idle
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        client?.close()
        webClient?.close()
    }
}
