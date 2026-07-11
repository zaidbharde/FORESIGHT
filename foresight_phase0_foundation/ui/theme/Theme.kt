package com.example.foresight.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Single source of truth for the app's visual identity.
 * Same color values as before (no visual regression), just centralized so every
 * screen can pull from MaterialTheme.colorScheme / ExtendedTheme.colors instead of
 * hardcoding its own palette.
 */
private val DarkColorScheme = darkColorScheme(
    primary = AccentPurple,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = AccentMint,
    onSecondary = Color00251F,
    background = DarkBackground,
    onBackground = androidx.compose.ui.graphics.Color.White,
    surface = DarkSurface,
    onSurface = androidx.compose.ui.graphics.Color.White,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = ErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryTeal,
    secondary = SecondaryIndigo,
    tertiary = TertiaryAmber,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    error = Color(0xFFD64545)
)

// Small helper so this file doesn't need an extra import line above
private val Color00251F = androidx.compose.ui.graphics.Color(0xFF00251F)
private fun Color(value: Long) = androidx.compose.ui.graphics.Color(value)

object ExtendedTheme {
    val colors: ForesightExtendedColors
        @Composable get() = LocalForesightExtendedColors.current
}

@Composable
fun ForesightTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalForesightExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ForesightTypography,
            shapes = ForesightShapes,
            content = content
        )
    }
}
