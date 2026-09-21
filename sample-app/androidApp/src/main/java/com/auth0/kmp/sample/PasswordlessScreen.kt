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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun PasswordlessScreen(
    state: LoginUiState,
    passwordlessState: PasswordlessUiState,
    isConfigured: Boolean,
    onSendCode: (email: String) -> Unit,
    onVerify: (email: String, code: String) -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }

    val isSending = passwordlessState is PasswordlessUiState.Sending
    val codeSent = passwordlessState is PasswordlessUiState.CodeSent
    val isVerifying = state is LoginUiState.Loading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(top = Spacing.xl, bottom = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Passwordless (email)",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Spacing.md))

        Text(
            text = "We'll email you a one-time code, then you enter it to sign in.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Spacing.xl))

        Column(Modifier.fillMaxWidth()) {
            Text(
                text = "Email",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                singleLine = true,
                enabled = !codeSent,
                shape = RoundedCornerShape(Sizes.cornerLarge),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(Spacing.md))

        Button(
            onClick = { onSendCode(email) },
            enabled = !isSending && !isVerifying && isConfigured && email.isNotBlank(),
            shape = RoundedCornerShape(Sizes.cornerLarge),
            modifier = Modifier
                .fillMaxWidth()
                .height(Sizes.buttonHeight),
        ) {
            if (isSending) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text(if (codeSent) "Resend code" else "Send code")
            }
        }

        if (codeSent) {
            Spacer(Modifier.height(Spacing.lg))

            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = "One-time code",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = Spacing.xs),
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    singleLine = true,
                    shape = RoundedCornerShape(Sizes.cornerLarge),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(Spacing.md))

            OutlinedButton(
                onClick = { onVerify(email, code) },
                enabled = !isVerifying && code.isNotBlank(),
                shape = RoundedCornerShape(Sizes.cornerLarge),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Sizes.buttonHeight),
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text("Verify & log in")
                }
            }
        }

        Spacer(Modifier.height(Spacing.lg))

        // Start-step failures (sending the code) render here; verify-step failures
        // come through the shared LoginUiState. Verify success navigates away.
        if (passwordlessState is PasswordlessUiState.Failure) {
            Text(
                text = passwordlessState.error.toString(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (codeSent) {
            Text(
                text = "Code sent — check your inbox.",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state is LoginUiState.Failure) {
            Text(
                text = state.error.toString(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
