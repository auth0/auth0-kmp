package com.auth0.kmp.sample

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun EmbeddedMethodsScreen(
    onPasswordLogin: () -> Unit,
    onSignup: () -> Unit,
    onPasskeySignup: () -> Unit,
    onPasskeyLogin: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg)
            .padding(top = Spacing.xl, bottom = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Embedded login",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Spacing.xl))

        MethodCard(
            title = "Password login",
            description = "Sign in with email and password",
            onClick = onPasswordLogin,
        )

        Spacer(Modifier.height(Spacing.md))

        MethodCard(
            title = "Sign up",
            description = "Create a new database user",
            onClick = onSignup,
        )

        Spacer(Modifier.height(Spacing.md))

        MethodCard(
            title = "Passkey sign up",
            description = "Register a passkey on this device",
            onClick = onPasskeySignup,
        )

        Spacer(Modifier.height(Spacing.md))

        MethodCard(
            title = "Passkey login",
            description = "Sign in with an existing passkey",
            onClick = onPasskeyLogin,
        )
    }
}

@Composable
private fun MethodCard(title: String, description: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(Sizes.cornerLarge),
            )
            .clickable(onClick = onClick)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
