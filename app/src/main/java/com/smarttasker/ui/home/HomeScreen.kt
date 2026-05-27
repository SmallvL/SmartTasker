package com.smarttasker.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.smarttasker.core.bridge.CoreBridgeManager
import com.smarttasker.core.bridge.CoreStatus
import com.smarttasker.data.entity.TaskEntity
import com.smarttasker.data.repository.TaskRepository
import com.smarttasker.data.repository.RunRepository
import com.smarttasker.ui.common.*
import com.smarttasker.ui.theme.SmartColors

@Composable
fun HomeScreen(
    taskRepo: TaskRepository,
    runRepo: RunRepository,
    coreBridgeManager: CoreBridgeManager,
    onCreateTask: (String) -> Unit = {},
    onTaskClick: (TaskEntity) -> Unit = {},
    onNavigateToTaskList: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val recentTasks by taskRepo.getRecentTasks(5).collectAsState(initial = emptyList())
    val activeCount by taskRepo.getActiveTaskCount().collectAsState(initial = 0)
    val successCount by runRepo.getTodaySuccessCount().collectAsState(initial = 0)
    val failedCount by runRepo.getTodayFailedCount().collectAsState(initial = 0)
    val modelCalls by runRepo.getTodayModelCalls().collectAsState(initial = 0)
    val failedRuns by runRepo.getRecentFailedRuns(3).collectAsState(initial = emptyList())
    
    // CoreBridge real status
    val coreStatus by coreBridgeManager.coreStatus.collectAsState()
    val isChecking by coreBridgeManager.isChecking.collectAsState()

    var taskInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with real Core status
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "SmartTask",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.5).sp
                        )
                    )
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = SmartColors.textTertiary()
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Real Core status pill
                    CoreStatusPill(coreStatus)
                    Spacer(Modifier.width(8.dp))
                    // Permission status (simplified for now)
                    StatusPill("权限正常", SmartColors.success())
                }
                Spacer(Modifier.height(8.dp))
                // Capability badges
                CapabilityBadges(coreStatus)
            }
        }

        // Shell-only mode info card
        if (coreStatus is CoreStatus.ShellOnly) {
            item {
                SmartCard(
                    onClick = onNavigateToSettings,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = SmartColors.warning(),
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("基础模式 · 路线执行可用", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Text(
                                "当前通过 SH 模式运行，路线回放正常。\n录制和截图需要无线调试或 Root 权限。",
                                fontSize = 13.sp,
                                color = SmartColors.textSecondary(),
                                lineHeight = 18.sp
                            )
                        }
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = SmartColors.textTertiary()
                        )
                    }
                }
            }
        }

        // Core not running warning
        if (coreStatus is CoreStatus.Stopped || coreStatus is CoreStatus.Error) {
            item {
                SmartCard(
                    onClick = onNavigateToSettings,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = SmartColors.warning(),
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Core 未连接", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Text(
                                "点击前往设置启动 AutoLXB Core",
                                fontSize = 13.sp,
                                color = SmartColors.textSecondary()
                            )
                        }
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = SmartColors.textTertiary()
                        )
                    }
                }
            }
        }

        // One-sentence task creation
        item {
            SmartCard {
                Text(
                    "你想自动完成什么？",
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(12.dp))
                SmartInputField(
                    value = taskInput,
                    onValueChange = { taskInput = it },
                    placeholder = "每天早上9点打开淘宝收金币"
                )
                Spacer(Modifier.height(12.dp))
                SmartButton(
                    text = "创建任务",
                    onClick = { onCreateTask(taskInput) },
                    enabled = taskInput.isNotBlank(),
                    icon = Icons.Outlined.AutoAwesome
                )
            }
        }

        // Today summary
        item {
            SmartCard {
                Text("今日", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SummaryItem("成功", successCount.toString(), SmartColors.success())
                    SummaryItem("失败", failedCount.toString(), SmartColors.danger())
                    SummaryItem("AI 调用", (modelCalls ?: 0).toString(), SmartColors.accent())
                    SummaryItem("任务", activeCount.toString(), SmartColors.textSecondary())
                }
            }
        }

        // Issues
        if (failedRuns.isNotEmpty()) {
            item { SectionHeader("需要处理") }
            items(failedRuns) { run ->
                SmartCard(onClick = { /* TODO: navigate to trace */ }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(run.diagnosisSummary.ifEmpty { "任务执行失败" }, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            if (run.diagnosisSuggestion.isNotEmpty()) {
                                Text(run.diagnosisSuggestion, fontSize = 13.sp, color = SmartColors.textSecondary(), modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                        StatusPill("失败", SmartColors.danger())
                    }
                }
            }
        }

        // Recent tasks
        item { SectionHeader("最近任务", action = "查看全部", onAction = onNavigateToTaskList) }
        if (recentTasks.isEmpty()) {
            item {
                EmptyState(icon = Icons.Outlined.CheckCircle, title = "还没有任务", subtitle = "在上方输入你想自动完成的事情")
            }
        } else {
            items(recentTasks) { task ->
                TaskMiniCard(task, onClick = { onTaskClick(task) })
            }
        }
    }
}

@Composable
private fun CoreStatusPill(status: CoreStatus) {
    val (text, color) = when (status) {
        is CoreStatus.Running -> "Core 运行中" to SmartColors.success()
        is CoreStatus.ShellOnly -> "基础模式" to SmartColors.warning()
        is CoreStatus.Stopped -> "Core 未运行" to SmartColors.warning()
        is CoreStatus.Error -> "连接异常" to SmartColors.danger()
        is CoreStatus.Unknown -> "检查中..." to SmartColors.textTertiary()
    }
    StatusPill(text, color)
}

@Composable
private fun CapabilityBadges(status: CoreStatus) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // Execution capability
        StatusPill(
            if (status.canExecute) "✅ 路线执行" else "❌ 路线执行",
            if (status.canExecute) SmartColors.success() else SmartColors.textTertiary()
        )
        // Recording capability
        StatusPill(
            if (status.canRecord) "✅ 录制" else "⚠️ 需无线调试",
            if (status.canRecord) SmartColors.success() else SmartColors.warning()
        )
    }
}

@Composable
private fun SummaryItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = color)
        Text(label, fontSize = 12.sp, color = SmartColors.textTertiary())
    }
}

@Composable
private fun TaskMiniCard(task: TaskEntity, onClick: () -> Unit) {
    SmartCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(task.name, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Text(
                    when (task.triggerType) {
                        "schedule" -> "定时 · ${task.triggerTime}"
                        "notification" -> "通知触发"
                        else -> "手动执行"
                    },
                    fontSize = 13.sp,
                    color = SmartColors.textTertiary()
                )
            }
            StatusPill(
                when (task.status) {
                    "active" -> "运行中"
                    "paused" -> "已暂停"
                    "draft" -> "草稿"
                    else -> task.status
                },
                when (task.status) {
                    "active" -> SmartColors.success()
                    "paused" -> SmartColors.warning()
                    else -> SmartColors.textTertiary()
                }
            )
        }
    }
}
