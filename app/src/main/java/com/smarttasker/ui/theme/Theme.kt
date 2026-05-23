package com.smarttasker.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// 亮色主题颜色
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF007AFF),           // 苹果蓝
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F2FD),
    onPrimaryContainer = Color(0xFF001F3F),
    
    secondary = Color(0xFF5856D6),         // 苹果紫
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE7F6),
    onSecondaryContainer = Color(0xFF1A1A2E),
    
    tertiary = Color(0xFFFF9500),          // 苹果橙
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFF3E0),
    onTertiaryContainer = Color(0xFF3E2723),
    
    error = Color(0xFFFF3B30),             // 苹果红
    onError = Color.White,
    errorContainer = Color(0xFFFFEBEE),
    onErrorContainer = Color(0xFFB71C1C),
    
    background = Color(0xFFF2F2F7),        // 苹果背景灰
    onBackground = Color(0xFF1C1C1E),
    
    surface = Color.White,
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF8E8E93),
    
    outline = Color(0xFFC6C6C8),
    outlineVariant = Color(0xFFE5E5EA),
    
    inverseSurface = Color(0xFF1C1C1E),
    inverseOnSurface = Color(0xFFF2F2F7),
    inversePrimary = Color(0xFF4DA6FF),
)

// 暗色主题颜色
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4DA6FF),           // 苹果蓝（暗色）
    onPrimary = Color(0xFF001F3F),
    primaryContainer = Color(0xFF003A70),
    onPrimaryContainer = Color(0xFFE3F2FD),
    
    secondary = Color(0xFF7B79E8),         // 苹果紫（暗色）
    onSecondary = Color(0xFF1A1A2E),
    secondaryContainer = Color(0xFF3D3D5C),
    onSecondaryContainer = Color(0xFFEDE7F6),
    
    tertiary = Color(0xFFFFB340),          // 苹果橙（暗色）
    onTertiary = Color(0xFF3E2723),
    tertiaryContainer = Color(0xFF6D4C41),
    onTertiaryContainer = Color(0xFFFFF3E0),
    
    error = Color(0xFFFF6B6B),             // 苹果红（暗色）
    onError = Color(0xFFB71C1C),
    errorContainer = Color(0xFF8B0000),
    onErrorContainer = Color(0xFFFFEBEE),
    
    background = Color(0xFF000000),        // 苹果背景黑
    onBackground = Color(0xFFF2F2F7),
    
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFF8E8E93),
    
    outline = Color(0xFF48484A),
    outlineVariant = Color(0xFF38383A),
    
    inverseSurface = Color(0xFFF2F2F7),
    inverseOnSurface = Color(0xFF1C1C1E),
    inversePrimary = Color(0xFF007AFF),
)

// 自定义颜色
object AppColors {
    // 状态颜色
    val success = Color(0xFF34C759)        // 苹果绿
    val warning = Color(0xFFFF9500)        // 苹果橙
    val info = Color(0xFF007AFF)           // 苹果蓝
    
    // 渐变色
    val gradientStart = Color(0xFF007AFF)
    val gradientEnd = Color(0xFF5856D6)
    
    // 卡片背景
    val cardLight = Color.White
    val cardDark = Color(0xFF2C2C2E)
    
    // 分割线
    val dividerLight = Color(0xFFE5E5EA)
    val dividerDark = Color(0xFF38383A)
}

@Composable
fun SmartTaskerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
