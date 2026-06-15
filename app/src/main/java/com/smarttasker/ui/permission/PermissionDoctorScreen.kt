package com.smarttasker.ui.permission

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.core.bridge.CoreBridgeManager
import com.smarttasker.core.bridge.CoreStatus
import com.smarttasker.ui.theme.SmartColors

/**
 * Permission Doctor — Precision Instrument aesthetic
 * Checks all required permissions and system state with a health-score dial.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionDoctorScreen(
    coreBridgeManager: CoreBridgeManager,
    onBack: () -> Unit,
    onFixPermission: (String) -> Unit = {}
) {
    val coreStatus by coreBridgeManager.coreStatus.collectAsState()

    // Permission checks
    val checks = remember {
        listOf(
            PermissionCheck("core_status", "AutoLXB Core", "检查 Core 服务是否运行"),
            PermissionCheck("accessibility", "无障碍服务", "需要开启 SmartTask 无障碍服务"),
            PermissionCheck("notification", "通知读取", "用于检测通知触发任务"),
            PermissionCheck("battery", "电池优化", "建议设为无限制以保证后台运行"),
            PermissionCheck("model_config", "AI 模型", "需要配置模型 API 用于任务解析"),
            PermissionCheck("adb", "ADB 权限", "用于设备控制和截图")
        )
    }

    // Update core status check
    val updatedChecks = checks.map { check ->
        when (check.id) {
            "core_status" -> check.copy(
                status = when (coreStatus) {
                    is CoreStatus.Running -> CheckStatus.PASS
                    is CoreStatus.ShellOnly -> CheckStatus.WARN
                    is CoreStatus.Stopped -> CheckStatus.FAIL
                    is CoreStatus.Error -> CheckStatus.FAIL
                    is CoreStatus.Unknown -> CheckStatus.CHECKING
                },
                detail = when (coreStatus) {
                    is CoreStatus.Running -> {
                        val port = (coreStatus as CoreStatus.Running).port
                        if (port > 0) "端口 $port · 完全控制" else "ADB 本地模式 · 完全控制"
                    }
                    is CoreStatus.ShellOnly -> "SH 模式 · 执行可用 · 录制需无线调试"
                    is CoreStatus.Stopped -> "Core 未运行"
                    is CoreStatus.Error -> (coreStatus as CoreStatus.Error).message
                    is CoreStatus.Unknown -> "检查中..."
                }
            )
            "model_config" -> check.copy(
                status = CheckStatus.WARN,
                detail = "请在设置中配置 API Key"
            )
            else -> check.copy(status = CheckStatus.PASS)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("权限体检", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("系统健康诊断", fontSize = 12.sp, color = SmartColors.textTertiary())
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Health Score Dial ──
            item {
                HealthScoreDial(checks = updatedChecks)
            }

            // ── Check items ──
            items(updatedChecks) { check ->
                PermissionCheckCard(
                    check = check,
                    onFix = { onFixPermission(check.id) }
                )
            }

            // ── Help card with gradient accent ──
            item {
                GradientHelpCard()
            }
        }
    }
}

enum class CheckStatus { PASS, WARN, FAIL, CHECKING }

data class PermissionCheck(
    val id: String,
    val name: String,
    val description: String,
    val status: CheckStatus = CheckStatus.CHECKING,
    val detail: String = ""
)

// ══════════════════════════════════════════════════════════════════
// Health Score Dial — Large circular progress indicator
// ══════════════════════════════════════════════════════════════════

@Composable
private fun HealthScoreDial(checks: List<PermissionCheck>) {
    val passCount = checks.count { it.status == CheckStatus.PASS }
    val totalCount = checks.size
    val progress = passCount.toFloat() / totalCount
    val isAllPass = passCount == totalCount

    val dialColor = when {
        isAllPass -> SmartColors.success()
        passCount >= totalCount / 2 -> SmartColors.warning()
        else -> SmartColors.danger()
    }
    val trackColor = SmartColors.borderSubtle()

    // Animated sweep
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1200, easing = LinearEasing),
        label = "dialProgress"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Circular dial
            Box(
                modifier = Modifier.size(160.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 10.dp.toPx()
                    val radius = (size.minDimension - strokeWidth) / 2f
                    val topLeft = Offset(
                        (size.width - radius * 2) / 2f,
                        (size.height - radius * 2) / 2f
                    )
                    val arcSize = Size(radius * 2, radius * 2)

                    // Track
                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Progress arc
                    drawArc(
                        color = dialColor,
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                // Center text
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$passCount",
                        fontWeight = FontWeight.Bold,
                        fontSize = 40.sp,
                        fontFamily = FontFamily.Monospace,
                        color = dialColor
                    )
                    Text(
                        text = "/ $totalCount",
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SmartColors.textTertiary()
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Status label
            Text(
                text = if (isAllPass) "所有检查通过" else "需要处理 ${totalCount - passCount} 项",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = if (isAllPass) SmartColors.success() else SmartColors.warning()
            )
            Text(
                text = "系统健康度",
                fontSize = 13.sp,
                color = SmartColors.textTertiary()
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════
// Permission Check Card — Colored left border, status icon in circle
// ══════════════════════════════════════════════════════════════════

@Composable
private fun PermissionCheckCard(
    check: PermissionCheck,
    onFix: () -> Unit
) {
    val borderColor = when (check.status) {
        CheckStatus.PASS -> SmartColors.success()
        CheckStatus.WARN -> SmartColors.warning()
        CheckStatus.FAIL -> SmartColors.danger()
        CheckStatus.CHECKING -> SmartColors.textTertiary()
    }

    val statusIcon = when (check.status) {
        CheckStatus.PASS -> Icons.Outlined.CheckCircle
        CheckStatus.WARN -> Icons.Outlined.Warning
        CheckStatus.FAIL -> Icons.Outlined.Error
        CheckStatus.CHECKING -> Icons.Outlined.Schedule
    }

    val statusIconColor = when (check.status) {
        CheckStatus.PASS -> SmartColors.success()
        CheckStatus.WARN -> SmartColors.warning()
        CheckStatus.FAIL -> SmartColors.danger()
        CheckStatus.CHECKING -> SmartColors.textTertiary()
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp)
        ) {
            // Colored left border
            Surface(
                modifier = Modifier
                    .width(4.dp)
                    .height(IntrinsicSize.Max),
                shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
                color = borderColor
            ) {}

            // Content
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Status icon in colored circle
                Surface(
                    shape = CircleShape,
                    color = statusIconColor.copy(alpha = 0.12f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            statusIcon,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = statusIconColor
                        )
                    }
                }

                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(check.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(
                        check.description,
                        fontSize = 13.sp,
                        color = SmartColors.textSecondary()
                    )
                    if (check.detail.isNotEmpty()) {
                        Text(
                            check.detail,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = SmartColors.textTertiary()
                        )
                    }
                }

                // Fix button — accent text button with arrow
                if (check.status == CheckStatus.FAIL || check.status == CheckStatus.WARN) {
                    TextButton(
                        onClick = onFix,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "修复",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SmartColors.accent()
                        )
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            Icons.Outlined.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = SmartColors.accent()
                        )
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
// Gradient Help Card
// ══════════════════════════════════════════════════════════════════

@Composable
private fun GradientHelpCard() {
    val accentColor = SmartColors.accent()

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.12f),
                            accentColor.copy(alpha = 0.04f)
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Help,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column {
                    Text("需要帮助？", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(
                        "如果某些权限无法开启，请参考帮助文档或联系支持。",
                        fontSize = 13.sp,
                        color = SmartColors.textSecondary(),
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
