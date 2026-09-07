package com.undersigned.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = RbColors.purple,
    onPrimary = RbColors.textPrimary,
    primaryContainer = RbColors.purpleDim,
    onPrimaryContainer = RbColors.textPrimary,
    secondary = RbColors.green,
    onSecondary = RbColors.background,
    background = RbColors.background,
    onBackground = RbColors.textPrimary,
    surface = RbColors.surface,
    onSurface = RbColors.textPrimary,
    surfaceVariant = RbColors.surfaceVariant,
    onSurfaceVariant = RbColors.textSecondary,
    error = RbColors.negative,
)

@Composable
fun UndersignedTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColorScheme, typography = UndersignedTypography, content = content)
}
