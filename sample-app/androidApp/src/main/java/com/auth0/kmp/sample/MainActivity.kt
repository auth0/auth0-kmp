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
                    val signupState by viewModel.signupState.collectAsState()

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

                    // A successful createUser lands on the confirmation screen (it
                    // mints no tokens, so login state is untouched).
                    LaunchedEffect(signupState) {
                        if (signupState is SignupUiState.Success) {
                            navController.navigate(SignupResult) { launchSingleTop = true }
                        }
                    }

                    NavHost(navController = navController, startDestination = Splash) {
                        composable<Splash> {
                            SplashScreen()
                        }
                        composable<Chooser> {
                            ChooseSignInScreen(
                                state = state,
                                onEmbeddedLogin = { navController.navigate(EmbeddedMethods) },
                                onWebAuthLogin = viewModel::webLogin,
                            )
                        }
                        composable<EmbeddedMethods> {
                            EmbeddedMethodsScreen(
                                onPasswordLogin = { navController.navigate(EmbeddedLogin) },
                                onSignup = {
                                    viewModel.resetSignup()
                                    navController.navigate(Signup)
                                },
                                onPasskeySignup = { navController.navigate(PasskeySignup) },
                                onPasskeyLogin = { navController.navigate(PasskeyLogin) },
                            )
                        }
                        composable<EmbeddedLogin> {
                            EmbeddedLoginScreen(
                                state = state,
                                isConfigured = viewModel.isConfigured,
                                onLogin = viewModel::login,
                            )
                        }
                        composable<Signup> {
                            SignupScreen(
                                state = signupState,
                                isConfigured = viewModel.isConfigured,
                                onSignup = viewModel::createUser,
                            )
                        }
                        composable<SignupResult> {
                            val user = (signupState as? SignupUiState.Success)?.user
                            if (user != null) {
                                SignupResultScreen(
                                    user = user,
                                    onLogIn = viewModel::completeSignupLogin,
                                )
                            }
                        }
                        composable<PasskeySignup> {
                            PasskeySignupScreen(
                                state = state,
                                isConfigured = viewModel.isConfigured,
                                onRegister = viewModel::passkeySignup,
                            )
                        }
                        composable<PasskeyLogin> {
                            PasskeyLoginScreen(
                                state = state,
                                isConfigured = viewModel.isConfigured,
                                onSignIn = viewModel::passkeyLogin,
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
