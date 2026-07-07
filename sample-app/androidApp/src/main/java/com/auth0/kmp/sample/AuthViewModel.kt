package com.auth0.kmp.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auth0.kmp.authentication.authenticationClient
import com.auth0.kmp.core.Auth0Account
import com.auth0.kmp.core.NetworkingConfiguration
import com.auth0.kmp.core.error.Auth0Error
import com.auth0.kmp.core.model.Credentials
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.webauth.LoginOptions
import com.auth0.kmp.webauth.LogoutOptions
import com.auth0.kmp.webauth.webAuthClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LoginUiState {
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

    private val client = account?.let { authenticationClient(it) }
    private val webClient = account?.let { webAuthClient(it) }

    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    // Tracks how the current session was established, so logout only performs the
    // browser round-trip for Web Auth sessions (embedded login holds no SSO cookie).
    private var loginMethod: LoginMethod? = null

    fun login(email: String, password: String, realm: String) {
        val client = client ?: return
        _state.value = LoginUiState.Loading
        viewModelScope.launch {
            _state.value = when (val result = client.login(email, password, realm)) {
                is Result.Success -> {
                    loginMethod = LoginMethod.Embedded
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
            _state.value = when (val result = webClient.login(LoginOptions())) {
                is Result.Success -> {
                    loginMethod = LoginMethod.WebAuth
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
                        loginMethod = null
                        _state.value = LoginUiState.Idle
                    }
                    // Logout did not complete (cancelled/failed): leave the user on
                    // the Welcome screen since the session was not cleared.
                    is Result.Failure -> Unit
                }
            }
        } else {
            loginMethod = null
            _state.value = LoginUiState.Idle
        }
    }

    override fun onCleared() {
        super.onCleared()
        client?.close()
        webClient?.close()
    }
}
