package com.smarttasker.ui.trialrun

import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.core.bridge.CoreBridgeManager
import com.smarttasker.core.bridge.ScreenshotResult
import com.smarttasker.data.entity.TaskEntity
import com.smarttasker.service.ExecutionState
import com.smarttasker.service.TaskExecutionService
import com.smarttasker.ui.common.*
import com.smarttasker.ui.theme.SmartColors
import kotlinx.coroutines.delay

private val PurpleGradient = listOf(Color(0xFF8B5CF6), Color(0xFF6366F1))

data class AiExecutionStep(
    val index: Int,
    val summary: String,
    val isSuccess: Boolean = true
)

@Composable
fun AiExecutionScreen(
    task: TaskEntity,
    executionService: TaskExecutionService,
    onComplete: (success: Boolean, routeId: String?) -> Unit,
    onCancel: () -> Unit
) {
    val executionState by executionService.executionState.collectAsState()

    // Track executed steps
    var steps by remember { mutableStateOf(listOf<AiExecutionStep>()) }
    var visionTurns by remember { mutableStateOf(0) }
    var isCompleted by remember { mutableStateOf(false) }

    // Screenshot state
    var screenshotBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    // Get bridge for screenshots via CoreBridgeManager
    val context = LocalContext.current
    val bridge = remember { CoreBridgeManager.getInstance(context).bridge }

    // ── Start execution ──
    LaunchedEffect(task.taskId) {
        try {
            val hasShell = com.smarttasker.core.direct.ShellExecutor.isAvailable()
            if (!hasShell) {
                onComplete(false, null)
                return@LaunchedEffect
            }

            val taskSpec = com.smarttasker.core.parser.TaskSpec(
                taskId = task.taskId,
                name = task.name,
                description = task.description,
                targetApp = if (task.targetPackage.isNotEmpty() || task.targetAppName.isNotEmpty()) {
                    com.smarttasker.core.parser.TaskSpec.AppInfo(
                        name = task.targetAppName,
                        packageName = task.targetPackage,
                        confidence = 0.9f
                    )
                } else null,
                trigger = com.smarttasker.core.parser.TaskSpec.TriggerConfig(
                    type = task.triggerType,
                    time = task.triggerTime,
                    repeat = task.triggerRepeat
                ),
                execution = com.smarttasker.core.parser.TaskSpec.ExecutionConfig(
                    mode = task.executionMode,
                    routeEnabled = task.routeEnabled,
                    fallbackToVision = task.fallbackToVision
                ),
                risk = com.smarttasker.core.parser.TaskSpec.RiskConfig(
                    level = task.riskLevel,
                    requiresConfirmation = task.requiresConfirmation,
                    reason = ""
                ),
                playbook = task.description
            )

            executionService.executeQuickTask(taskSpec)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            onComplete(false, null)
        }
    }

    // ── State monitoring ──
    LaunchedEffect(executionState) {
        when (val state = executionState) {
            is ExecutionState.Running -> {
                visionTurns = state.stepCount
                // Update steps list
                if (state.stepCount > steps.size) {
                    val newSteps = (steps.size until state.stepCount).map { i ->
                        AiExecutionStep(
                            index = i + 1,
                            summary = if (i == state.stepCount - 1) state.currentStepSummary else "步骤 ${i + 1}",
                            isSuccess = true
                        )
                    }
                    steps = steps + newSteps
                }
                // Update last step summary
                if (steps.isNotEmpty() && state.currentStepSummary.isNotBlank()) {
                    steps = steps.toMutableList().apply {
                        this[lastIndex] = this[lastIndex].copy(summary = state.currentStepSummary)
                    }
                }
            }
            is ExecutionState.AutoSaved -> {
                isCompleted = true
                delay(2000)
                onComplete(true, state.routeId)
            }
            is ExecutionState.Completed -> {
                isCompleted = true
                delay(2000)
                onComplete(true, null)
            }
            is ExecutionState.Error -> {
                isCompleted = true
            }
            else -> {}
        }
    }

    // ── Screenshot refresh every 2 seconds ──
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val currentBridge = bridge
                if (currentBridge != null) {
                    val result = currentBridge.screenshot()
                    if (result is ScreenshotResult.Success) {
                        screenshotBitmap = BitmapFactory.decodeByteArray(result.pngBytes, 0, result.pngBytes.size)
                    }
                }
            } catch (_: Exception) {
            }
            delay(2000)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Top fixed area: progress bar + stop button ──
        Surface(
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "第 $visionTurns 轮 / 30",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    // Stop button (red)
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(12),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = SmartColors.danger()
                        ),
                        border = BorderStroke(1.dp, SmartColors.danger().copy(alpha = 0.4f)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("停止", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = (visionTurns.toFloat() / 30f).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = SmartColors.accent(),
                    trackColor = SmartColors.borderSubtle()
                )
            }
        }

        Divider(color = SmartColors.borderSubtle().copy(alpha = 0.5f))

        // ── Scrollable content ──
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Screenshot area ──
            item {
                SmartCard {
                    Text(
                        "实时屏幕",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = SmartColors.textSecondary()
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (screenshotBitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = screenshotBitmap!!.asImageBitmap(),
                                contentDescription = "屏幕截图",
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                            )
                        } else {
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
            }

            // ── AI thinking process card (purple gradient) ──
            item {
                AiThinkingCard(executionState)
            }

            // ── Step timeline ──
            if (steps.isNotEmpty()) {
                item {
                    Text(
                        "执行步骤",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            itemsIndexed(steps) { index, step ->
                AiStepTimelineItem(step, isLast = index == steps.lastIndex)
            }

            // ── Completion states ──
            when (val state = executionState) {
                is ExecutionState.AutoSaved -> {
                    item {
                        CompletionCard(
                            isSuccess = true,
                            title = "任务完成",
                            subtitle = "路线已自动保存（${state.stepCount}步）"
                        )
                    }
                }
                is ExecutionState.Completed -> {
                    item {
                        CompletionCard(
                            isSuccess = true,
                            title = "任务完成",
                            subtitle = "执行成功"
                        )
                    }
                }
                is ExecutionState.Error -> {
                    item {
                        CompletionCard(
                            isSuccess = false,
                            title = "执行失败",
                            subtitle = state.message
                        )
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SmartButton(
                                text = "重试",
                                onClick = {
                                    // Reset state and retry - caller handles this
                                    onComplete(false, null)
                                },
                                icon = Icons.Outlined.Refresh,
                                modifier = Modifier.weight(1f)
                            )
                            SmartSecondaryButton(
                                text = "放弃",
                                onClick = onCancel,
                                icon = Icons.Outlined.Close,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

// ── AI Thinking Card with purple gradient background ──
@Composable
private fun AiThinkingCard(executionState: ExecutionState) {
    val runningState = executionState as? ExecutionState.Running
    val aiThinking = runningState?.aiThinking.orEmpty()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box {
            // Purple gradient background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            colors = PurpleGradient.map { it.copy(alpha = 0.12f) }
                        ),
                        shape = RoundedCornerShape(20)
                    )
            )
            Column(modifier = Modifier.padding(20.dp)) {
                // Title row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.Psychology,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = PurpleGradient.first()
                    )
                    Text(
                        "AI 思考过程",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = PurpleGradient.first()
                    )
                }
                Spacer(Modifier.height(12.dp))

                if (aiThinking.isBlank()) {
                    Text(
                        "AI 正在分析...",
                        fontSize = 13.sp,
                        color = SmartColors.textTertiary(),
                        lineHeight = 18.sp
                    )
                } else {
                    // Parse and display observation/thinking/action/expectation
                    val lines = aiThinking.split("\n").filter { it.isNotBlank() }
                    for (line in lines) {
                        val (label, content, labelColor) = when {
                            line.trimStart().startsWith("观察") || line.trimStart().startsWith("Observe") ->
                                Triple("观察", line.removePrefix("观察").removePrefix("：").removePrefix(":").trim(), Color(0xFF3B82F6))
                            line.trimStart().startsWith("思考") || line.trimStart().startsWith("Think") ->
                                Triple("思考", line.removePrefix("思考").removePrefix("：").removePrefix(":").trim(), PurpleGradient.first())
                            line.trimStart().startsWith("行动") || line.trimStart().startsWith("Action") ->
                                Triple("行动", line.removePrefix("行动").removePrefix("：").removePrefix(":").trim(), SmartColors.accent())
                            line.trimStart().startsWith("预期") || line.trimStart().startsWith("Expect") ->
                                Triple("预期", line.removePrefix("预期").removePrefix("：").removePrefix(":").trim(), SmartColors.warning())
                            else -> Triple(null, line, SmartColors.textSecondary())
                        }

                        if (label != null && content.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4),
                                    color = labelColor.copy(alpha = 0.1f)
                                ) {
                                    Text(
                                        label,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = labelColor
                                    )
                                }
                                Text(
                                    content,
                                    fontSize = 13.sp,
                                    color = SmartColors.textSecondary(),
                                    lineHeight = 18.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            Text(
                                content,
                                fontSize = 13.sp,
                                color = SmartColors.textSecondary(),
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Step timeline item (same style as RouteLearningResultScreen) ──
@Composable
private fun AiStepTimelineItem(step: AiExecutionStep, isLast: Boolean) {
    val stepColor = if (step.isSuccess) SmartColors.success() else SmartColors.danger()
    val connectorColor = SmartColors.borderSubtle()

    Row(modifier = Modifier.fillMaxWidth()) {
        // Left: Timeline connector
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
                color = stepColor.copy(alpha = 0.15f),
                border = BorderStroke(2.dp, stepColor),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (step.isSuccess) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = stepColor
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = stepColor
                        )
                    }
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

        // Right: Step content card
        Column(modifier = Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 4.dp)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, SmartColors.borderSubtle())
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Step index badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = stepColor.copy(alpha = 0.1f)
                    ) {
                        Text(
                            "#${step.index}",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = stepColor
                        )
                    }
                    // Step summary
                    Text(
                        step.summary.ifEmpty { "步骤 ${step.index}" },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    // Status icon
                    if (step.isSuccess) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = "成功",
                            modifier = Modifier.size(18.dp),
                            tint = SmartColors.success()
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Cancel,
                            contentDescription = "失败",
                            modifier = Modifier.size(18.dp),
                            tint = SmartColors.danger()
                        )
                    }
                }
            }
        }
    }
}

// ── Completion card ──
@Composable
private fun CompletionCard(
    isSuccess: Boolean,
    title: String,
    subtitle: String
) {
    // Animated scale for checkmark
    val infiniteTransition = rememberInfiniteTransition(label = "completionPulse")
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconScale"
    )

    val color = if (isSuccess) SmartColors.success() else SmartColors.danger()
    val icon = if (isSuccess) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel

    Surface(
        shape = RoundedCornerShape(20),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                color.copy(alpha = 0.15f),
                                color.copy(alpha = 0.05f)
                            )
                        ),
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    )
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = color.copy(alpha = 0.15f),
                        modifier = Modifier.size((72 * iconScale).dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                icon,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = color
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        subtitle,
                        fontSize = 14.sp,
                        color = SmartColors.textSecondary()
                    )
                }
            }
        }
    }
}
