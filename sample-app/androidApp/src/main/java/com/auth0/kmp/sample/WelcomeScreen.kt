package com.auth0.kmp.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Composable
fun WelcomeScreen(
    state: LoginUiState,
    apiCredentialsState: ApiCredentialsUiState,
    defaultApiAudience: String,
    onGetApiCredentials: (audience: String, scope: String) -> Unit,
    onLogout: () -> Unit,
) {
    val credentials = (state as? LoginUiState.Success)?.credentials

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(top = Spacing.xl, bottom = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = "You're logged in",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )

        if (credentials != null) {
            val secondsLeft = (credentials.expiresAt - Clock.System.now()).inWholeSeconds

            TokenField("Access token", credentials.accessToken)
            TokenField("ID token", credentials.idToken)
            CredentialRow("Token type", credentials.tokenType)
            CredentialRow("Expires at", credentials.expiresAt.toString())
            CredentialRow(
                "Expires in",
                if (secondsLeft > 0) "$secondsLeft s" else "expired",
            )
            CredentialRow("Refresh token", credentials.refreshToken ?: "— not granted")
            CredentialRow("Scope", credentials.scope ?: "—")
        }

        Spacer(Modifier.height(Spacing.md))

        ApiCredentialsSection(
            state = apiCredentialsState,
            defaultAudience = defaultApiAudience,
            onGetApiCredentials = onGetApiCredentials,
        )

        Spacer(Modifier.height(Spacing.md))

        Button(
            onClick = onLogout,
            shape = RoundedCornerShape(Sizes.cornerLarge),
            modifier = Modifier
                .fillMaxWidth()
                .height(Sizes.buttonHeight),
        ) {
            Text("Log out")
        }
    }
}

/**
 * Exchanges the stored refresh token for an access token scoped to another API.
 * Takes an audience (pre-filled) and an optional scope, then shows the resulting
 * [com.auth0.kmp.core.model.APICredentials] or the error.
 */
@OptIn(ExperimentalTime::class)
@Composable
private fun ApiCredentialsSection(
    state: ApiCredentialsUiState,
    defaultAudience: String,
    onGetApiCredentials: (audience: String, scope: String) -> Unit,
) {
    var audience by rememberSaveable { mutableStateOf(defaultAudience) }
    var scope by rememberSaveable { mutableStateOf("") }

    Text(
        text = "API credentials",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = audience,
        onValueChange = { audience = it },
        label = { Text("Audience") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = scope,
        onValueChange = { scope = it },
        label = { Text("Scope (optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Button(
        onClick = { onGetApiCredentials(audience, scope) },
        enabled = audience.isNotBlank(),
        shape = RoundedCornerShape(Sizes.cornerLarge),
        modifier = Modifier
            .fillMaxWidth()
            .height(Sizes.buttonHeight),
    ) {
        Text("Get API credentials")
    }

    when (state) {
        ApiCredentialsUiState.Idle -> Unit

        is ApiCredentialsUiState.Success -> {
            val apiCredentials = state.apiCredentials
            val secondsLeft = (apiCredentials.expiresAt - Clock.System.now()).inWholeSeconds

            TokenField("API access token", apiCredentials.accessToken)
            CredentialRow("Token type", apiCredentials.tokenType)
            CredentialRow("Expires at", apiCredentials.expiresAt.toString())
            CredentialRow(
                "Expires in",
                if (secondsLeft > 0) "$secondsLeft s" else "expired",
            )
            CredentialRow("Scope", apiCredentials.scope ?: "—")
        }

        is ApiCredentialsUiState.Failure ->
            CredentialRow("Error", state.error.toString())
    }
}

/** A short single-line claim: bold label above the value. */
@Composable
private fun CredentialRow(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A long token value: shown in full, selectable, in a bordered monospace card. */
@Composable
private fun TokenField(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(Sizes.cornerLarge),
                    )
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(Sizes.cornerLarge),
                    )
                    .padding(Spacing.md),
            )
        }
    }
}
