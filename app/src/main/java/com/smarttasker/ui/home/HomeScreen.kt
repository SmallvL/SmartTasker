package com.smarttasker.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.core.bridge.CoreBridgeManager
import com.smarttasker.core.bridge.CoreStatus
import com.smarttasker.data.entity.RunRecordEntity
import com.smarttasker.data.entity.TaskEntity
import com.smarttasker.data.repository.TaskRepository
import com.smarttasker.data.repository.RunRepository
import com.smarttasker.service.ExecutionState
import com.smarttasker.service.TaskExecutionService
import com.smarttasker.ui.common.*
import com.smarttasker.ui.theme.SmartColors

@Composable
fun HomeScreen(
    taskRepo: TaskRepository,
    runRepo: RunRepository,
    coreBridgeManager: CoreBridgeManager,
    executionService: TaskExecutionService? = null,
    onCreateTask: (String) -> Unit = {},
    onAiExecute: (String, String) -> Unit = { _, _ -> },
    onTaskClick: (TaskEntity) -> Unit = {},
    onNavigateToTaskList: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToTrace: (String) -> Unit = {},
    onDeleteTask: (TaskEntity) -> Unit = {},
    onToggleTask: (TaskEntity) -> Unit = {}
) {
    val recentTasks by taskRepo.getRecentTasks(5).collectAsState(initial = emptyList())
    val activeCount by taskRepo.getActiveTaskCount().collectAsState(initial = 0)
    val successCount by runRepo.getTodaySuccessCount().collectAsState(initial = 0)
    val failedCount by runRepo.getTodayFailedCount().collectAsState(initial = 0)
    val modelCalls by runRepo.getTodayModelCalls().collectAsState(initial = 0)
    val failedRuns by runRepo.getRecentFailedRuns(3).collectAsState(initial = emptyList())

    // Execution state for real-time feedback
    val executionState by executionService?.executionState?.collectAsState() ?: remember { mutableStateOf(ExecutionState.Idle) }

    // CoreBridge real status
    val coreStatus by coreBridgeManager.coreStatus.collectAsState()
    val isChecking by coreBridgeManager.isChecking.collectAsState()

    var taskInput by remember { mutableStateOf("") }
    var selectedAppName by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Hero greeting section ──
        item {
            HeroGreeting(coreStatus = coreStatus, isChecking = isChecking)
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

        // ── Execution status card ──
        if (executionState is ExecutionState.Running || executionState is ExecutionState.Submitting) {
            item {
                ExecutionStatusCard(executionState = executionState)
            }
        }

        // ── Task creation card ──
        item {
            TaskCreationCard(
                taskInput = taskInput,
                onTaskInputChange = { taskInput = it },
                selectedAppName = selectedAppName,
                onSelectedAppChange = { selectedAppName = it },
                onCreateTask = { onCreateTask(taskInput) },
                onAiExecute = { onAiExecute(taskInput, selectedAppName) }
            )
        }

        // ── Today summary ──
        item {
            TodaySummaryGrid(
                successCount = successCount,
                failedCount = failedCount,
                modelCalls = modelCalls ?: 0,
                activeCount = activeCount
            )
        }

        // ── Failed runs timeline ──
        if (failedRuns.isNotEmpty()) {
            item { SectionHeader("需要处理") }
            items(failedRuns) { run ->
                FailedRunTimelineItem(
                    run = run,
                    onClick = { onNavigateToTrace(run.runId) }
                )
            }
        }

        // ── Recent tasks ──
        item { SectionHeader("最近任务", action = "查看全部", onAction = onNavigateToTaskList) }
        if (recentTasks.isEmpty()) {
            item {
                EmptyState(icon = Icons.Outlined.CheckCircle, title = "还没有任务", subtitle = "在上方输入你想自动完成的事情")
            }
        } else {
            items(recentTasks, key = { it.taskId }) { task ->
                TaskMiniCard(
                    task = task,
                    onClick = { onTaskClick(task) },
                    onDelete = { onDeleteTask(task) },
                    onToggle = { onToggleTask(task) }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Hero greeting with gradient accent & integrated Core status
// ═══════════════════════════════════════════════════════════════

@Composable
private fun HeroGreeting(coreStatus: CoreStatus, isChecking: Boolean) {
    val accent = SmartColors.accent()

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gradient-accented "SmartTask" title
            Text(
                "SmartTask",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Draw a subtle gradient underline behind the text
                modifier = Modifier.drawBehind {
                    val strokeWidth = 3.dp.toPx()
                    val y = size.height + 4.dp.toPx()
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(accent, accent.copy(alpha = 0.1f))
                        ),
                        start = Offset(0f, y),
                        end = Offset(size.width * 0.6f, y),
                        strokeWidth = strokeWidth
                    )
                }
            )
            if (isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = SmartColors.textTertiary()
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Core status pill (redesigned)
            CoreStatusPill(coreStatus)
            // Permission status
            StatusPill("权限正常", SmartColors.success())
        }
        Spacer(Modifier.height(8.dp))
        CapabilityBadges(coreStatus)
    }
}

// ═══════════════════════════════════════════════════════════════
// Task creation card with gradient accent border
// ═══════════════════════════════════════════════════════════════

@Composable
private fun TaskCreationCard(
    taskInput: String,
    onTaskInputChange: (String) -> Unit,
    selectedAppName: String,
    onSelectedAppChange: (String) -> Unit,
    onCreateTask: () -> Unit,
    onAiExecute: () -> Unit
) {
    val accent = SmartColors.accent()

    // Gradient border wrapper
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(accent, accent.copy(alpha = 0.4f), accent.copy(alpha = 0.1f))
                )
            )
            .padding(1.5.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(23.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "你想自动完成什么？",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp
                )
                Spacer(Modifier.height(14.dp))
                SmartInputField(
                    value = taskInput,
                    onValueChange = onTaskInputChange,
                    placeholder = "每天早上9点打开淘宝收金币",
                    singleLine = false
                )

                // App picker row
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppPickerChip(onAppSelected = onSelectedAppChange)
                    // Selected app chip (clickable to clear)
                    if (selectedAppName.isNotBlank()) {
                        Surface(
                            onClick = { onSelectedAppChange("") },
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF8B5CF6).copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = Color(0xFF8B5CF6)
                                )
                                Text(
                                    selectedAppName,
                                    fontSize = 13.sp,
                                    color = Color(0xFF8B5CF6),
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = "清除",
                                    modifier = Modifier.size(12.dp),
                                    tint = Color(0xFF8B5CF6).copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Two buttons: AI Execute (gradient) + Create Task (original)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // AI Execute button — gradient purple, more prominent
                    val aiEnabled = taskInput.isNotBlank()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (aiEnabled) Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF8B5CF6), Color(0xFF6366F1))
                                ) else Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF8B5CF6).copy(alpha = 0.3f), Color(0xFF6366F1).copy(alpha = 0.3f))
                                )
                            )
                            .clickable(enabled = aiEnabled, onClick = onAiExecute),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color.White.copy(alpha = if (aiEnabled) 1f else 0.5f)
                            )
                            Text(
                                "AI 执行",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = Color.White.copy(alpha = if (aiEnabled) 1f else 0.5f)
                            )
                        }
                    }

                    // Create Task button — original style
                    Button(
                        onClick = onCreateTask,
                        enabled = taskInput.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = Color.White,
                            disabledContainerColor = accent.copy(alpha = 0.3f),
                            disabledContentColor = Color.White.copy(alpha = 0.5f)
                        )
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("创建任务", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Today summary – 2x2 grid of stat cards
// ═══════════════════════════════════════════════════════════════

@Composable
private fun TodaySummaryGrid(
    successCount: Int,
    failedCount: Int,
    modelCalls: Int,
    activeCount: Int
) {
    Column {
        Text("今日概览", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Spacer(Modifier.height(12.dp))
        // Row 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = { Icon(Icons.Outlined.CheckCircle, null, tint = SmartColors.success(), modifier = Modifier.size(16.dp)) },
                value = successCount.toString(),
                label = "成功",
                iconBg = SmartColors.success(),
                trend = if (successCount > 0) "+${successCount}" else null
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = { Icon(Icons.Outlined.ErrorOutline, null, tint = SmartColors.danger(), modifier = Modifier.size(16.dp)) },
                value = failedCount.toString(),
                label = "失败",
                iconBg = SmartColors.danger(),
                trend = if (failedCount > 0) "${failedCount}次" else null
            )
        }
        Spacer(Modifier.height(10.dp))
        // Row 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = { Icon(Icons.Outlined.Psychology, null, tint = SmartColors.accent(), modifier = Modifier.size(16.dp)) },
                value = modelCalls.toString(),
                label = "AI 调用",
                iconBg = SmartColors.accent(),
                trend = null
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = { Icon(Icons.Outlined.TaskAlt, null, tint = SmartColors.warning(), modifier = Modifier.size(16.dp)) },
                value = activeCount.toString(),
                label = "任务",
                iconBg = SmartColors.warning(),
                trend = if (activeCount > 0) "运行中" else null
            )
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    value: String,
    label: String,
    iconBg: Color,
    trend: String?
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Colored icon background circle
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconBg.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    icon()
                }
                // Trend indicator
                if (trend != null) {
                    Text(
                        trend,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = iconBg.copy(alpha = 0.7f)
                    )
                }
            }
            Text(
                value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 32.sp
            )
            Text(
                label,
                fontSize = 12.sp,
                color = SmartColors.textTertiary()
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Failed runs – timeline style with red accent
// ═══════════════════════════════════════════════════════════════

@Composable
private fun FailedRunTimelineItem(
    run: RunRecordEntity,
    onClick: () -> Unit
) {
    val danger = SmartColors.danger()

    Row(modifier = Modifier.fillMaxWidth()) {
        // Timeline rail
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(32.dp)
        ) {
            // Red dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(danger)
            )
            // Vertical line
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(danger.copy(alpha = 0.2f))
            )
        }
        // Content card
        SmartCard(onClick = onClick) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        run.diagnosisSummary.ifEmpty { "任务执行失败" },
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    )
                    if (run.diagnosisSuggestion.isNotEmpty()) {
                        Text(
                            run.diagnosisSuggestion,
                            fontSize = 13.sp,
                            color = SmartColors.textSecondary(),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                StatusPill("失败", SmartColors.danger())
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Execution status – animated progress card with gradient bar
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ExecutionStatusCard(executionState: ExecutionState) {
    val accent = SmartColors.accent()

    // Infinite shimmer for the progress bar
    val infiniteTransition = rememberInfiniteTransition(label = "execShimmer")
    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer"
    )

    SmartCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 3.dp,
                color = accent
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when (executionState) {
                        is ExecutionState.Submitting -> "正在提交任务..."
                        is ExecutionState.Running -> "任务执行中"
                        else -> ""
                    },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                if (executionState is ExecutionState.Running) {
                    val running = executionState as ExecutionState.Running
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when (running.phase) {
                            "analyzing" -> "🔍 分析任务..."
                            "launching" -> "🚀 启动应用..."
                            "navigating" -> "🧭 导航中..."
                            "interacting" -> "👆 操作中..."
                            "verifying" -> "✅ 验证结果..."
                            else -> "⏳ ${running.phase}"
                        },
                        fontSize = 13.sp,
                        color = SmartColors.textSecondary()
                    )
                    Spacer(Modifier.height(10.dp))
                    // Gradient progress bar
                    val progress by animateFloatAsState(
                        targetValue = running.progress,
                        animationSpec = tween(300),
                        label = "execProgress"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(accent.copy(alpha = 0.12f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            accent,
                                            accent.copy(alpha = 0.6f + shimmerProgress * 0.4f)
                                        )
                                    )
                                )
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${(progress * 100).toInt()}%",
                        fontSize = 11.sp,
                        color = SmartColors.textTertiary(),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// CoreStatusPill – redesigned as subtle indicator
// ═══════════════════════════════════════════════════════════════

@Composable
private fun CoreStatusPill(status: CoreStatus) {
    val (text, color) = when (status) {
        is CoreStatus.Running -> "Core 运行中" to SmartColors.success()
        is CoreStatus.ShellOnly -> "基础模式" to SmartColors.warning()
        is CoreStatus.Stopped -> "Core 未运行" to SmartColors.warning()
        is CoreStatus.Error -> "连接异常" to SmartColors.danger()
        is CoreStatus.Unknown -> "检查中..." to SmartColors.textTertiary()
    }

    // Subtle pill with leading dot indicator
    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(400),
        label = "corePillColor"
    )

    Surface(
        shape = RoundedCornerShape(100),
        color = animatedColor.copy(alpha = 0.10f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Pulsing dot
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(animatedColor)
            )
            Text(
                text,
                color = animatedColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// CapabilityBadges – redesigned
// ═══════════════════════════════════════════════════════════════

@Composable
private fun CapabilityBadges(status: CoreStatus) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // Execution capability
        CapabilityBadge(
            label = "路线执行",
            enabled = status.canExecute
        )
        // Recording capability
        CapabilityBadge(
            label = "录制",
            enabled = status.canRecord
        )
    }
}

@Composable
private fun CapabilityBadge(label: String, enabled: Boolean) {
    val color = if (enabled) SmartColors.success() else SmartColors.warning()
    Surface(
        shape = RoundedCornerShape(100),
        color = color.copy(alpha = 0.10f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                if (enabled) "✓" else "⚠",
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                label,
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// SummaryItem – kept for compatibility (unused in new grid)
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SummaryItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = color)
        Text(label, fontSize = 12.sp, color = SmartColors.textTertiary())
    }
}

// ═══════════════════════════════════════════════════════════════
// TaskMiniCard – redesigned with icon letter, status dot, trigger badge
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskMiniCard(
    task: TaskEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit = {},
    onToggle: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    // Status dot color
    val statusDotColor = when (task.status) {
        "active" -> SmartColors.success()
        "paused" -> SmartColors.warning()
        else -> SmartColors.textTertiary()
    }

    // App icon letter background color based on status
    val iconBgColor = when (task.status) {
        "active" -> SmartColors.accent()
        "paused" -> SmartColors.warning()
        else -> SmartColors.textTertiary()
    }

    SmartCard(
        onClick = onClick,
        onLongClick = { showMenu = true }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App icon letter with status dot
            Box {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBgColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = task.targetAppName.ifEmpty { task.name.take(1) }.take(1).uppercase(),
                        color = iconBgColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
                // Status indicator dot (overlapping bottom-right)
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusDotColor)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .align(Alignment.BottomEnd)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(task.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Trigger type badge
                    val (triggerIcon, triggerText, triggerColor) = when (task.triggerType) {
                        "schedule" -> Triple("⏰", task.triggerTime, SmartColors.accent())
                        "notification" -> Triple("🔔", "通知触发", SmartColors.warning())
                        else -> Triple("▶", "手动", SmartColors.textTertiary())
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = triggerColor.copy(alpha = 0.10f)
                    ) {
                        Text(
                            "$triggerIcon $triggerText",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = triggerColor
                        )
                    }

                    // Repeat info
                    if (task.triggerType == "schedule") {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SmartColors.textTertiary().copy(alpha = 0.08f)
                        ) {
                            Text(
                                when (task.triggerRepeat) {
                                    "daily" -> "每天"
                                    "weekly" -> "每周"
                                    else -> "一次"
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                color = SmartColors.textTertiary()
                            )
                        }
                    }

                    // Risk level
                    if (task.riskLevel != "low") {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when (task.riskLevel) {
                                "high" -> SmartColors.warning().copy(alpha = 0.10f)
                                "critical" -> SmartColors.danger().copy(alpha = 0.10f)
                                else -> Color.Transparent
                            }
                        ) {
                            Text(
                                when (task.riskLevel) {
                                    "high" -> "⚠ 高风险"
                                    "critical" -> "🚫 禁止"
                                    else -> ""
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = when (task.riskLevel) {
                                    "high" -> SmartColors.warning()
                                    "critical" -> SmartColors.danger()
                                    else -> SmartColors.textTertiary()
                                }
                            )
                        }
                    }
                }
            }

            // Status text
            Text(
                when (task.status) {
                    "active" -> "运行中"
                    "paused" -> "已暂停"
                    "draft" -> "草稿"
                    else -> task.status
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = statusDotColor
            )
        }

        // Context menu
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(if (task.status == "active") "暂停" else "启用") },
                onClick = {
                    showMenu = false
                    onToggle()
                },
                leadingIcon = {
                    Icon(
                        if (task.status == "active") Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                }
            )
            DropdownMenuItem(
                text = { Text("删除", color = SmartColors.danger()) },
                onClick = {
                    showMenu = false
                    onDelete()
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = SmartColors.danger()
                    )
                }
            )
        }
    }
}
