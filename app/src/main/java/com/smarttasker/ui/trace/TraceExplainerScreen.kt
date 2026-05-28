package com.smarttasker.ui.trace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.smarttasker.data.entity.RunRecordEntity
import com.smarttasker.data.entity.TraceEventEntity
import com.smarttasker.ui.common.*
import com.smarttasker.ui.theme.SmartColors
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TraceExplainerScreen(
    run: RunRecordEntity,
    traceEvents: List<TraceEventEntity>,
    onOpenRouteStudio: () -> Unit,
    onBack: () -> Unit
) {
    var showTechLog by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    // Group trace events by step
    val stepEvents = traceEvents.filter { it.stepId.isNotEmpty() }.groupBy { it.stepId }
    val failedStep = traceEvents.find { it.stepResult == "failed" }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("失败诊断", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Failure summary
            item {
                SmartCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Error,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = SmartColors.danger()
                        )
                        Column {
                            Text("任务执行失败", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                            if (run.failedStepId != null) {
                                Text(
                                    "失败在第 ${run.failedStepId} 步",
                                    fontSize = 14.sp,
                                    color = SmartColors.textSecondary()
                                )
                            }
                            Text(
                                "耗时 ${run.durationMs}ms · ${dateFormat.format(Date(run.startedAt))}",
                                fontSize = 13.sp,
                                color = SmartColors.textTertiary()
                            )
                        }
                    }
                }
            }

            // Human-readable diagnosis
            item {
                SmartCard {
                    Text("原因分析", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        run.diagnosisSummary.ifEmpty {
                            when (run.failureType) {
                                "locator_not_found" -> "没有找到目标控件。可能是 App 首页布局变化，原路线使用坐标点击，位置已经偏移。"
                                "timeout" -> "操作超时。页面加载时间过长或网络不稳定。"
                                "model_error" -> "AI 模型调用失败。请检查 API 配置和网络连接。"
                                "safety_blocked" -> "操作被安全策略拦截。该动作被标记为高风险。"
                                else -> "执行过程中出现未知错误。"
                            }
                        },
                        fontSize = 15.sp,
                        lineHeight = 24.sp
                    )

                    // Show failed step details if available
                    if (failedStep != null) {
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SmartColors.danger().copy(alpha = 0.1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "失败步骤详情",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    color = SmartColors.danger()
                                )
                                Spacer(Modifier.height(4.dp))
                                Text("步骤 ID: ${failedStep.stepId}", fontSize = 13.sp)
                                Text("操作类型: ${failedStep.stepType}", fontSize = 13.sp)
                                Text("目标: ${failedStep.stepTarget}", fontSize = 13.sp)
                                if (failedStep.message.isNotEmpty()) {
                                    Text("错误信息: ${failedStep.message}", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Suggestion
            item {
                SmartCard {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Lightbulb,
                            contentDescription = null,
                            tint = SmartColors.warning(),
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text("修复建议", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                run.diagnosisSuggestion.ifEmpty {
                                    when (run.failureType) {
                                        "locator_not_found" -> "将坐标定位改为文本定位或控件ID定位，提高稳定性。"
                                        "timeout" -> "增加等待时间或重试次数。"
                                        "model_error" -> "检查模型 API 地址和密钥是否正确。"
                                        else -> "进入 Route Studio 查看详细步骤并手动修复。"
                                    }
                                },
                                fontSize = 14.sp,
                                color = SmartColors.textSecondary(),
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
            }

            // Execution timeline
            if (stepEvents.isNotEmpty()) {
                item {
                    Text(
                        "执行时间线",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(stepEvents.entries.toList()) { (stepId, events) ->
                    val stepStart = events.find { it.eventType == "step_start" }
                    val stepEnd = events.find { it.eventType == "step_end" }
                    val isFailed = events.any { it.stepResult == "failed" }
                    val isSuccess = events.any { it.stepResult == "success" }

                    SmartCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Status indicator
                            Icon(
                                when {
                                    isFailed -> Icons.Outlined.Error
                                    isSuccess -> Icons.Outlined.CheckCircle
                                    else -> Icons.Outlined.Schedule
                                },
                                contentDescription = null,
                                tint = when {
                                    isFailed -> SmartColors.danger()
                                    isSuccess -> SmartColors.success()
                                    else -> SmartColors.textTertiary()
                                },
                                modifier = Modifier.size(20.dp)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "步骤 $stepId",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                                if (stepStart != null) {
                                    Text(
                                        "类型: ${stepStart.stepType} · 目标: ${stepStart.stepTarget}",
                                        fontSize = 12.sp,
                                        color = SmartColors.textSecondary()
                                    )
                                }
                            }

                            // Duration
                            if (stepStart != null && stepEnd != null) {
                                Text(
                                    "${stepEnd.durationMs}ms",
                                    fontSize = 12.sp,
                                    color = SmartColors.textTertiary()
                                )
                            }
                        }

                        // Show error details for failed step
                        if (isFailed) {
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SmartColors.danger().copy(alpha = 0.1f)
                            ) {
                                Text(
                                    events.filter { it.level == "error" }.joinToString("\n") { it.message },
                                    fontSize = 12.sp,
                                    color = SmartColors.danger(),
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Quick action
            item {
                SmartButton(
                    text = "进入 Route Studio 修复",
                    onClick = onOpenRouteStudio,
                    icon = Icons.Outlined.Edit
                )
            }

            // Tech log toggle
            item {
                OutlinedButton(
                    onClick = { showTechLog = !showTechLog },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16)
                ) {
                    Icon(
                        if (showTechLog) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (showTechLog) "隐藏技术日志" else "查看技术日志")
                }
            }

            // Tech log
            if (showTechLog) {
                item {
                    SmartCard {
                        Text("技术日志", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = SmartColors.textTertiary())
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12),
                            color = Color(0xFF1A1A1A)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                if (traceEvents.isEmpty()) {
                                    Text(
                                        "暂无详细日志",
                                        fontSize = 12.sp,
                                        color = Color(0xFF10A37F),
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                } else {
                                    traceEvents.forEach { event ->
                                        Text(
                                            "[${dateFormat.format(Date(event.timestamp))}] ${event.eventType} ${event.message}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF10A37F),
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
