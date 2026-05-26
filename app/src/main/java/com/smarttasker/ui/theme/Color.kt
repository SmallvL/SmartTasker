package com.smarttasker.ui.theme

import androidx.compose.ui.graphics.Color

// Light theme tokens
object LightColors {
    val bgPrimary = Color(0xFFF7F7F8)         // 浅灰主背景
    val bgSecondary = Color(0xFFFFFFFF)        // 白色卡片
    val bgTertiary = Color(0xFFF0F0F0)         // 更浅灰
    val textPrimary = Color(0xFF1A1A1A)        // 深黑主文字
    val textSecondary = Color(0xFF6B6B6B)      // 中灰次级文字
    val textTertiary = Color(0xFF9B9B9B)       // 浅灰弱文字
    val borderSubtle = Color(0xFFE5E5E5)       // 轻边框
    val accent = Color(0xFF10A37F)             // OpenAI 绿色强调
    val accentHover = Color(0xFF0D8C6D)
    val success = Color(0xFF10A37F)
    val warning = Color(0xFFE8A317)
    val danger = Color(0xFFEF4444)
    val surface = Color(0xFFFFFFFF)
    val overlay = Color(0x80000000)
}

// Dark theme tokens
object DarkColors {
    val bgPrimary = Color(0xFF212121)          // 深灰主背景（非纯黑）
    val bgSecondary = Color(0xFF2F2F2F)        // 卡片背景
    val bgTertiary = Color(0xFF3A3A3A)         // 更浅
    val textPrimary = Color(0xFFECECEC)        // 浅白主文字
    val textSecondary = Color(0xFF9B9B9B)      // 中灰次级文字
    val textTertiary = Color(0xFF6B6B6B)       // 暗灰弱文字
    val borderSubtle = Color(0xFF424242)       // 轻边框
    val accent = Color(0xFF10A37F)             // 保持一致
    val accentHover = Color(0xFF13BF97)
    val success = Color(0xFF10A37F)
    val warning = Color(0xFFE8A317)
    val danger = Color(0xFFEF4444)
    val surface = Color(0xFF2F2F2F)
    val overlay = Color(0x80000000)
}
