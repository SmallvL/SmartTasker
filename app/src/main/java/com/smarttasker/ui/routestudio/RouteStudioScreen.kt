package com.smarttasker.ui.routestudio

import androidx.compose.foundation.BorderStroke
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
import com.smarttasker.core.bridge.CoreBridgeManager
import com.smarttasker.data.entity.RouteStepEntity
import com.smarttasker.data.entity.RouteVersionEntity
import com.smarttasker.data.repository.RouteRepository
import com.smarttasker.ui.common.*
import com.smarttasker.ui.theme.SmartColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteStudioScreen(
    routeId: String,
    taskName: String,
    routeRepo: RouteRepository,
    coreBridgeManager: CoreBridgeManager? = null,
    onBack: () -> Unit
) {
    val steps by routeRepo.getStepsForRoute(routeId).collectAsState(initial = emptyList())
    var selectedStep by remember { mutableStateOf<RouteStepEntity?>(null) }
    var showStepEditor by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<RouteStepEntity?>(null) }
    var isTestingRoute by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TopAppBar(
                title = {
                    Column {
                        Text(taskName, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        Text("路线编辑器 · ${steps.size} 步", fontSize = 12.sp, color = SmartColors.textSecondary())
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
                            isTestingRoute = true
                            testResult = null
                            // TODO: Call CoreBridge to test route
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("测试功能需要连接 Core")
                                isTestingRoute = false
                            }
                        },
                        enabled = !isTestingRoute
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
                    TextButton(onClick = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("路线已发布")
                        }
                    }) {
                        Icon(Icons.Outlined.Publish, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("发布")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )

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
                            onClick = { selectedStep = step; showStepEditor = true },
                            onToggleEnabled = { coroutineScope.launch { routeRepo.toggleStepEnabled(step) } },
                            onToggleLocked = { coroutineScope.launch { routeRepo.toggleStepLocked(step) } },
                            onDelete = { showDeleteConfirm = step }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }

                // Step detail panel
                if (showStepEditor && selectedStep != null) {
                    StepDetailPanel(
                        step = selectedStep!!,
                        onEdit = { /* TODO: open editor */ },
                        onTest = { /* TODO: single step test */ },
                        onWaitTimeChange = { newTime ->
                            coroutineScope.launch {
                                routeRepo.updateStep(selectedStep!!.copy(waitTimeMs = newTime))
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
                            showDeleteConfirm = null
                            snackbarHostState.showSnackbar("已删除")
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = SmartColors.danger())
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("取消")
                }
            }
        )
    }
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
private fun StepDetailPanel(
    step: RouteStepEntity,
    onEdit: () -> Unit,
    onTest: () -> Unit,
    onWaitTimeChange: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var showWaitTimeEditor by remember { mutableStateOf(false) }
    var waitTimeText by remember { mutableStateOf((step.waitTimeMs / 1000).toString()) }

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

            // Screenshot placeholder
            Surface(
                shape = RoundedCornerShape(16),
                color = SmartColors.textTertiary().copy(alpha = 0.1f),
                modifier = Modifier.fillMaxWidth().height(160.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Screenshot, null, modifier = Modifier.size(36.dp), tint = SmartColors.textTertiary())
                        Text("步骤截图", fontSize = 13.sp, color = SmartColors.textTertiary())
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // Step info
            Text("步骤 ${step.stepIndex}: ${step.summary}", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))

            DetailRow("动作", when (step.type) { "tap" -> "点击"; "input" -> "输入"; else -> step.type })
            DetailRow("定位方式", step.locatorStrategy)
            if (step.locatorValue.isNotEmpty()) DetailRow("定位值", step.locatorValue)
            DetailRow("等待时间", "${step.waitTimeMs}ms")
            DetailRow("来源", when (step.source) { "ai_learned" -> "AI 学习"; "user_edit" -> "手动编辑"; else -> step.source })

            // Wait time editor
            Spacer(Modifier.height(12.dp))
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
                    ) {
                        Text("保存")
                    }
                }
            }

            // AI suggestion
            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(16), color = SmartColors.accent().copy(alpha = 0.08f)) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = SmartColors.accent(), modifier = Modifier.size(20.dp))
                    Column {
                        Text("AI 建议", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = SmartColors.accent())
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (step.locatorStrategy == "coordinate") "建议改为文本定位，提高稳定性" else "当前定位方式较稳定",
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
                    onClick = onTest,
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
