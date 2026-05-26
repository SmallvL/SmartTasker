package com.smarttasker.ui.trace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.smarttasker.ui.common.*
import com.smarttasker.ui.theme.SmartColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TraceExplainerScreen(
    run: RunRecordEntity,
    onOpenRouteStudio: () -> Unit,
    onBack: () -> Unit
) {
    var showTechLog by remember { mutableStateOf(false) }

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
                                val logLines = listOf(
                                    "[${run.startedAt}] RUN_START task=${run.taskId}",
                                    "[${run.startedAt + 100}] STEP_START step=1 type=open_app",
                                    "[${run.startedAt + 500}] STEP_OK step=1",
                                    "[${run.startedAt + 600}] STEP_START step=2 type=wait",
                                    "[${run.startedAt + 1600}] STEP_OK step=2",
                                    "[${run.startedAt + 1700}] STEP_START step=3 type=tap locator=text",
                                    "[${run.startedAt + 3200}] LOCATOR_MISS strategy=text value=\"目标\"",
                                    "[${run.startedAt + 3300}] STEP_FAIL step=3 reason=locator_not_found",
                                    "[${run.startedAt + 3400}] RUN_END status=failed"
                                )
                                logLines.forEach { line ->
                                    Text(
                                        line,
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
