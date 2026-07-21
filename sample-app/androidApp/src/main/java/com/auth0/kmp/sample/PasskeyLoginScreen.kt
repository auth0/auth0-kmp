package com.auth0.kmp.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.auth0.kmp.authentication.model.AuthParamsPublicKey
import com.auth0.kmp.authentication.model.PublicKeyCredentials

@Composable
fun PasskeyLoginScreen(
    state: LoginUiState,
    isConfigured: Boolean,
    onSignIn: (
        connection: String,
        runCeremony: suspend (AuthParamsPublicKey) -> PublicKeyCredentials,
    ) -> Unit,
) {
    val context = LocalContext.current
    var connection by rememberSaveable { mutableStateOf("Username-Password-Authentication") }

    val isLoading = state is LoginUiState.Loading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(top = Spacing.xl, bottom = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Sign in with a passkey",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Spacing.xl))

        Column(Modifier.fillMaxWidth()) {
            Text(
                text = "Connection (database)",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
            OutlinedTextField(
                value = connection,
                onValueChange = { connection = it },
                singleLine = true,
                shape = RoundedCornerShape(Sizes.cornerLarge),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(Spacing.lg))

        Button(
            onClick = {
                onSignIn(connection) { options ->
                    PasskeyCeremony.authenticate(context, options)
                }
            },
            enabled = !isLoading && isConfigured,
            shape = RoundedCornerShape(Sizes.cornerLarge),
            modifier = Modifier
                .fillMaxWidth()
                .height(Sizes.buttonHeight),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text("Sign in with passkey")
            }
        }

        Spacer(Modifier.height(Spacing.lg))

        // Success navigates away to the Welcome screen, so only failures render.
        if (state is LoginUiState.Failure) {
            Text(
                text = state.error.toString(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
