package com.auth0.kmp.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {

    private val viewModel: AuthViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AuthViewModel(
                    domain = BuildConfig.AUTH0_DOMAIN,
                    clientId = BuildConfig.AUTH0_CLIENT_ID,
                ) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SampleTheme {
                Surface {
                    val navController = rememberNavController()
                    val state by viewModel.state.collectAsState()

                    LaunchedEffect(Unit) {
                        viewModel.restoreSession()
                    }

                    LaunchedEffect(state) {
                        when (state) {
                            is LoginUiState.Success ->
                                navController.navigate(Welcome) {
                                    popUpTo(navController.graph.id) { inclusive = true }
                                    launchSingleTop = true
                                }
                            LoginUiState.Idle ->
                                navController.navigate(Chooser) {
                                    popUpTo(navController.graph.id) { inclusive = true }
                                    launchSingleTop = true
                                }
                            else -> Unit
                        }
                    }

                    NavHost(navController = navController, startDestination = Splash) {
                        composable<Splash> {
                            SplashScreen()
                        }
                        composable<Chooser> {
                            ChooseSignInScreen(
                                onEmbeddedLogin = { navController.navigate(EmbeddedLogin) },
                                onWebAuthLogin = viewModel::webLogin,
                            )
                        }
                        composable<EmbeddedLogin> {
                            EmbeddedLoginScreen(
                                state = state,
                                isConfigured = viewModel.isConfigured,
                                onLogin = viewModel::login,
                            )
                        }
                        composable<Welcome> {
                            WelcomeScreen(state = state, onLogout = viewModel::logout)
                        }
                    }
                }
            }
        }
    }
}
