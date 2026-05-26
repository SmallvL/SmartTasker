package com.smarttasker.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = LightColors.accent,
    onPrimary = Color.White,
    primaryContainer = LightColors.accent.copy(alpha = 0.1f),
    secondary = LightColors.textSecondary,
    background = LightColors.bgPrimary,
    surface = LightColors.bgSecondary,
    onBackground = LightColors.textPrimary,
    onSurface = LightColors.textPrimary,
    surfaceVariant = LightColors.bgTertiary,
    outline = LightColors.borderSubtle,
    error = LightColors.danger
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkColors.accent,
    onPrimary = Color.White,
    primaryContainer = DarkColors.accent.copy(alpha = 0.15f),
    secondary = DarkColors.textSecondary,
    background = DarkColors.bgPrimary,
    surface = DarkColors.bgSecondary,
    onBackground = DarkColors.textPrimary,
    onSurface = DarkColors.textPrimary,
    surfaceVariant = DarkColors.bgTertiary,
    outline = DarkColors.borderSubtle,
    error = DarkColors.danger
)

@Composable
fun SmartTaskerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = SmartShapes,
        content = content
    )
}

// Helper to get custom colors
object SmartColors {
    @Composable
    fun accent() = if (isSystemInDarkTheme()) DarkColors.accent else LightColors.accent
    @Composable
    fun success() = if (isSystemInDarkTheme()) DarkColors.success else LightColors.success
    @Composable
    fun warning() = if (isSystemInDarkTheme()) DarkColors.warning else LightColors.warning
    @Composable
    fun danger() = if (isSystemInDarkTheme()) DarkColors.danger else LightColors.danger
    @Composable
    fun textSecondary() = if (isSystemInDarkTheme()) DarkColors.textSecondary else LightColors.textSecondary
    @Composable
    fun textTertiary() = if (isSystemInDarkTheme()) DarkColors.textTertiary else LightColors.textTertiary
    @Composable
    fun borderSubtle() = if (isSystemInDarkTheme()) DarkColors.borderSubtle else LightColors.borderSubtle
}
