package com.auth0.kmp.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ChooseSignInScreen(onEmbeddedLogin: () -> Unit, onWebAuthLogin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg)
            .padding(top = Spacing.xl, bottom = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandBadge()

        Spacer(Modifier.height(Spacing.lg))

        Text(
            text = "Choose how to sign in",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Spacing.xl))

        OptionCard(
            title = "Embedded Login",
            description = "Total brand control and low user friction",
            onClick = onEmbeddedLogin,
        )

        Spacer(Modifier.height(Spacing.md))

        OptionCard(
            title = "Web Auth",
            description = "Hosted Universal Login in a secure browser tab",
            onClick = onWebAuthLogin,
        )
    }
}

@Composable
private fun BrandBadge() {
    Box(
        modifier = Modifier
            .size(56.dp)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(Sizes.cornerLarge)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "A0",
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun OptionCard(
    title: String,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(Sizes.cornerLarge),
            )
            .let { if (enabled) it.clickable(onClick = onClick) else it }
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
