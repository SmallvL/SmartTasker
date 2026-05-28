package com.smarttasker.ui.routestudio

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.core.adb.ScreenshotManager
import com.smarttasker.core.bridge.CoreBridgeManager
import com.smarttasker.core.direct.InputEngine
import com.smarttasker.core.direct.SenseEngine
import com.smarttasker.data.entity.RouteStepEntity
import com.smarttasker.data.entity.RouteVersionEntity
import com.smarttasker.data.entity.RunRecordEntity
import com.smarttasker.data.repository.RouteRepository
import com.smarttasker.data.repository.RunRepository
import com.smarttasker.ui.common.*
import com.smarttasker.ui.theme.SmartColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val screenshotManager = remember { ScreenshotManager(context) }

    // Load publish state
    LaunchedEffect(routeId) {
        val route = routeRepo.getRouteById(routeId)
        isPublished = route?.status == "published"
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TopAppBar(
                title = {
                    Column {
                        Text(taskName, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        Text(
                            "路线编辑器 · ${steps.size} 步" + if (isPublished) " · 已发布" else " · 草稿",
                            fontSize = 12.sp,
                            color = if (isPublished) SmartColors.success() else SmartColors.textSecondary()
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // Test route
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                isTestingRoute = true
                                testResult = null
                                val startTime = System.currentTimeMillis()
                                val result = executeRoute(steps, context)
                                val durationMs = System.currentTimeMillis() - startTime
                                testResult = result
                                isTestingRoute = false
                                
                                // Save run record
                                val runId = "run_${System.currentTimeMillis()}"
                                val isSuccess = result.startsWith("✅")
                                val runRecord = RunRecordEntity(
                                    runId = runId,
                                    taskId = routeId,
                                    status = if (isSuccess) "success" else "failed",
                                    durationMs = durationMs,
                                    modelCalls = 0,
                                    diagnosisSummary = if (isSuccess) "路线测试成功" else "路线测试失败",
                                    diagnosisSuggestion = if (isSuccess) "路线执行完成" else result.take(100),
                                    routeSnapshot = "",
                                    retryCount = 0
                                )
                                runRepo?.insertRun(runRecord)
                                
                                if (isSuccess) {
                                    snackbarHostState.showSnackbar("路线执行成功")
                                } else {
                                    snackbarHostState.showSnackbar("执行失败: ${result.take(50)}")
                                }
                            }
                        },
                        enabled = !isTestingRoute && steps.isNotEmpty()
                    ) {
                        if (isTestingRoute) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.PlayArrow, null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text("测试")
                    }
                    // Publish
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                val route = routeRepo.getRouteById(routeId)
                                if (route != null) {
                                    routeRepo.publishRoute(route)
                                    isPublished = true
                                    snackbarHostState.showSnackbar("路线已发布")
                                }
                            }
                        },
                        enabled = !isPublished
                    ) {
                        Icon(
                            if (isPublished) Icons.Outlined.CheckCircle else Icons.Outlined.Publish,
                            null,
                            modifier = Modifier.size(18.dp),
                            tint = if (isPublished) SmartColors.success() else LocalContentColor.current
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (isPublished) "已发布" else "发布")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )

            // Test result banner
            if (testResult != null) {
                val isSuccess = testResult!!.startsWith("✅")
                Surface(
                    color = if (isSuccess) SmartColors.success().copy(alpha = 0.1f)
                    else SmartColors.danger().copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        testResult!!,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        fontSize = 13.sp,
                        color = if (isSuccess) SmartColors.success() else SmartColors.danger()
                    )
                }
            }

            if (steps.isEmpty()) {
                EmptyState(icon = Icons.Outlined.Route, title = "还没有路线", subtitle = "完成首次试跑后会自动生成路线")
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(steps) { _, step ->
                        StepCard(
                            step = step,
                            isSelected = selectedStep?.stepId == step.stepId,
                            onClick = {
                                selectedStep = step
                                showStepEditor = true
                                // Auto-capture screenshot on step selection
                                coroutineScope.launch {
                                    screenshotManager.capture(step.stepId)
                                }
                            },
                            onToggleEnabled = { coroutineScope.launch { routeRepo.toggleStepEnabled(step) } },
                            onToggleLocked = { coroutineScope.launch { routeRepo.toggleStepLocked(step) } },
                            onDelete = { showDeleteConfirm = step }
                        )
                    }
                    item { Spacer(Modifier.height(120.dp)) }
                }

                // Step detail panel
                if (showStepEditor && selectedStep != null) {
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
        }
    }

    // Delete confirmation dialog
    showDeleteConfirm?.let { step ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("删除步骤") },
            text = { Text("确定要删除步骤 ${step.stepIndex} \"${step.summary}\" 吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            routeRepo.deleteStep(step)
                            routeRepo.reindexSteps(routeId)
                            showDeleteConfirm = null
                            // Clear selection if deleted step was selected
                            if (selectedStep?.stepId == step.stepId) {
                                selectedStep = null
                                showStepEditor = false
                            }
                            snackbarHostState.showSnackbar("已删除")
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = SmartColors.danger())
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") }
            }
        )
    }
}

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

@Composable
private fun StepCard(
    step: RouteStepEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleEnabled: () -> Unit,
    onToggleLocked: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) SmartColors.accent() else SmartColors.borderSubtle()
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Step number
            Surface(
                shape = RoundedCornerShape(12),
                color = if (step.enabled) SmartColors.accent().copy(alpha = 0.1f) else SmartColors.textTertiary().copy(alpha = 0.1f),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "${step.stepIndex}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (step.enabled) SmartColors.accent() else SmartColors.textTertiary()
                    )
                }
            }

            // Step info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    step.summary.ifEmpty { step.type },
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = if (step.enabled) MaterialTheme.colorScheme.onSurface else SmartColors.textTertiary()
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(
                        when (step.type) {
                            "tap" -> "点击"
                            "input" -> "输入"
                            "swipe" -> "滑动"
                            "wait" -> "等待"
                            "open_app" -> "打开应用"
                            "back" -> "返回"
                            "home" -> "主页"
                            "key" -> "按键"
                            else -> step.type
                        },
                        SmartColors.textTertiary()
                    )
                    StatusPill(
                        when (step.locatorStrategy) {
                            "text" -> "文本定位"
                            "coordinate" -> "坐标定位"
                            "resource_id" -> "ID定位"
                            else -> step.locatorStrategy
                        },
                        if (step.locatorStrategy == "coordinate") SmartColors.warning() else SmartColors.accent()
                    )
                    if (step.lockedByUser) {
                        Icon(Icons.Outlined.Lock, null, modifier = Modifier.size(14.dp), tint = SmartColors.warning())
                    }
                }
            }

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onToggleEnabled, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (step.enabled) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        null,
                        modifier = Modifier.size(18.dp),
                        tint = if (step.enabled) SmartColors.success() else SmartColors.textTertiary()
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.Delete,
                        null,
                        modifier = Modifier.size(18.dp),
                        tint = SmartColors.danger().copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

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
            val f = screenshotManager.getScreenshotFile(step.stepId)
            if (f == null) {
                val path = screenshotManager.capture(step.stepId)
                if (path != null) BitmapFactory.decodeFile(path) else null
            } else {
                BitmapFactory.decodeFile(f.absolutePath)
            }
        }
        screenshotBitmap = file // Set on Main thread (inside LaunchedEffect)
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding()
        ) {
            // Drag handle
            Box(
                modifier = Modifier.width(40.dp).height(4.dp).align(Alignment.CenterHorizontally)
            ) {
                Surface(shape = RoundedCornerShape(2.dp), color = SmartColors.borderSubtle()) {}
            }
            Spacer(Modifier.height(12.dp))

            // Screenshot with tap marker overlay
            val bitmap = screenshotBitmap
            if (bitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
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

                        // Calculate scale to fit
                        val scaleX = viewW / bitmapW
                        val scaleY = viewH / bitmapH
                        val scale = minOf(scaleX, scaleY)
                        val offsetX = (viewW - bitmapW * scale) / 2f
                        val offsetY = (viewH - bitmapH * scale) / 2f

                        val tapX = coords.first.toFloat() * scale + offsetX
                        val tapY = coords.second.toFloat() * scale + offsetY

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Outer circle
                            drawCircle(
                                color = Color(0xFF5E6AD2),
                                radius = 18f,
                                center = Offset(tapX, tapY),
                                style = Stroke(width = 2.5f)
                            )
                            // Inner dot
                            drawCircle(
                                color = Color(0xFF5E6AD2).copy(alpha = 0.6f),
                                radius = 5f,
                                center = Offset(tapX, tapY)
                            )
                            // Crosshair
                            drawLine(
                                color = Color(0xFF5E6AD2).copy(alpha = 0.4f),
                                start = Offset(tapX - 10f, tapY),
                                end = Offset(tapX + 10f, tapY),
                                strokeWidth = 1.5f
                            )
                            drawLine(
                                color = Color(0xFF5E6AD2).copy(alpha = 0.4f),
                                start = Offset(tapX, tapY - 10f),
                                end = Offset(tapX, tapY + 10f),
                                strokeWidth = 1.5f
                            )
                        }
                    }

                    // Coordinate label
                    if (coords != null) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                            shape = RoundedCornerShape(6),
                            color = Color(0xCC121212)
                        ) {
                            Text(
                                "📍 (${coords.first}, ${coords.second})",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(16),
                    color = SmartColors.textTertiary().copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.Screenshot, null, modifier = Modifier.size(36.dp), tint = SmartColors.textTertiary())
                            Text("选中步骤自动截图", fontSize = 13.sp, color = SmartColors.textTertiary())
                            Text("需 ADB 连接", fontSize = 11.sp, color = SmartColors.textTertiary())
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // Step info header
            Text("步骤 ${step.stepIndex}: ${step.summary}", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))

            DetailRow("动作", when (step.type) { "tap" -> "点击"; "input" -> "输入"; "swipe" -> "滑动"; "wait" -> "等待"; "back" -> "返回"; "home" -> "主页"; "open_app" -> "打开应用"; "key" -> "按键"; else -> step.type })
            DetailRow("定位方式", locatorStrategy)
            if (locatorValue.isNotEmpty()) DetailRow("定位值", locatorValue)
            DetailRow("等待时间", "${step.waitTimeMs}ms")
            DetailRow("来源", when (step.source) { "ai_learned" -> "AI 学习"; "manual_recording" -> "手动录制"; "user_edit" -> "手动编辑"; else -> step.source })
            if (step.riskLevel != "low") DetailRow("风险等级", step.riskLevel, SmartColors.warning())

            // Locator editor
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(12),
                color = SmartColors.warning().copy(alpha = 0.05f),
                onClick = { showLocatorEditor = !showLocatorEditor }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.MyLocation, null, modifier = Modifier.size(18.dp), tint = SmartColors.warning())
                    Text("修改定位方式", fontSize = 14.sp, color = SmartColors.warning())
                    if (step.userModified) {
                        Text("(已修改)", fontSize = 11.sp, color = SmartColors.textTertiary())
                    }
                }
            }

            if (showLocatorEditor) {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Locator strategy dropdown
                    Text("定位策略", fontSize = 13.sp, color = SmartColors.textSecondary())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                    // Locator value field
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
                        shape = RoundedCornerShape(12),
                        singleLine = true
                    )

                    // Save button
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                onLocatorChange(locatorStrategy, locatorValue)
                                showLocatorEditor = false
                            },
                            shape = RoundedCornerShape(12),
                            colors = ButtonDefaults.buttonColors(containerColor = SmartColors.accent())
                        ) { Text("保存定位") }
                        OutlinedButton(
                            onClick = { showLocatorEditor = false },
                            shape = RoundedCornerShape(12)
                        ) { Text("取消") }
                    }
                }
            }

            // Wait time editor
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(12),
                color = SmartColors.accent().copy(alpha = 0.05f),
                onClick = { showWaitTimeEditor = !showWaitTimeEditor }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.Timer, null, modifier = Modifier.size(18.dp), tint = SmartColors.accent())
                    Text("修改等待时间", fontSize = 14.sp, color = SmartColors.accent())
                }
            }

            if (showWaitTimeEditor) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = waitTimeText,
                        onValueChange = { waitTimeText = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("等待秒数") },
                        shape = RoundedCornerShape(12),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            val seconds = waitTimeText.toLongOrNull() ?: 1L
                            onWaitTimeChange(seconds * 1000)
                            showWaitTimeEditor = false
                        },
                        shape = RoundedCornerShape(12)
                    ) { Text("保存") }
                }
            }

            // AI suggestion
            Spacer(Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(16), color = SmartColors.accent().copy(alpha = 0.08f)) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = SmartColors.accent(), modifier = Modifier.size(20.dp))
                    Column {
                        Text("AI 建议", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = SmartColors.accent())
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (step.locatorStrategy == "coordinate") "建议改为文本定位，提高稳定性"
                            else "当前定位方式较稳定",
                            fontSize = 13.sp,
                            color = SmartColors.textSecondary()
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16),
                    colors = ButtonDefaults.buttonColors(containerColor = SmartColors.accent())
                ) {
                    Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("编辑")
                }
                OutlinedButton(
                    onClick = onSingleStepTest,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16)
                ) {
                    Icon(Icons.Outlined.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("单步测试")
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = SmartColors.textSecondary())
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = valueColor)
    }
}
