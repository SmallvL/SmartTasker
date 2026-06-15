package com.smarttasker.ui.trialrun

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.ui.common.SmartCard
import com.smarttasker.ui.theme.SmartColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrialModeSelectScreen(
    taskName: String,
    onAiMode: () -> Unit,
    onManualMode: () -> Unit,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("首次试跑", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Outlined.Close, contentDescription = "取消")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Task info card
            SmartCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10),
                        color = SmartColors.accent().copy(alpha = 0.12f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.RocketLaunch,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = SmartColors.accent()
                            )
                        }
                    }
                    Column {
                        Text(taskName, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        Text(
                            "选择执行模式开始首次试跑",
                            fontSize = 14.sp,
                            color = SmartColors.textSecondary()
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── AI Mode card with indigo/purple gradient ──
            Card(
                onClick = onAiMode,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                ),
                border = null
            ) {
                Box {
                    // Gradient background
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF5B6EF5).copy(alpha = 0.15f),
                                        Color(0xFF8B5CF6).copy(alpha = 0.12f),
                                        Color(0xFF7C3AED).copy(alpha = 0.08f)
                                    )
                                ),
                                shape = RoundedCornerShape(20)
                            )
                    )

                    Column(modifier = Modifier.padding(24.dp)) {
                        // Header row with sparkle icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Sparkle icon with gradient background
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14))
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                Color(0xFF5B6EF5),
                                                Color(0xFF8B5CF6)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Column {
                                Text(
                                    "AI 自动执行",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    "AI 分析屏幕并自主操作",
                                    fontSize = 13.sp,
                                    color = SmartColors.textSecondary()
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Feature list with icons
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            FeatureItem(
                                icon = Icons.Outlined.Visibility,
                                text = "AI 截图分析屏幕内容",
                                color = Color(0xFF5B6EF5)
                            )
                            FeatureItem(
                                icon = Icons.Outlined.Psychology,
                                text = "自动决定下一步操作",
                                color = Color(0xFF7C3AED)
                            )
                            FeatureItem(
                                icon = Icons.Outlined.Api,
                                text = "需要配置 VLM API",
                                color = Color(0xFF8B5CF6)
                            )
                            FeatureItem(
                                icon = Icons.Outlined.Explore,
                                text = "适合复杂/未知流程",
                                color = Color(0xFF6366F1)
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // Recommended badge
                        Surface(
                            shape = RoundedCornerShape(100),
                            color = Color(0xFF5B6EF5).copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = Color(0xFF5B6EF5)
                                )
                                Text(
                                    "推荐",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF5B6EF5)
                                )
                            }
                        }
                    }
                }
            }

            // ── Manual Recording Mode card with green gradient ──
            Card(
                onClick = onManualMode,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                ),
                border = null
            ) {
                Box {
                    // Gradient background
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF10B981).copy(alpha = 0.12f),
                                        Color(0xFF059669).copy(alpha = 0.08f),
                                        Color(0xFF047857).copy(alpha = 0.05f)
                                    )
                                ),
                                shape = RoundedCornerShape(20)
                            )
                    )

                    Column(modifier = Modifier.padding(24.dp)) {
                        // Header row with recording icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Recording icon with gradient background
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14))
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                Color(0xFF10B981),
                                                Color(0xFF059669)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.FiberManualRecord,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Column {
                                Text(
                                    "手动录制",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    "你操作，我学习",
                                    fontSize = 13.sp,
                                    color = SmartColors.textSecondary()
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Feature list with icons
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            FeatureItem(
                                icon = Icons.Outlined.TouchApp,
                                text = "你手动操作手机",
                                color = Color(0xFF10B981)
                            )
                            FeatureItem(
                                icon = Icons.Outlined.VideoCameraFront,
                                text = "系统录制你的操作",
                                color = Color(0xFF059669)
                            )
                            FeatureItem(
                                icon = Icons.Outlined.MoneyOff,
                                text = "无需 API，零成本",
                                color = Color(0xFF047857)
                            )
                            FeatureItem(
                                icon = Icons.Outlined.Repeat,
                                text = "适合固定流程",
                                color = Color(0xFF10B981)
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // Zero cost badge
                        Surface(
                            shape = RoundedCornerShape(100),
                            color = Color(0xFF10B981).copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Savings,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = Color(0xFF10B981)
                                )
                                Text(
                                    "零成本",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF10B981)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── Feature comparison section ──
            SmartCard {
                Text(
                    "模式对比",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(16.dp))

                // Comparison rows
                ComparisonRow("智能程度", "全自动", "需手动操作")
                ComparisonRow("适用场景", "复杂/未知流程", "固定流程")
                ComparisonRow("API 依赖", "需要 VLM API", "无需 API")
                ComparisonRow("学习精度", "依赖模型准确度", "精确录制操作")
                ComparisonRow("成本", "按调用计费", "完全免费")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Feature item with icon ──
@Composable
private fun FeatureItem(
    icon: ImageVector,
    text: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(8),
            color = color.copy(alpha = 0.1f),
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = color
                )
            }
        }
        Text(text, fontSize = 14.sp, color = SmartColors.textSecondary())
    }
}

// ── Comparison row ──
@Composable
private fun ComparisonRow(
    label: String,
    aiValue: String,
    manualValue: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = SmartColors.textTertiary(),
            modifier = Modifier.weight(1f)
        )
        Surface(
            shape = RoundedCornerShape(6),
            color = Color(0xFF5B6EF5).copy(alpha = 0.08f)
        ) {
            Text(
                aiValue,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                fontSize = 12.sp,
                color = Color(0xFF5B6EF5),
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(6),
            color = Color(0xFF10B981).copy(alpha = 0.08f)
        ) {
            Text(
                manualValue,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                fontSize = 12.sp,
                color = Color(0xFF10B981),
                fontWeight = FontWeight.Medium
            )
        }
    }
}
