package com.smarttasker.ui.trialrun

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.data.entity.RouteStepEntity
import com.smarttasker.data.entity.TaskEntity
import com.smarttasker.data.repository.RouteRepository
import com.smarttasker.ui.common.*
import com.smarttasker.ui.theme.SmartColors

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
            // Success banner
            item {
                SmartCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = SmartColors.success()
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "已学到路线",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 22.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "共 ${steps.size} 个步骤，后续执行将优先使用路线",
                            fontSize = 14.sp,
                            color = SmartColors.textSecondary()
                        )
                    }
                }
            }

            // Route summary
            if (steps.isNotEmpty()) {
                item {
                    SmartCard {
                        Text("路线摘要", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                        Spacer(Modifier.height(12.dp))
                        steps.forEachIndexed { index, step ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12),
                                    color = if (step.enabled) SmartColors.accent().copy(alpha = 0.1f)
                                    else SmartColors.textTertiary().copy(alpha = 0.1f),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            "${index + 1}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (step.enabled) SmartColors.accent() else SmartColors.textTertiary()
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        step.summary.ifEmpty { step.type },
                                        fontSize = 15.sp,
                                        color = if (step.enabled) MaterialTheme.colorScheme.onSurface
                                        else SmartColors.textTertiary()
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TypePill(step.type)
                                        LocatorPill(step.locatorStrategy, step.locatorValue)
                                        if (step.riskLevel != "low") RiskPill(step.riskLevel)
                                    }
                                }
                            }
                        }
                    }
                }

                // Stats row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            label = "成功步骤",
                            value = "${successCount}/${steps.size}",
                            color = SmartColors.success()
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            label = "风险等级",
                            value = when {
                                hasCriticalRisk -> "⚠️ 危险"
                                steps.any { it.riskLevel == "high" } -> "高"
                                steps.any { it.riskLevel == "medium" } -> "中"
                                else -> "低"
                            },
                            color = when {
                                hasCriticalRisk -> SmartColors.danger()
                                steps.any { it.riskLevel == "high" } -> SmartColors.warning()
                                else -> SmartColors.success()
                            }
                        )
                    }
                }

                // Route info
                item {
                    SmartCard {
                        Text("路线信息", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        InfoRow("路线 ID", routeId)
                        InfoRow("状态", "草稿")
                        InfoRow("来源", "手动录制")
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

            // Action buttons
            item {
                Spacer(Modifier.height(8.dp))
                SmartButton(
                    text = "保存并启用",
                    onClick = onSaveAndEnable,
                    icon = Icons.Outlined.CheckCircle,
                    enabled = steps.isNotEmpty()
                )
                Spacer(Modifier.height(8.dp))
                SmartSecondaryButton(
                    text = "进入 Route Studio 编辑",
                    onClick = { onOpenRouteStudio(routeId) },
                    icon = Icons.Outlined.Edit
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDiscard,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = SmartColors.textSecondary()
                    )
                ) {
                    Text("放弃路线")
                }
            }
        }
    }
}

@Composable
private fun TypePill(type: String) {
    val label = when (type) {
        "tap" -> "点击"
        "input" -> "输入"
        "swipe" -> "滑动"
        "wait" -> "等待"
        "open_app" -> "打开应用"
        "back" -> "返回"
        "home" -> "主页"
        "key" -> "按键"
        "screenshot" -> "截图"
        else -> type
    }
    Surface(
        shape = RoundedCornerShape(6),
        color = SmartColors.accent().copy(alpha = 0.08f)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 11.sp,
            color = SmartColors.accent()
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
        "critical" -> "🔴 危险" to SmartColors.danger()
        "high" -> "🟠 高风险" to SmartColors.warning()
        "medium" -> "🟡 中风险" to Color(0xFFF0AD4E)
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

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    color: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = color)
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 13.sp, color = SmartColors.textSecondary())
        }
    }
}
