package com.auth0.kmp.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auth0.kmp.authentication.authenticationClient
import com.auth0.kmp.authentication.error.AuthenticationError
import com.auth0.kmp.core.Auth0Account
import com.auth0.kmp.core.NetworkingConfiguration
import com.auth0.kmp.core.model.Credentials
import com.auth0.kmp.core.result.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class Success(val credentials: Credentials) : LoginUiState
    data class Failure(val error: AuthenticationError) : LoginUiState
}

class AuthViewModel(domain: String, clientId: String) : ViewModel() {

    // Whether the tenant config was supplied. When false we never build the SDK
    // client, so the app can still render its screens instead of crashing on a
    // blank domain (the SDK validates and throws eagerly, by design).
    val isConfigured: Boolean = domain.isNotBlank() && clientId.isNotBlank()

    private val client = if (isConfigured) {
        authenticationClient(
            Auth0Account(
                clientId = clientId,
                domain = domain,
                configuration = NetworkingConfiguration(enableLogging = true),
            ),
        )
    } else {
        null
    }

    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun login(email: String, password: String, realm: String) {
        val client = client ?: return
        _state.value = LoginUiState.Loading
        viewModelScope.launch {
            _state.value = when (val result = client.login(email, password, realm)) {
                is Result.Success -> LoginUiState.Success(result.data)
                is Result.Failure -> LoginUiState.Failure(result.error)
            }
        }
    }

    fun logout() {
        _state.value = LoginUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        client?.close()
    }
}
