package com.smarttasker.ui.routestudio

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.core.adb.ScreenshotManager
import com.smarttasker.core.bridge.CoreBridgeManager
import com.smarttasker.core.direct.InputEngine
import com.smarttasker.core.direct.SenseEngine
import com.smarttasker.data.entity.RouteStepEntity
import com.smarttasker.data.entity.RunRecordEntity
import com.smarttasker.data.repository.RouteRepository
import com.smarttasker.data.repository.RunRepository
import com.smarttasker.ui.common.*
import com.smarttasker.ui.theme.SmartColors
import com.smarttasker.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ── Step type color mapping ──
private val StepTypeColors = mapOf(
    "tap"       to Color(0xFF3B82F6),  // Blue
    "input"     to Color(0xFF8B5CF6),  // Purple
    "swipe"     to Color(0xFFF97316),  // Orange
    "wait"      to Color(0xFF9CA3AF),  // Gray
    "back"      to Color(0xFFEF4444),  // Red
    "home"      to Color(0xFF22C55E),  // Green
    "open_app"  to Color(0xFF06B6D4),  // Cyan
    "key"       to Color(0xFFEAB308)   // Yellow
)

private val StepTypeIcons = mapOf(
    "tap"       to Icons.Outlined.TouchApp,
    "input"     to Icons.Outlined.Keyboard,
    "swipe"     to Icons.Outlined.Swipe,
    "wait"      to Icons.Outlined.HourglassTop,
    "back"      to Icons.Outlined.ArrowBack,
    "home"      to Icons.Outlined.Home,
    "open_app"  to Icons.Outlined.Launch,
    "key"       to Icons.Outlined.VpnKey
)

private val StepTypeLabels = mapOf(
    "tap"       to "点击",
    "input"     to "输入",
    "swipe"     to "滑动",
    "wait"      to "等待",
    "back"      to "返回",
    "home"      to "主页",
    "open_app"  to "打开应用",
    "key"       to "按键"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteStudioScreen(
    routeId: String,
    taskName: String,
    routeRepo: RouteRepository,
    runRepo: RunRepository? = null,
    coreBridgeManager: CoreBridgeManager? = null,
    onBack: () -> Unit,
    onOpenRouteEditor: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val steps by routeRepo.getStepsForRoute(routeId).collectAsState(initial = emptyList())
    var selectedStep by remember { mutableStateOf<RouteStepEntity?>(null) }
    var showStepEditor by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<RouteStepEntity?>(null) }
    var isTestingRoute by remember { mutableStateOf(false) }
    var isTestingStep by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isPublished by remember { mutableStateOf(false) }
    // Step-by-step test progress
    var testProgress by remember { mutableStateOf<TestProgress?>(null) }
    // Publish success banner
    var showPublishBanner by remember { mutableStateOf(false) }
    var publishBannerStepCount by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val screenshotManager = remember { ScreenshotManager(context) }

    // Load publish state
    var routeExists by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(routeId) {
        try {
            val route = routeRepo.getRouteById(routeId)
            isPublished = route?.status == "published"
            routeExists = route != null
        } catch (e: Exception) {
            DebugLog.e("RouteStudio", "load route error: ${e.message}")
            routeExists = false
        }
    }

    // Auto-dismiss publish banner
    LaunchedEffect(showPublishBanner) {
        if (showPublishBanner) {
            delay(3000)
            showPublishBanner = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // ── Empty state when route does not exist ──
            if (routeExists == false) {
                Column(modifier = Modifier.fillMaxSize()) {
                    TopAppBar(
                        title = {
                            Text(
                                "路线不存在",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
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
                    EmptyState(
                        icon = Icons.Outlined.ErrorOutline,
                        title = "路线不存在或已删除",
                        subtitle = "该路线可能已被删除，请返回任务列表重新选择"
                    )
                }
                return@Box
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // ── Top App Bar ──
                TopAppBar(
                    title = {
                        Column {
                            Text(taskName, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "路线工作室",
                                    fontSize = 12.sp,
                                    color = SmartColors.textTertiary()
                                )
                                Text(
                                    "·",
                                    fontSize = 12.sp,
                                    color = SmartColors.textTertiary()
                                )
                                Text(
                                    "${steps.size} 步",
                                    fontSize = 12.sp,
                                    color = SmartColors.textTertiary()
                                )
                                if (isPublished) {
                                    Text("·", fontSize = 12.sp, color = SmartColors.textTertiary())
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = SmartColors.success().copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            "已发布",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = SmartColors.success()
                                        )
                                    }
                                } else {
                                    Text("·", fontSize = 12.sp, color = SmartColors.textTertiary())
                                    Text(
                                        "草稿",
                                        fontSize = 12.sp,
                                        color = SmartColors.warning()
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        // Test route button
                        FilledTonalButton(
                            onClick = {
                                coroutineScope.launch {
                                    isTestingRoute = true
                                    testResult = null
                                    testProgress = null
                                    val startTime = System.currentTimeMillis()
                                    val activeSteps = steps.filter { it.enabled }
                                    val totalSteps = activeSteps.size
                                    var successCount = 0
                                    val results = mutableListOf<String>()

                                    for ((idx, step) in activeSteps.withIndex()) {
                                        testProgress = TestProgress(
                                            currentStep = idx + 1,
                                            totalSteps = totalSteps,
                                            currentStepName = step.summary.ifEmpty { StepTypeLabels[step.type] ?: step.type }
                                        )
                                        try {
                                            val ok = withContext(Dispatchers.IO) {
                                                val sense = SenseEngine(context)
                                                val input = InputEngine()
                                                executeStepAction(step, sense, input)
                                            }
                                            if (ok) {
                                                successCount++
                                                results.add("✓ ${step.summary}")
                                            } else {
                                                results.add("✗ ${step.summary}")
                                            }
                                            if (step.waitTimeMs > 0) {
                                                delay(step.waitTimeMs)
                                            }
                                        } catch (e: Exception) {
                                            results.add("✗ ${step.summary}: ${e.message}")
                                        }
                                    }

                                    val durationMs = System.currentTimeMillis() - startTime
                                    testProgress = null
                                    val result = if (successCount == totalSteps) "✅ 全部 $successCount 步执行成功"
                                        else "⚠️ $successCount/$totalSteps 步成功，${totalSteps - successCount} 步失败\n${results.takeLast(3).joinToString("\n")}"
                                    testResult = result
                                    isTestingRoute = false

                                    // Save run record
                                    val runId = "run_${System.currentTimeMillis()}"
                                    val isSuccess = result.startsWith("✅")
                                    val runRecord = RunRecordEntity(
                                        runId = runId,
                                        taskId = "",  // Will be filled by route's taskId below
                                        routeVersion = "",
                                        status = if (isSuccess) "success" else "failed",
                                        durationMs = durationMs,
                                        modelCalls = 0,
                                        diagnosisSummary = if (isSuccess) "路线测试成功" else "路线测试失败",
                                        diagnosisSuggestion = if (isSuccess) "路线执行完成" else result.take(100),
                                        routeSnapshot = "",
                                        retryCount = 0
                                    )
                                    // Look up taskId from the route itself
                                    val currentRoute = routeRepo.getRouteById(routeId)
                                    val finalRun = if (currentRoute != null) {
                                        runRecord.copy(taskId = currentRoute.taskId)
                                    } else runRecord
                                    runRepo?.insertRun(finalRun)
                                }
                            },
                            enabled = !isTestingRoute && steps.isNotEmpty(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = SmartColors.accent().copy(alpha = 0.12f),
                                contentColor = SmartColors.accent()
                            )
                        ) {
                            if (isTestingRoute) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = SmartColors.accent()
                                )
                            } else {
                                Icon(Icons.Outlined.PlayArrow, null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                            Text("测试", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }

                        Spacer(Modifier.width(4.dp))

                        // Publish button
                        if (isPublished) {
                            FilledTonalButton(
                                onClick = {},
                                enabled = false,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    disabledContainerColor = SmartColors.success().copy(alpha = 0.08f),
                                    disabledContentColor = SmartColors.success()
                                )
                            ) {
                                Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("已发布", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        val route = routeRepo.getRouteById(routeId)
                                        if (route != null) {
                                            routeRepo.publishRoute(route)
                                            isPublished = true
                                            publishBannerStepCount = steps.size
                                            showPublishBanner = true
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, SmartColors.accent()),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = SmartColors.accent()
                                )
                            ) {
                                Icon(Icons.Outlined.Publish, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("发布", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )

                // ── Test progress indicator ──
                AnimatedVisibility(
                    visible = testProgress != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    testProgress?.let { progress ->
                        Surface(
                            color = SmartColors.accent().copy(alpha = 0.06f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                        val pulseAlpha by infiniteTransition.animateFloat(
                                            initialValue = 0.4f,
                                            targetValue = 1f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(800, easing = LinearEasing),
                                                repeatMode = RepeatMode.Reverse
                                            ),
                                            label = "pulseAlpha"
                                        )
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = SmartColors.accent().copy(alpha = pulseAlpha)
                                        )
                                        Text(
                                            "正在执行: ${progress.currentStepName}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = SmartColors.accent()
                                        )
                                    }
                                    Text(
                                        "${progress.currentStep}/${progress.totalSteps}",
                                        fontSize = 13.sp,
                                        color = SmartColors.textSecondary()
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = progress.currentStep.toFloat() / progress.totalSteps,
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = SmartColors.accent(),
                                    trackColor = SmartColors.accent().copy(alpha = 0.12f),
                                )
                            }
                        }
                    }
                }

                // ── Test result banner ──
                AnimatedVisibility(
                    visible = testResult != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    testResult?.let { result ->
                        val isSuccess = result.startsWith("✅")
                        val bannerColor = if (isSuccess) SmartColors.success() else SmartColors.danger()
                        Surface(
                            color = bannerColor.copy(alpha = 0.08f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    if (isSuccess) Icons.Outlined.CheckCircle else Icons.Outlined.Error,
                                    contentDescription = null,
                                    tint = bannerColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (isSuccess) "测试通过" else "测试失败",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = bannerColor
                                    )
                                    if (result.contains("\n")) {
                                        Text(
                                            result.substringAfter("\n"),
                                            fontSize = 12.sp,
                                            color = SmartColors.textSecondary(),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { testResult = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "关闭",
                                        modifier = Modifier.size(16.dp),
                                        tint = SmartColors.textTertiary()
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Main content ──
                if (steps.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.Route,
                        title = "还没有路线",
                        subtitle = "完成首次试跑后会自动生成路线"
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        itemsIndexed(steps) { index, step ->
                            StepCard(
                                step = step,
                                isLast = index == steps.lastIndex,
                                isSelected = selectedStep?.stepId == step.stepId,
                                onClick = {
                                    selectedStep = step
                                    showStepEditor = true
                                    coroutineScope.launch {
                                        screenshotManager.capture(step.stepId)
                                    }
                                },
                                onToggleEnabled = { coroutineScope.launch { routeRepo.toggleStepEnabled(step) } },
                                onToggleLocked = { coroutineScope.launch { routeRepo.toggleStepLocked(step) } },
                                onDelete = { showDeleteConfirm = step }
                            )
                        }
                        item { Spacer(Modifier.height(140.dp)) }
                    }
                }
            }

            // ── Step detail panel (bottom sheet overlay) ──
            AnimatedVisibility(
                visible = showStepEditor && selectedStep != null,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                if (selectedStep != null) {
                    // Scrim
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { showStepEditor = false }
                    )
                }
            }

            // ── Step detail panel ──
            AnimatedVisibility(
                visible = showStepEditor && selectedStep != null,
                enter = slideInVertically(initialOffsetY = { it / 2 }),
                exit = slideOutVertically(targetOffsetY = { it / 2 })
            ) {
                if (selectedStep != null) {
                    StepDetailPanel(
                        step = selectedStep!!,
                        screenshotManager = screenshotManager,
                        onEdit = {
                            if (onOpenRouteEditor != null) {
                                onOpenRouteEditor(routeId)
                            } else {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("编辑功能即将上线")
                                }
                            }
                        },
                        onSingleStepTest = {
                            coroutineScope.launch {
                                isTestingStep = true
                                val result = executeSingleStep(selectedStep!!, context)
                                isTestingStep = false
                                snackbarHostState.showSnackbar(result)
                            }
                        },
                        onWaitTimeChange = { newTime ->
                            coroutineScope.launch {
                                routeRepo.updateStep(selectedStep!!.copy(waitTimeMs = newTime))
                            }
                        },
                        onLocatorChange = { strategy, value ->
                            coroutineScope.launch {
                                val updated = selectedStep!!.copy(
                                    locatorStrategy = strategy,
                                    locatorValue = value,
                                    userModified = true
                                )
                                routeRepo.updateStep(updated)
                                selectedStep = updated
                                snackbarHostState.showSnackbar("定位已更新")
                            }
                        },
                        onDismiss = { showStepEditor = false }
                    )
                }
            }

            // ── Publish success banner ──
            AnimatedVisibility(
                visible = showPublishBanner,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Surface(
                    shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                    color = SmartColors.success(),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                "路线已发布！",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                "包含 ${publishBannerStepCount} 个步骤，可在任务详情中查看",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    showDeleteConfirm?.let { step ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            shape = RoundedCornerShape(20.dp),
            title = { Text("删除步骤", fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text("确定要删除此步骤吗？此操作不可撤销。")
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val stepColor = StepTypeColors[step.type] ?: SmartColors.accent()
                            Surface(
                                shape = CircleShape,
                                color = stepColor.copy(alpha = 0.12f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        StepTypeIcons[step.type] ?: Icons.Outlined.TouchApp,
                                        contentDescription = null,
                                        tint = stepColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    "步骤 ${step.stepIndex}",
                                    fontSize = 12.sp,
                                    color = SmartColors.textTertiary()
                                )
                                Text(
                                    step.summary.ifEmpty { StepTypeLabels[step.type] ?: step.type },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            routeRepo.deleteStep(step)
                            routeRepo.reindexSteps(routeId)
                            showDeleteConfirm = null
                            if (selectedStep?.stepId == step.stepId) {
                                selectedStep = null
                                showStepEditor = false
                            }
                            snackbarHostState.showSnackbar("已删除")
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = SmartColors.danger())
                ) { Text("删除", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") }
            }
        )
    }
}

// ── Test progress data class ──
private data class TestProgress(
    val currentStep: Int,
    val totalSteps: Int,
    val currentStepName: String
)

/**
 * Execute all steps in route order. Returns result summary.
 */
private suspend fun executeRoute(steps: List<RouteStepEntity>, context: android.content.Context): String =
    withContext(Dispatchers.IO) {
        val sense = SenseEngine(context)
        val input = InputEngine()
        val activeSteps = steps.filter { it.enabled }

        if (activeSteps.isEmpty()) return@withContext "❌ 没有可执行的步骤"

        val results = mutableListOf<String>()
        var successCount = 0

        for (step in activeSteps) {
            try {
                val ok = executeStepAction(step, sense, input)
                if (ok) {
                    successCount++
                    results.add("✓ ${step.summary}")
                } else {
                    results.add("✗ ${step.summary}")
                }
                // Wait between steps
                if (step.waitTimeMs > 0) {
                    kotlinx.coroutines.delay(step.waitTimeMs)
                }
            } catch (e: Exception) {
                results.add("✗ ${step.summary}: ${e.message}")
            }
        }

        if (successCount == activeSteps.size) "✅ 全部 $successCount 步执行成功"
        else "⚠️ $successCount/${activeSteps.size} 步成功，${activeSteps.size - successCount} 步失败\n${results.takeLast(3).joinToString("\n")}"
    }

/**
 * Execute a single step. Returns result message.
 */
private suspend fun executeSingleStep(step: RouteStepEntity, context: android.content.Context): String =
    withContext(Dispatchers.IO) {
        val sense = SenseEngine(context)
        val input = InputEngine()
        val ok = executeStepAction(step, sense, input)
        if (ok) "✅ ${step.summary}" else "❌ ${step.summary} 执行失败"
    }

private suspend fun executeStepAction(
    step: RouteStepEntity,
    sense: SenseEngine,
    input: InputEngine
): Boolean {
    return try {
        when (step.type) {
            "tap" -> {
                // 支持多种定位策略
                val coords = resolveTapCoordinates(step, sense)
                if (coords != null) {
                    input.tap(coords.first, coords.second)
                    kotlinx.coroutines.delay(500)
                    true
                } else false
            }
            "swipe" -> {
                // 解析滑动坐标: "startX,startY,endX,endY"
                val coords = parseSwipeCoordinates(step)
                if (coords != null) {
                    input.swipe(coords[0], coords[1], coords[2], coords[3], 300)
                    kotlinx.coroutines.delay(500)
                    true
                } else false
            }
            "input" -> {
                input.inputText(step.locatorValue)
                kotlinx.coroutines.delay(300)
                true
            }
            "wait" -> {
                val ms = step.locatorValue.toLongOrNull() ?: step.waitTimeMs
                kotlinx.coroutines.delay(ms)
                true
            }
            "back" -> {
                input.pressBack()
                kotlinx.coroutines.delay(500)
                true
            }
            "home" -> {
                input.pressHome()
                kotlinx.coroutines.delay(500)
                true
            }
            "open_app" -> {
                sense.launchApp(step.locatorValue)
                kotlinx.coroutines.delay(2000)
                true
            }
            "key" -> {
                val keyCode = step.locatorValue.toIntOrNull() ?: mapKeyNameToCode(step.locatorValue)
                if (keyCode > 0) {
                    input.pressKey(keyCode)
                    kotlinx.coroutines.delay(300)
                    true
                } else false
            }
            else -> {
                // Unknown type, try as tap if there are coords
                val coords = parseCoordinate(step.locatorValue)
                if (coords != null) {
                    input.tap(coords.first, coords.second)
                    kotlinx.coroutines.delay(500)
                    true
                } else false
            }
        }
    } catch (e: Exception) {
        false
    }
}

/**
 * 根据步骤的定位策略，解析出点击坐标。
 * 支持: coordinate / text / resource_id / content_desc
 */
private suspend fun resolveTapCoordinates(
    step: RouteStepEntity,
    sense: SenseEngine
): Pair<Int, Int>? {
    return when (step.locatorStrategy) {
        "coordinate" -> parseCoordinate(step.locatorValue)
        "text", "resource_id", "content_desc" -> {
            // 通过 uiautomator dump 找到元素坐标
            val result = sense.dumpHierarchy()
            when (result) {
                is com.smarttasker.core.bridge.HierarchyResult.Success -> {
                    findElementBounds(result.xml, step.locatorStrategy, step.locatorValue)
                }
                else -> null
            }
        }
        else -> parseCoordinate(step.locatorValue)
    }
}

/**
 * 从 UI hierarchy XML 中查找匹配元素的中心坐标。
 * @param xml uiautomator dump 的 XML
 * @param strategy 定位策略: text / resource_id / content_desc
 * @param value 要匹配的值
 * @return 元素中心坐标 (x, y)，未找到返回 null
 */
private fun findElementBounds(xml: String, strategy: String, value: String): Pair<Int, Int>? {
    try {
        // uiautomator XML 格式: <node ... bounds="[x1,y1][x2,y2]" text="..." resource-id="..." content-desc="..." />
        val attrName = when (strategy) {
            "text" -> "text"
            "resource_id" -> "resource-id"
            "content_desc" -> "content-desc"
            else -> return null
        }

        // 用正则匹配节点（避免 XML 解析器依赖）
        val nodePattern = Regex("""<node\s[^>]*?>""")
        val boundsPattern = Regex("""bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"""")
        val attrPattern = Regex("""$attrName="([^"]*?)"""")

        for (nodeMatch in nodePattern.findAll(xml)) {
            val nodeStr = nodeMatch.value
            val attrMatch = attrPattern.find(nodeStr) ?: continue
            val attrValue = attrMatch.groupValues[1]

            // 精确匹配或包含匹配
            if (attrValue == value || attrValue.contains(value)) {
                val boundsMatch = boundsPattern.find(nodeStr) ?: continue
                val x1 = boundsMatch.groupValues[1].toInt()
                val y1 = boundsMatch.groupValues[2].toInt()
                val x2 = boundsMatch.groupValues[3].toInt()
                val y2 = boundsMatch.groupValues[4].toInt()
                return Pair((x1 + x2) / 2, (y1 + y2) / 2)
            }
        }
    } catch (_: Exception) {}
    return null
}

/**
 * 解析滑动步骤的坐标。支持多种格式：
 * - "startX,startY,endX,endY" (逗号分隔)
 * - "startX startY endX endY" (空格分隔)
 * - 从 step 的 fallbackValue 或 locatorValue 解析
 */
private fun parseSwipeCoordinates(step: RouteStepEntity): List<Int>? {
    val value = step.locatorValue.ifEmpty { step.fallbackValue }
    if (value.isBlank()) return null

    // 尝试逗号分隔
    val commaParts = value.split(",").map { it.trim().toIntOrNull() }
    if (commaParts.size >= 4 && commaParts.all { it != null }) {
        return commaParts.take(4).map { it!! }
    }

    // 尝试空格分隔
    val spaceParts = value.split(Regex("\\s+")).map { it.trim().toIntOrNull() }
    if (spaceParts.size >= 4 && spaceParts.all { it != null }) {
        return spaceParts.take(4).map { it!! }
    }

    return null
}

private fun parseCoordinate(value: String): Pair<Int, Int>? {
    if (value.isBlank()) return null
    val parts = value.split(",")
    if (parts.size < 2) return null
    val x = parts[0].trim().toIntOrNull() ?: return null
    val y = parts[1].trim().toIntOrNull() ?: return null
    return Pair(x, y)
}

/**
 * Map key name string to Android KeyEvent code.
 */
private fun mapKeyNameToCode(name: String): Int = when (name.uppercase()) {
    "BACK" -> 4
    "HOME" -> 3
    "MENU" -> 82
    "POWER" -> 26
    "APP_SWITCH", "RECENT" -> 187
    "VOLUME_UP" -> 24
    "VOLUME_DOWN" -> 25
    "ENTER" -> 66
    "DEL", "DELETE" -> 67
    "TAB" -> 61
    "SPACE" -> 62
    "DPAD_UP" -> 19
    "DPAD_DOWN" -> 20
    "DPAD_LEFT" -> 21
    "DPAD_RIGHT" -> 22
    "DPAD_CENTER" -> 23
    else -> 0
}

// ══════════════════════════════════════════════════════════════════
// StepCard with flowchart-style connector lines
// ══════════════════════════════════════════════════════════════════

@Composable
private fun StepCard(
    step: RouteStepEntity,
    isLast: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleEnabled: () -> Unit,
    onToggleLocked: () -> Unit,
    onDelete: () -> Unit
) {
    val stepColor = StepTypeColors[step.type] ?: SmartColors.accent()
    val stepIcon = StepTypeIcons[step.type] ?: Icons.Outlined.TouchApp
    val stepLabel = StepTypeLabels[step.type] ?: step.type
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
            val circleBgColor by animateColorAsState(
                targetValue = if (step.enabled) stepColor.copy(alpha = 0.15f) else SmartColors.textTertiary().copy(alpha = 0.08f),
                label = "circleBg"
            )
            val circleBorderColor by animateColorAsState(
                targetValue = if (step.enabled) stepColor else SmartColors.textTertiary().copy(alpha = 0.3f),
                label = "circleBorder"
            )

            Surface(
                shape = CircleShape,
                color = circleBgColor,
                border = BorderStroke(2.dp, circleBorderColor),
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
            val cardBorderColor by animateColorAsState(
                targetValue = if (isSelected) stepColor else SmartColors.borderSubtle(),
                label = "cardBorder"
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = cardBorderColor
                ),
                shadowElevation = if (isSelected) 4.dp else 0.dp,
                onClick = onClick
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Row 1: Step index + summary + action buttons
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
                                "#${step.stepIndex}",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = stepColor
                            )
                        }

                        // Summary text
                        Text(
                            step.summary.ifEmpty { stepLabel },
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = if (step.enabled) MaterialTheme.colorScheme.onSurface else SmartColors.textTertiary(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Action buttons
                        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                            IconButton(onClick = onToggleEnabled, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    if (step.enabled) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                    null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (step.enabled) SmartColors.success() else SmartColors.textTertiary()
                                )
                            }
                            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    null,
                                    modifier = Modifier.size(16.dp),
                                    tint = SmartColors.danger().copy(alpha = 0.5f)
                                )
                            }
                        }
                    }

                    // Row 2: Tags
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Type pill
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = stepColor.copy(alpha = 0.1f)
                        ) {
                            Text(
                                stepLabel,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = stepColor
                            )
                        }

                        // Locator strategy pill
                        val locatorColor = when (step.locatorStrategy) {
                            "coordinate" -> SmartColors.warning()
                            "text" -> SmartColors.accent()
                            "resource_id" -> Color(0xFF8B5CF6)
                            "content_desc" -> Color(0xFF06B6D4)
                            else -> SmartColors.textTertiary()
                        }
                        val locatorLabel = when (step.locatorStrategy) {
                            "text" -> "文本定位"
                            "coordinate" -> "坐标定位"
                            "resource_id" -> "ID定位"
                            "content_desc" -> "内容描述"
                            else -> step.locatorStrategy
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = locatorColor.copy(alpha = 0.1f)
                        ) {
                            Text(
                                locatorLabel,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = locatorColor
                            )
                        }

                        // Locked indicator
                        if (step.lockedByUser) {
                            Icon(
                                Icons.Outlined.Lock,
                                null,
                                modifier = Modifier.size(14.dp),
                                tint = SmartColors.warning()
                            )
                        }

                        // Wait time indicator
                        if (step.waitTimeMs > 0) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SmartColors.textTertiary().copy(alpha = 0.08f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Schedule,
                                        null,
                                        modifier = Modifier.size(10.dp),
                                        tint = SmartColors.textTertiary()
                                    )
                                    Text(
                                        if (step.waitTimeMs >= 1000) "${step.waitTimeMs / 1000}s"
                                        else "${step.waitTimeMs}ms",
                                        fontSize = 10.sp,
                                        color = SmartColors.textTertiary()
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

// ══════════════════════════════════════════════════════════════════
// StepDetailPanel - polished bottom sheet
// ══════════════════════════════════════════════════════════════════

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun StepDetailPanel(
    step: RouteStepEntity,
    screenshotManager: ScreenshotManager,
    onEdit: () -> Unit,
    onSingleStepTest: () -> Unit,
    onWaitTimeChange: (Long) -> Unit,
    onLocatorChange: (strategy: String, value: String) -> Unit,
    onDismiss: () -> Unit
) {
    val stepColor = StepTypeColors[step.type] ?: SmartColors.accent()
    val stepIcon = StepTypeIcons[step.type] ?: Icons.Outlined.TouchApp
    val stepLabel = StepTypeLabels[step.type] ?: step.type

    var showWaitTimeEditor by remember(step.stepId) { mutableStateOf(false) }
    var showLocatorEditor by remember(step.stepId) { mutableStateOf(false) }
    var waitTimeText by remember(step.stepId) { mutableStateOf((step.waitTimeMs / 1000).toString()) }
    var locatorStrategy by remember(step.stepId) { mutableStateOf(step.locatorStrategy) }
    var locatorValue by remember(step.stepId) { mutableStateOf(step.locatorValue) }
    var screenshotBitmap by remember(step.stepId) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var screenshotSize by remember(step.stepId) { mutableStateOf(IntSize.Zero) }
    val coroutineScope = rememberCoroutineScope()

    // Parse tap coordinates for overlay
    val coords = remember(step.locatorValue) { parseCoordinate(step.locatorValue) }

    // Load screenshot when panel opens
    LaunchedEffect(step.stepId) {
        val file = withContext(Dispatchers.IO) {
            val savedRef = step.screenshotRef
            if (!savedRef.isNullOrEmpty()) {
                val savedFile = File(savedRef)
                if (savedFile.exists()) {
                    BitmapFactory.decodeFile(savedFile.absolutePath)
                } else null
            } else {
                val f = screenshotManager.getScreenshotFile(step.stepId)
                if (f != null) {
                    BitmapFactory.decodeFile(f.absolutePath)
                } else {
                    val path = screenshotManager.capture(step.stepId)
                    if (path != null) BitmapFactory.decodeFile(path) else null
                }
            }
        }
        screenshotBitmap = file
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            // ── Handle bar ──
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = SmartColors.textTertiary().copy(alpha = 0.3f),
                    modifier = Modifier.width(36.dp).height(5.dp)
                ) {}
                Spacer(Modifier.height(8.dp))
            }

            // ── Header row ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = stepColor.copy(alpha = 0.12f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                stepIcon,
                                contentDescription = null,
                                tint = stepColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            "步骤 ${step.stepIndex}",
                            fontSize = 12.sp,
                            color = SmartColors.textTertiary()
                        )
                        Text(
                            step.summary.ifEmpty { stepLabel },
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "关闭",
                        modifier = Modifier.size(20.dp),
                        tint = SmartColors.textTertiary()
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Screenshot section ──
            val bitmap = screenshotBitmap
            if (bitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .onSizeChanged { screenshotSize = it }
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "步骤截图",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    // Overlay tap marker
                    if (coords != null && screenshotSize != IntSize.Zero) {
                        val bitmapW = bitmap.width.toFloat()
                        val bitmapH = bitmap.height.toFloat()
                        val viewW = screenshotSize.width.toFloat()
                        val viewH = screenshotSize.height.toFloat()

                        val scaleX = viewW / bitmapW
                        val scaleY = viewH / bitmapH
                        val scale = minOf(scaleX, scaleY)
                        val offsetX = (viewW - bitmapW * scale) / 2f
                        val offsetY = (viewH - bitmapH * scale) / 2f

                        val tapX = coords.first.toFloat() * scale + offsetX
                        val tapY = coords.second.toFloat() * scale + offsetY

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Outer ring with pulse
                            drawCircle(
                                color = stepColor,
                                radius = 20f,
                                center = Offset(tapX, tapY),
                                style = Stroke(width = 2.5f)
                            )
                            // Inner dot
                            drawCircle(
                                color = stepColor.copy(alpha = 0.5f),
                                radius = 5f,
                                center = Offset(tapX, tapY)
                            )
                            // Crosshair
                            drawLine(
                                color = stepColor.copy(alpha = 0.3f),
                                start = Offset(tapX - 12f, tapY),
                                end = Offset(tapX + 12f, tapY),
                                strokeWidth = 1.5f
                            )
                            drawLine(
                                color = stepColor.copy(alpha = 0.3f),
                                start = Offset(tapX, tapY - 12f),
                                end = Offset(tapX, tapY + 12f),
                                strokeWidth = 1.5f
                            )
                        }
                    }

                    // Coordinate label
                    if (coords != null) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xDD121212)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.MyLocation,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = Color.White
                                )
                                Text(
                                    "(${coords.first}, ${coords.second})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SmartColors.textTertiary().copy(alpha = 0.06f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(140.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.Screenshot,
                                null,
                                modifier = Modifier.size(36.dp),
                                tint = SmartColors.textTertiary().copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text("暂无截图", fontSize = 13.sp, color = SmartColors.textTertiary())
                            Text("需 ADB 连接后自动获取", fontSize = 11.sp, color = SmartColors.textTertiary().copy(alpha = 0.7f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Step details section ──
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                // Section header
                Text(
                    "步骤详情",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SmartColors.textTertiary(),
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        DetailRow("动作", stepLabel, stepColor)
                        DetailRow("定位方式", locatorStrategy)
                        if (locatorValue.isNotEmpty()) DetailRow("定位值", locatorValue)
                        DetailRow("等待时间", "${step.waitTimeMs}ms")
                        DetailRow("来源", when (step.source) {
                            "ai_learned" -> "AI 学习"
                            "manual_recording" -> "手动录制"
                            "user_edit" -> "手动编辑"
                            else -> step.source
                        })
                        if (step.riskLevel != "low") DetailRow("风险等级", step.riskLevel, SmartColors.warning())
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Quick actions section ──
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    "快捷操作",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SmartColors.textTertiary(),
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.height(10.dp))

                // Locator editor toggle
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SmartColors.warning().copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, SmartColors.warning().copy(alpha = 0.15f)),
                    onClick = { showLocatorEditor = !showLocatorEditor }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Outlined.MyLocation,
                            null,
                            modifier = Modifier.size(18.dp),
                            tint = SmartColors.warning()
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("修改定位方式", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = SmartColors.warning())
                            if (step.userModified) {
                                Text("已手动修改", fontSize = 11.sp, color = SmartColors.textTertiary())
                            }
                        }
                        Icon(
                            if (showLocatorEditor) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            null,
                            modifier = Modifier.size(18.dp),
                            tint = SmartColors.textTertiary()
                        )
                    }
                }

                // Locator editor expanded
                AnimatedVisibility(
                    visible = showLocatorEditor,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("定位策略", fontSize = 13.sp, color = SmartColors.textSecondary())
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val strategies = listOf("coordinate" to "坐标", "text" to "文本", "resource_id" to "资源ID", "content_desc" to "内容描述")
                            strategies.forEach { (value, label) ->
                                FilterChip(
                                    selected = locatorStrategy == value,
                                    onClick = { locatorStrategy = value },
                                    label = { Text(label, fontSize = 12.sp) },
                                    modifier = Modifier.height(32.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SmartColors.accent().copy(alpha = 0.15f),
                                        selectedLabelColor = SmartColors.accent()
                                    )
                                )
                            }
                        }

                        OutlinedTextField(
                            value = locatorValue,
                            onValueChange = { locatorValue = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("定位值") },
                            placeholder = {
                                Text(when (locatorStrategy) {
                                    "coordinate" -> "例: 540,1200"
                                    "text" -> "例: 搜索"
                                    "resource_id" -> "例: com.example:id/search"
                                    "content_desc" -> "例: 返回按钮"
                                    else -> ""
                                })
                            },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    onLocatorChange(locatorStrategy, locatorValue)
                                    showLocatorEditor = false
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SmartColors.accent())
                            ) { Text("保存定位") }
                            OutlinedButton(
                                onClick = { showLocatorEditor = false },
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("取消") }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Wait time editor toggle
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SmartColors.accent().copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, SmartColors.accent().copy(alpha = 0.15f)),
                    onClick = { showWaitTimeEditor = !showWaitTimeEditor }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Timer,
                            null,
                            modifier = Modifier.size(18.dp),
                            tint = SmartColors.accent()
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("修改等待时间", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = SmartColors.accent())
                            Text("当前: ${step.waitTimeMs}ms", fontSize = 11.sp, color = SmartColors.textTertiary())
                        }
                        Icon(
                            if (showWaitTimeEditor) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            null,
                            modifier = Modifier.size(18.dp),
                            tint = SmartColors.textTertiary()
                        )
                    }
                }

                // Wait time editor expanded
                AnimatedVisibility(
                    visible = showWaitTimeEditor,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = waitTimeText,
                            onValueChange = { waitTimeText = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("等待秒数") },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                val seconds = waitTimeText.toLongOrNull() ?: 1L
                                onWaitTimeChange(seconds * 1000)
                                showWaitTimeEditor = false
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("保存") }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── AI suggestion ──
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SmartColors.accent().copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, SmartColors.accent().copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = SmartColors.accent().copy(alpha = 0.12f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.AutoAwesome,
                                    null,
                                    tint = SmartColors.accent(),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Column {
                            Text("AI 建议", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = SmartColors.accent())
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (step.locatorStrategy == "coordinate") "建议改为文本定位，提高稳定性"
                                else "当前定位方式较稳定",
                                fontSize = 13.sp,
                                color = SmartColors.textSecondary()
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Action buttons ──
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SmartColors.accent())
                ) {
                    Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("编辑", fontWeight = FontWeight.Medium)
                }
                OutlinedButton(
                    onClick = onSingleStepTest,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Outlined.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("单步测试", fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = SmartColors.textTertiary())
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = valueColor)
    }
}
