package com.smarttasker.ui.trialrun

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.data.entity.RouteStepEntity
import com.smarttasker.data.entity.TaskEntity
import com.smarttasker.data.repository.RouteRepository
import com.smarttasker.ui.common.*
import com.smarttasker.ui.theme.SmartColors

// Step type visual mapping (same as RouteStudioScreen)
private val StepTypeColors = mapOf(
    "tap" to Color(0xFF3B82F6),
    "input" to Color(0xFF8B5CF6),
    "swipe" to Color(0xFFF59E0B),
    "wait" to Color(0xFF6B7280),
    "open_app" to Color(0xFF10B981),
    "back" to Color(0xFFEF4444),
    "home" to Color(0xFF06B6D4),
    "key" to Color(0xFFEC4899),
    "screenshot" to Color(0xFF6366F1)
)

private val StepTypeIcons = mapOf(
    "tap" to Icons.Outlined.TouchApp,
    "input" to Icons.Outlined.Keyboard,
    "swipe" to Icons.Outlined.Swipe,
    "wait" to Icons.Outlined.Timer,
    "open_app" to Icons.Outlined.Launch,
    "back" to Icons.Outlined.ArrowBack,
    "home" to Icons.Outlined.Home,
    "key" to Icons.Outlined.Keyboard,
    "screenshot" to Icons.Outlined.Screenshot
)

private val StepTypeLabels = mapOf(
    "tap" to "点击",
    "input" to "输入",
    "swipe" to "滑动",
    "wait" to "等待",
    "open_app" to "打开应用",
    "back" to "返回",
    "home" to "主页",
    "key" to "按键",
    "screenshot" to "截图"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteLearningResultScreen(
    task: TaskEntity,
    routeId: String,
    routeRepo: RouteRepository,
    onSaveAndEnable: () -> Unit,
    onOpenRouteStudio: (String) -> Unit,
    onDiscard: () -> Unit
) {
    val steps by routeRepo.getStepsForRoute(routeId).collectAsState(initial = emptyList())

    val successCount = steps.count { it.enabled }
    val hasCriticalRisk = steps.any { it.riskLevel == "critical" }

    // Animated scale for checkmark
    val infiniteTransition = rememberInfiniteTransition(label = "checkPulse")
    val checkScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "checkScale"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("学习结果", fontWeight = FontWeight.SemiBold) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Success celebration header ──
            item {
                Surface(
                    shape = RoundedCornerShape(20),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Gradient celebration area
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            SmartColors.success().copy(alpha = 0.15f),
                                            SmartColors.accent().copy(alpha = 0.08f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                                )
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Animated checkmark
                                Surface(
                                    shape = CircleShape,
                                    color = SmartColors.success().copy(alpha = 0.15f),
                                    modifier = Modifier.size((72 * checkScale).dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Outlined.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = SmartColors.success()
                                        )
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "已学到路线",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "共 ${steps.size} 个步骤，后续执行将优先使用路线",
                                    fontSize = 14.sp,
                                    color = SmartColors.textSecondary()
                                )
                            }
                        }
                    }
                }
            }

            // ── Route summary with timeline connector ──
            if (steps.isNotEmpty()) {
                item {
                    Text(
                        "路线摘要",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                // Steps with timeline connector (same style as RouteStudioScreen)
                items(steps.size) { index ->
                    val step = steps[index]
                    val isLast = index == steps.lastIndex
                    val stepColor = StepTypeColors[step.type] ?: SmartColors.accent()
                    val stepIcon = StepTypeIcons[step.type] ?: Icons.Outlined.TouchApp
                    val connectorColor = SmartColors.borderSubtle()

                    Row(modifier = Modifier.fillMaxWidth()) {
                        // ── Left: Timeline connector ──
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(44.dp)
                        ) {
                            // Top connector line
                            Canvas(modifier = Modifier.width(2.dp).height(12.dp)) {
                                drawLine(
                                    color = connectorColor,
                                    start = Offset(size.width / 2f, 0f),
                                    end = Offset(size.width / 2f, size.height),
                                    strokeWidth = 2f
                                )
                            }

                            // Step circle with icon
                            Surface(
                                shape = CircleShape,
                                color = if (step.enabled) stepColor.copy(alpha = 0.15f)
                                else SmartColors.textTertiary().copy(alpha = 0.08f),
                                border = BorderStroke(
                                    2.dp,
                                    if (step.enabled) stepColor
                                    else SmartColors.textTertiary().copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        stepIcon,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (step.enabled) stepColor else SmartColors.textTertiary()
                                    )
                                }
                            }

                            // Bottom connector line
                            if (!isLast) {
                                Canvas(modifier = Modifier.width(2.dp).weight(1f)) {
                                    drawLine(
                                        color = connectorColor,
                                        start = Offset(size.width / 2f, 0f),
                                        end = Offset(size.width / 2f, size.height),
                                        strokeWidth = 2f
                                    )
                                }
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        // ── Right: Step content card ──
                        Column(modifier = Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 4.dp)) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, SmartColors.borderSubtle())
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Step index badge
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = stepColor.copy(alpha = 0.1f)
                                        ) {
                                            Text(
                                                "#${index + 1}",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = stepColor
                                            )
                                        }
                                        // Step summary
                                        Text(
                                            step.summary.ifEmpty { step.type },
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (step.enabled) MaterialTheme.colorScheme.onSurface
                                            else SmartColors.textTertiary(),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    // Pills
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TypePill(step.type)
                                        LocatorPill(step.locatorStrategy, step.locatorValue)
                                        if (step.riskLevel != "low") RiskPill(step.riskLevel)
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Stats cards with gradient backgrounds ──
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GradientStatCard(
                            modifier = Modifier.weight(1f),
                            label = "成功步骤",
                            value = "${successCount}/${steps.size}",
                            gradientColors = listOf(
                                SmartColors.success().copy(alpha = 0.12f),
                                SmartColors.accent().copy(alpha = 0.06f)
                            ),
                            valueColor = SmartColors.success(),
                            icon = Icons.Outlined.CheckCircle
                        )
                        GradientStatCard(
                            modifier = Modifier.weight(1f),
                            label = "风险等级",
                            value = when {
                                hasCriticalRisk -> "危险"
                                steps.any { it.riskLevel == "high" } -> "高"
                                steps.any { it.riskLevel == "medium" } -> "中"
                                else -> "低"
                            },
                            gradientColors = when {
                                hasCriticalRisk -> listOf(
                                    SmartColors.danger().copy(alpha = 0.12f),
                                    SmartColors.danger().copy(alpha = 0.04f)
                                )
                                steps.any { it.riskLevel == "high" } -> listOf(
                                    SmartColors.warning().copy(alpha = 0.12f),
                                    SmartColors.warning().copy(alpha = 0.04f)
                                )
                                else -> listOf(
                                    SmartColors.success().copy(alpha = 0.12f),
                                    SmartColors.success().copy(alpha = 0.04f)
                                )
                            },
                            valueColor = when {
                                hasCriticalRisk -> SmartColors.danger()
                                steps.any { it.riskLevel == "high" } -> SmartColors.warning()
                                else -> SmartColors.success()
                            },
                            icon = when {
                                hasCriticalRisk -> Icons.Outlined.Warning
                                steps.any { it.riskLevel == "high" } -> Icons.Outlined.WarningAmber
                                else -> Icons.Outlined.Shield
                            }
                        )
                    }
                }

                // ── Route info card ──
                item {
                    SmartCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10),
                                color = SmartColors.accent().copy(alpha = 0.1f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Outlined.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = SmartColors.accent()
                                    )
                                }
                            }
                            Text("路线信息", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                        InfoRow("路线 ID", routeId)
                        InfoRow("状态", "草稿")
                        InfoRow("来源", when (steps.firstOrNull()?.source) {
                            "ai_learned" -> "AI 学习"
                            "manual_recording" -> "手动录制"
                            else -> steps.firstOrNull()?.source?.ifEmpty { "未知" } ?: "未知"
                        })
                        InfoRow("创建时间", formatTimestamp(System.currentTimeMillis()))
                    }
                }
            } else {
                // Empty state
                item {
                    SmartCard {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Outlined.Route,
                                null,
                                modifier = Modifier.size(48.dp),
                                tint = SmartColors.textTertiary()
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("暂未学到步骤", color = SmartColors.textTertiary())
                            Text("请重新录制或使用 AI 模式", fontSize = 13.sp, color = SmartColors.textTertiary())
                        }
                    }
                }
            }

            // ── Action buttons with better visual hierarchy ──
            item {
                Spacer(Modifier.height(8.dp))
                // Primary action
                SmartButton(
                    text = "保存并启用",
                    onClick = onSaveAndEnable,
                    icon = Icons.Outlined.CheckCircle,
                    enabled = steps.isNotEmpty()
                )
                Spacer(Modifier.height(10.dp))
                // Secondary action
                SmartSecondaryButton(
                    text = "进入 Route Studio 编辑",
                    onClick = { onOpenRouteStudio(routeId) },
                    icon = Icons.Outlined.Edit
                )
                Spacer(Modifier.height(10.dp))
                // Destructive action
                OutlinedButton(
                    onClick = onDiscard,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = SmartColors.danger()
                    ),
                    border = BorderStroke(1.dp, SmartColors.danger().copy(alpha = 0.3f))
                ) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("放弃路线", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                }
            }
        }
    }
}

// ── Gradient stat card ──
@Composable
private fun GradientStatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    gradientColors: List<Color>,
    valueColor: Color,
    icon: ImageVector
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box {
            // Gradient background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(colors = gradientColors),
                        shape = RoundedCornerShape(20)
                    )
            )
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = valueColor.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(8.dp))
                Text(value, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
                Spacer(Modifier.height(4.dp))
                Text(label, fontSize = 13.sp, color = SmartColors.textSecondary())
            }
        }
    }
}

@Composable
private fun TypePill(type: String) {
    val label = StepTypeLabels[type] ?: type
    val color = StepTypeColors[type] ?: SmartColors.accent()
    Surface(
        shape = RoundedCornerShape(6),
        color = color.copy(alpha = 0.08f)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 11.sp,
            color = color
        )
    }
}

@Composable
private fun LocatorPill(strategy: String, value: String) {
    if (value.isBlank()) return
    val label = when (strategy) {
        "coordinate" -> "坐标"
        "text" -> "文本"
        "resource_id" -> "ID"
        "content_desc" -> "描述"
        "key" -> "按键"
        "package" -> "包名"
        "time" -> "时长"
        else -> strategy.take(6)
    }
    val shortVal = if (value.length > 12) value.take(12) + "…" else value
    Surface(
        shape = RoundedCornerShape(6),
        color = SmartColors.textTertiary().copy(alpha = 0.08f)
    ) {
        Text(
            "$label: $shortVal",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 11.sp,
            color = SmartColors.textSecondary()
        )
    }
}

@Composable
private fun RiskPill(level: String) {
    val (label, color) = when (level) {
        "critical" -> "危险" to SmartColors.danger()
        "high" -> "高风险" to SmartColors.warning()
        "medium" -> "中风险" to Color(0xFFF0AD4E)
        else -> return
    }
    Surface(
        shape = RoundedCornerShape(6),
        color = color.copy(alpha = 0.1f)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 11.sp,
            color = color
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = SmartColors.textSecondary())
        Text(value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun formatTimestamp(millis: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(millis))
}
