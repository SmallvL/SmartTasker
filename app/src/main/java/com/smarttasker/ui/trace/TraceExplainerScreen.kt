package com.smarttasker.ui.trace

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.data.entity.RunRecordEntity
import com.smarttasker.data.entity.TraceEventEntity
import com.smarttasker.ui.theme.SmartColors
import java.text.SimpleDateFormat
import java.util.*

/**
 * Trace Explainer — Precision Instrument aesthetic
 * Dramatic failure diagnosis with timeline, terminal log, and surgical precision.
 */
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
            title = {
                Column {
                    Text("失败诊断", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("执行分析报告", fontSize = 12.sp, color = SmartColors.textTertiary())
                }
            },
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
            // ── Dramatic failure header with red gradient ──
            item {
                FailureHeader(run = run)
            }

            // ── Cause Analysis — amber left border ──
            item {
                CauseAnalysisCard(run = run, failedStep = failedStep)
            }

            // ── Fix Suggestion — green left border, lightbulb ──
            item {
                FixSuggestionCard(run = run)
            }

            // ── Execution timeline with connector lines ──
            if (stepEvents.isNotEmpty()) {
                item {
                    Text(
                        "执行时间线",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                items(stepEvents.entries.toList()) { (stepId, events) ->
                    TimelineStepCard(
                        stepId = stepId,
                        events = events,
                        dateFormat = dateFormat
                    )
                }
            }

            // ── Fix in Route Studio button ──
            item {
                GradientFixButton(onClick = onOpenRouteStudio)
            }

            // ── Tech log toggle ──
            item {
                OutlinedButton(
                    onClick = { showTechLog = !showTechLog },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        if (showTechLog) Icons.Outlined.ExpandLess else Icons.Outlined.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (showTechLog) "隐藏技术日志" else "查看技术日志")
                }
            }

            // ── Tech log — terminal style ──
            if (showTechLog) {
                item {
                    TerminalLogCard(
                        traceEvents = traceEvents,
                        dateFormat = dateFormat
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
// Failure Header — Red gradient background with pulse animation
// ══════════════════════════════════════════════════════════════════

@Composable
private fun FailureHeader(run: RunRecordEntity) {
    val dangerColor = SmartColors.danger()
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    // Pulse animation for error icon
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            dangerColor.copy(alpha = 0.15f),
                            dangerColor.copy(alpha = 0.03f)
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier.padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pulsing error icon
                Box(contentAlignment = Alignment.Center) {
                    // Glow ring
                    Surface(
                        shape = CircleShape,
                        color = dangerColor.copy(alpha = pulseAlpha * 0.15f),
                        modifier = Modifier
                            .size(72.dp)
                            .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
                    ) {}
                    // Icon circle
                    Surface(
                        shape = CircleShape,
                        color = dangerColor.copy(alpha = 0.12f),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.Error,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = dangerColor
                            )
                        }
                    }
                }

                Column {
                    Text(
                        "任务执行失败",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = dangerColor
                    )
                    if (run.failedStepId != null) {
                        Text(
                            "失败在第 ${run.failedStepId} 步",
                            fontSize = 14.sp,
                            color = SmartColors.textSecondary()
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${run.durationMs}ms",
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = SmartColors.textTertiary()
                        )
                        Text("·", fontSize = 13.sp, color = SmartColors.textTertiary())
                        Text(
                            dateFormat.format(Date(run.startedAt)),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            color = SmartColors.textTertiary()
                        )
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
// Cause Analysis Card — Amber left border
// ══════════════════════════════════════════════════════════════════

@Composable
private fun CauseAnalysisCard(run: RunRecordEntity, failedStep: TraceEventEntity?) {
    val warningColor = SmartColors.warning()

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp)
        ) {
            // Amber left border
            Surface(
                modifier = Modifier
                    .width(4.dp)
                    .height(IntrinsicSize.Max),
                shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
                color = warningColor
            ) {}

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = warningColor.copy(alpha = 0.12f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = warningColor
                            )
                        }
                    }
                    Text(
                        "原因分析",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Spacer(Modifier.height(12.dp))

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
                    lineHeight = 24.sp,
                    color = SmartColors.textSecondary()
                )

                // Failed step details
                if (failedStep != null) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SmartColors.danger().copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, SmartColors.danger().copy(alpha = 0.15f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "失败步骤详情",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = SmartColors.danger()
                            )
                            Spacer(Modifier.height(6.dp))
                            TerminalDetailRow("步骤 ID", failedStep.stepId)
                            TerminalDetailRow("操作类型", failedStep.stepType)
                            TerminalDetailRow("目标", failedStep.stepTarget)
                            if (failedStep.message.isNotEmpty()) {
                                TerminalDetailRow("错误信息", failedStep.message)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
// Fix Suggestion Card — Green left border, lightbulb icon
// ══════════════════════════════════════════════════════════════════

@Composable
private fun FixSuggestionCard(run: RunRecordEntity) {
    val accentColor = SmartColors.accent()

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp)
        ) {
            // Green left border
            Surface(
                modifier = Modifier
                    .width(4.dp)
                    .height(IntrinsicSize.Max),
                shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
                color = accentColor
            ) {}

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Lightbulb icon
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.12f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Lightbulb,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = accentColor
                        )
                    }
                }

                Column {
                    Text(
                        "修复建议",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
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
}

// ══════════════════════════════════════════════════════════════════
// Timeline Step Card — Vertical connector lines and status dots
// ══════════════════════════════════════════════════════════════════

@Composable
private fun TimelineStepCard(
    stepId: String,
    events: List<TraceEventEntity>,
    dateFormat: SimpleDateFormat
) {
    val stepStart = events.find { it.eventType == "step_start" }
    val stepEnd = events.find { it.eventType == "step_end" }
    val isFailed = events.any { it.stepResult == "failed" }
    val isSuccess = events.any { it.stepResult == "success" }

    val statusColor = when {
        isFailed -> SmartColors.danger()
        isSuccess -> SmartColors.success()
        else -> SmartColors.textTertiary()
    }
    val statusIcon = when {
        isFailed -> Icons.Outlined.Error
        isSuccess -> Icons.Outlined.CheckCircle
        else -> Icons.Outlined.Schedule
    }
    val connectorColor = SmartColors.borderSubtle()

    Row(modifier = Modifier.fillMaxWidth()) {
        // ── Left: Timeline connector ──
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp)
        ) {
            // Top connector
            Canvas(modifier = Modifier.width(2.dp).height(8.dp)) {
                drawLine(
                    color = connectorColor,
                    start = Offset(size.width / 2f, 0f),
                    end = Offset(size.width / 2f, size.height),
                    strokeWidth = 2f
                )
            }

            // Status dot
            Surface(
                shape = CircleShape,
                color = statusColor.copy(alpha = 0.15f),
                border = BorderStroke(2.dp, statusColor),
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        statusIcon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = statusColor
                    )
                }
            }

            // Bottom connector
            Canvas(modifier = Modifier.width(2.dp).weight(1f)) {
                drawLine(
                    color = connectorColor,
                    start = Offset(size.width / 2f, 0f),
                    end = Offset(size.width / 2f, size.height),
                    strokeWidth = 2f
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // ── Right: Step content ──
        Column(modifier = Modifier.weight(1f).padding(bottom = 4.dp)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = if (isFailed) SmartColors.danger().copy(alpha = 0.06f) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = if (isFailed) 1.5.dp else 1.dp,
                    color = if (isFailed) SmartColors.danger().copy(alpha = 0.3f) else SmartColors.borderSubtle()
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "步骤 $stepId",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = if (isFailed) SmartColors.danger() else MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(Modifier.weight(1f))

                        // Duration
                        if (stepStart != null && stepEnd != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SmartColors.textTertiary().copy(alpha = 0.08f)
                            ) {
                                Text(
                                    "${stepEnd.durationMs}ms",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium,
                                    color = SmartColors.textTertiary()
                                )
                            }
                        }
                    }

                    if (stepStart != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "类型: ${stepStart.stepType} · 目标: ${stepStart.stepTarget}",
                            fontSize = 12.sp,
                            color = SmartColors.textSecondary(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Error details for failed step
                    if (isFailed) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SmartColors.danger().copy(alpha = 0.08f)
                        ) {
                            Text(
                                events.filter { it.level == "error" }.joinToString("\n") { it.message },
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = SmartColors.danger(),
                                modifier = Modifier.padding(8.dp),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
// Gradient Fix Button
// ══════════════════════════════════════════════════════════════════

@Composable
private fun GradientFixButton(onClick: () -> Unit) {
    val accentColor = SmartColors.accent()

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accentColor,
            contentColor = Color.White
        )
    ) {
        Icon(Icons.Outlined.Build, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("进入 Route Studio 修复", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

// ══════════════════════════════════════════════════════════════════
// Terminal Log Card — Dark terminal background, green accent text
// ══════════════════════════════════════════════════════════════════

@Composable
private fun TerminalLogCard(
    traceEvents: List<TraceEventEntity>,
    dateFormat: SimpleDateFormat
) {
    val accentGreen = SmartColors.accent()
    val terminalBg = Color(0xFF1A1A1A)
    val connectorColor = SmartColors.borderSubtle()

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            // Terminal header bar
            Surface(
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                color = Color(0xFF2A2A2A)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Terminal dots
                    Surface(shape = CircleShape, color = Color(0xFFFF5F57), modifier = Modifier.size(10.dp)) {}
                    Surface(shape = CircleShape, color = Color(0xFFFFBD2E), modifier = Modifier.size(10.dp)) {}
                    Surface(shape = CircleShape, color = Color(0xFF28C840), modifier = Modifier.size(10.dp)) {}
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "tech-log",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF888888)
                    )
                }
            }

            // Terminal body
            Surface(
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                color = terminalBg
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (traceEvents.isEmpty()) {
                        Text(
                            "$ _ No events recorded",
                            fontSize = 12.sp,
                            color = accentGreen,
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        traceEvents.forEach { event ->
                            val lineColor = when (event.level) {
                                "error" -> SmartColors.danger()
                                "warn" -> SmartColors.warning()
                                else -> accentGreen
                            }
                            Text(
                                "[${dateFormat.format(Date(event.timestamp))}] ${event.eventType} ${event.message}",
                                fontSize = 11.sp,
                                color = lineColor,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
// Helper: Terminal-style detail row
// ══════════════════════════════════════════════════════════════════

@Composable
private fun TerminalDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "$label:",
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = SmartColors.textTertiary()
        )
        Text(
            value,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
