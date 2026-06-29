package com.auth0.kmp.sample

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Lightweight design tokens modeled on Auth0's ui-components-android reference.
// A single login screen does not need the full token-system machinery, so we
// keep the brand look (palette, spacing, corners, sizes) in one place.

object Spacing {
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

object Sizes {
    val buttonHeight = 56.dp
    val inputHeight = 68.dp
    val cornerLarge = 16.dp
}

private val Brand = Color(0xFF09090B)        // reference primary (near-black)
private val BrandOnDark = Color(0xFFFAFAFA)

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color(0xFFF0F0F0),
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F1F1F),
    outline = Color(0xFFD9D9D9),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = BrandOnDark,
    onPrimary = Color(0xFF18181B),
    background = Color(0xFF09090B),
    surface = Color(0xFF27272A),
    onSurface = Color(0xFFFAFAFA),
    outline = Color(0xFF3F3F46),
    error = Color(0xFFF2B8B5),
)

private val SampleShapes = Shapes(
    large = RoundedCornerShape(Sizes.cornerLarge),
)

@Composable
fun SampleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = SampleShapes,
        content = content,
    )
}
