package com.smarttasker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// 苹果风格圆角形状
val Shapes = Shapes(
    // 小圆角（按钮、标签等）
    small = RoundedCornerShape(8.dp),
    
    // 中圆角（卡片、输入框等）
    medium = RoundedCornerShape(12.dp),
    
    // 大圆角（对话框、底部弹窗等）
    large = RoundedCornerShape(16.dp),
    
    // 超大圆角（全屏卡片等）
    extraLarge = RoundedCornerShape(24.dp)
)

// 自定义形状
object AppShapes {
    // 按钮圆角
    val button = RoundedCornerShape(10.dp)
    
    // 卡片圆角
    val card = RoundedCornerShape(12.dp)
    
    // 输入框圆角
    val textField = RoundedCornerShape(10.dp)
    
    // 对话框圆角
    val dialog = RoundedCornerShape(16.dp)
    
    // 底部弹窗圆角
    val bottomSheet = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    
    // 芯片圆角
    val chip = RoundedCornerShape(8.dp)
    
    // 图片圆角
    val image = RoundedCornerShape(12.dp)
    
    // 图标圆角
    val icon = RoundedCornerShape(8.dp)
    
    // 进度条圆角
    val progressBar = RoundedCornerShape(4.dp)
    
    // 分割线
    val divider = RoundedCornerShape(0.5.dp)
}
