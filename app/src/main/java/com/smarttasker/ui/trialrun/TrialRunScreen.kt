package com.smarttasker.ui.trialrun

import androidx.compose.animation.*
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
import com.smarttasker.core.parser.TaskSpec
import com.smarttasker.data.entity.RouteStepEntity
import com.smarttasker.data.entity.TaskEntity
import com.smarttasker.service.ExecutionResult
import com.smarttasker.service.ExecutionState
import com.smarttasker.service.TaskExecutionService
import com.smarttasker.ui.common.*
import com.smarttasker.ui.theme.SmartColors

enum class TrialStepStatus { PENDING, RUNNING, SUCCESS, FAILED, SKIPPED }

data class TrialStep(
    val index: Int,
    val summary: String,
    val status: TrialStepStatus = TrialStepStatus.PENDING,
    val detail: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrialRunScreen(
    task: TaskEntity,
    executionService: TaskExecutionService,
    onComplete: (List<TrialStep>) -> Unit,
    onCancel: () -> Unit
) {
    val executionState by executionService.executionState.collectAsState()
    
    var steps by remember {
        mutableStateOf(listOf(
            TrialStep(1, "打开${task.targetAppName.ifEmpty { "目标应用" }}"),
            TrialStep(2, "等待页面加载"),
            TrialStep(3, "查找目标入口"),
            TrialStep(4, "执行操作"),
            TrialStep(5, "检查执行结果")
        ))
    }
    var currentStep by remember { mutableStateOf(0) }
    var isRunning by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("准备开始试跑...") }
    var executionResult by remember { mutableStateOf<ExecutionResult?>(null) }
    
    // Update UI based on execution state
    LaunchedEffect(executionState) {
        when (val state = executionState) {
            is ExecutionState.Submitting -> {
                isRunning = true
                statusText = "正在提交任务..."
                steps = steps.mapIndexed { i, step ->
                    if (i == 0) step.copy(status = TrialStepStatus.RUNNING) else step
                }
            }
            is ExecutionState.Running -> {
                isRunning = true
                statusText = state.phase
                val progressStep = (state.progress * steps.size).toInt().coerceIn(0, steps.size - 1)
                currentStep = progressStep
                steps = steps.mapIndexed { i, step ->
                    when {
                        i < progressStep -> step.copy(status = TrialStepStatus.SUCCESS)
                        i == progressStep -> step.copy(status = TrialStepStatus.RUNNING)
                        else -> step.copy(status = TrialStepStatus.PENDING)
                    }
                }
            }
            is ExecutionState.Completed -> {
                isRunning = false
                statusText = "任务学习完成！"
                steps = steps.map { it.copy(status = TrialStepStatus.SUCCESS) }
            }
            is ExecutionState.Error -> {
                isRunning = false
                statusText = "执行失败：${state.message}"
                steps = steps.mapIndexed { i, step ->
                    if (i == currentStep) step.copy(status = TrialStepStatus.FAILED, detail = state.message)
                    else if (i < currentStep) step.copy(status = TrialStepStatus.SUCCESS)
                    else step
                }
            }
            is ExecutionState.Idle -> {
                isRunning = false
            }
        }
    }

    // Start execution on first composition
    LaunchedEffect(task.taskId) {
        try {
            // Check if we have a working shell before attempting execution
            val hasShell = com.smarttasker.core.direct.ShellExecutor.isAvailable()
            if (!hasShell) {
                isRunning = false
                statusText = "无可用 Shell: 请先连接 ADB 或获取 Root 权限"
                executionResult = ExecutionResult.Error("无可用 Shell")
                return@LaunchedEffect
            }

            // Build TaskSpec from TaskEntity
            val taskSpec = TaskSpec(
                taskId = task.taskId,
                name = task.name,
                description = task.description,
                targetApp = if (task.targetPackage.isNotEmpty()) {
                    TaskSpec.AppInfo(
                        name = task.targetAppName,
                        packageName = task.targetPackage,
                        confidence = 0.9f
                    )
                } else null,
                trigger = TaskSpec.TriggerConfig(
                    type = task.triggerType,
                    time = task.triggerTime,
                    repeat = task.triggerRepeat
                ),
                execution = TaskSpec.ExecutionConfig(
                    mode = task.executionMode,
                    routeEnabled = task.routeEnabled,
                    fallbackToVision = task.fallbackToVision
                ),
                risk = TaskSpec.RiskConfig(
                    level = task.riskLevel,
                    requiresConfirmation = task.requiresConfirmation,
                    reason = ""
                ),
                playbook = task.description
            )

            val result = executionService.executeQuickTask(taskSpec)
            // Capture the result for UI display
            executionResult = result
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            isRunning = false
            statusText = "执行异常: ${e.message}"
            executionResult = ExecutionResult.Error(e.message ?: "未知异常")
        }
    }

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

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Task info
            item {
                SmartCard {
                    Text(task.name, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "AI 正在学习这个任务的执行流程",
                        fontSize = 14.sp,
                        color = SmartColors.textSecondary()
                    )
                }
            }

            // Status
            item {
                SmartCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                                color = SmartColors.accent()
                            )
                        } else if (executionResult is ExecutionResult.Success) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = SmartColors.success(),
                                modifier = Modifier.size(24.dp)
                            )
                        } else if (executionResult is ExecutionResult.Failed) {
                            Icon(
                                Icons.Outlined.Error,
                                contentDescription = null,
                                tint = SmartColors.danger(),
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(
                                Icons.Outlined.Schedule,
                                contentDescription = null,
                                tint = SmartColors.textTertiary(),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            statusText,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            color = when {
                                isRunning -> SmartColors.accent()
                                executionResult is ExecutionResult.Success -> SmartColors.success()
                                executionResult is ExecutionResult.Failed -> SmartColors.danger()
                                else -> SmartColors.textTertiary()
                            }
                        )
                    }
                }
            }

            // Execution progress bar
            if (isRunning) {
                item {
                    LinearProgressIndicator(
                        progress = (executionState as? ExecutionState.Running)?.progress ?: 0f,
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = SmartColors.accent(),
                        trackColor = SmartColors.borderSubtle()
                    )
                }
            }

            // Screen preview placeholder
            item {
                SmartCard {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.PhoneAndroid,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = SmartColors.textTertiary()
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "屏幕预览",
                                fontSize = 14.sp,
                                color = SmartColors.textTertiary()
                            )
                        }
                    }
                }
            }

            // Steps timeline
            item {
                Text("执行步骤", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            itemsIndexed(steps) { _, step ->
                StepTimelineItem(step, step.index == currentStep && isRunning)
            }

            // Error detail
            if (executionResult is ExecutionResult.Failed) {
                item {
                    SmartCard {
                        Text("失败原因", fontWeight = FontWeight.Medium, fontSize = 15.sp, color = SmartColors.danger())
                        Spacer(Modifier.height(8.dp))
                        Text(
                            (executionResult as ExecutionResult.Failed).reason,
                            fontSize = 14.sp,
                            color = SmartColors.textSecondary()
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("诊断建议", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Text(
                            (executionResult as ExecutionResult.Failed).diagnosis.second,
                            fontSize = 14.sp,
                            color = SmartColors.textSecondary()
                        )
                    }
                }
            }

            // Stop button
            if (isRunning) {
                item {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            isRunning = false
                            statusText = "已停止"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = SmartColors.danger()
                        )
                    ) {
                        Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("停止试跑")
                    }
                }
            }

            // Complete button
            if (!isRunning && executionResult is ExecutionResult.Success) {
                item {
                    Spacer(Modifier.height(8.dp))
                    SmartButton(
                        text = "查看学习结果",
                        onClick = { onComplete(steps) },
                        icon = Icons.Outlined.ArrowForward
                    )
                }
            }

            // Retry button on failure
            if (!isRunning && executionResult is ExecutionResult.Failed) {
                item {
                    Spacer(Modifier.height(8.dp))
                    SmartButton(
                        text = "重试",
                        onClick = {
                            executionResult = null
                        },
                        icon = Icons.Outlined.Refresh
                    )
                }
            }
        }
    }
}

@Composable
private fun StepTimelineItem(step: TrialStep, isCurrent: Boolean) {
    val color = when (step.status) {
        TrialStepStatus.SUCCESS -> SmartColors.success()
        TrialStepStatus.FAILED -> SmartColors.danger()
        TrialStepStatus.RUNNING -> SmartColors.accent()
        TrialStepStatus.PENDING -> SmartColors.textTertiary()
        TrialStepStatus.SKIPPED -> SmartColors.textTertiary()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Status icon
        when (step.status) {
            TrialStepStatus.SUCCESS -> Icon(Icons.Outlined.CheckCircle, null, tint = color, modifier = Modifier.size(20.dp))
            TrialStepStatus.FAILED -> Icon(Icons.Outlined.Cancel, null, tint = color, modifier = Modifier.size(20.dp))
            TrialStepStatus.RUNNING -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = color)
            TrialStepStatus.PENDING -> Icon(Icons.Outlined.RadioButtonUnchecked, null, tint = color, modifier = Modifier.size(20.dp))
            TrialStepStatus.SKIPPED -> Icon(Icons.Outlined.RemoveCircleOutline, null, tint = color, modifier = Modifier.size(20.dp))
        }

        // Step number
        Text(
            "${step.index}",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = color
        )

        // Summary
        Column(modifier = Modifier.weight(1f)) {
            Text(
                step.summary,
                fontSize = 15.sp,
                fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                color = if (step.status == TrialStepStatus.PENDING) SmartColors.textTertiary() else MaterialTheme.colorScheme.onSurface
            )
            if (step.detail.isNotEmpty()) {
                Text(
                    step.detail,
                    fontSize = 12.sp,
                    color = SmartColors.danger().copy(alpha = 0.7f)
                )
            }
        }
    }
}
