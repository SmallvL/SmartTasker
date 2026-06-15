package com.smarttasker.ui.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.data.entity.TaskEntity
import com.smarttasker.data.entity.RouteVersionEntity
import com.smarttasker.data.entity.RunRecordEntity
import com.smarttasker.data.repository.TaskRepository
import com.smarttasker.data.repository.RunRepository
import com.smarttasker.data.repository.RouteRepository
import com.smarttasker.data.repository.TemplateRepository
import com.smarttasker.ui.common.*
import com.smarttasker.ui.theme.SmartColors
import com.smarttasker.schedule.AlarmScheduler
import com.smarttasker.core.playback.RoutePlaybackService
import android.content.Intent
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    taskId: String,
    taskRepo: TaskRepository,
    runRepo: RunRepository,
    routeRepo: RouteRepository,
    templateRepo: TemplateRepository? = null,
    onBack: () -> Unit,
    onOpenRouteStudio: (routeId: String, taskName: String) -> Unit,
    onStartTrial: (TaskEntity) -> Unit,
    onRunClick: (RunRecordEntity) -> Unit = {}
) {
    var task by remember { mutableStateOf<TaskEntity?>(null) }
    val runs by runRepo.getRunsForTask(taskId).collectAsState(initial = emptyList())
    val routes by routeRepo.getRouteVersions(taskId).collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Publish feedback state
    var publishBanner by remember { mutableStateOf<PublishBannerInfo?>(null) }

    // Inline editing states
    var editingField by remember { mutableStateOf<String?>(null) }
    var editName by remember { mutableStateOf("") }
    var editDescription by remember { mutableStateOf("") }
    var editTargetApp by remember { mutableStateOf("") }
    var editTriggerType by remember { mutableStateOf("manual") }
    var editTriggerTime by remember { mutableStateOf("") }
    var showTriggerTypeSelector by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Save-as-template feedback
    var templateSaveSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(taskId) { task = taskRepo.getTaskById(taskId) }

    val taskData = task ?: run {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = SmartColors.accent())
        }
        return
    }

    // ===== Trigger Type Selector Dialog =====
    if (showTriggerTypeSelector) {
        AlertDialog(
            onDismissRequest = { showTriggerTypeSelector = false },
            title = { Text("选择触发方式", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Manual card
                    TriggerTypeCard(
                        icon = Icons.Outlined.PlayArrow,
                        title = "手动执行",
                        subtitle = "需要手动启动任务",
                        color = SmartColors.success(),
                        selected = editTriggerType == "manual",
                        onClick = {
                            editTriggerType = "manual"
                            editTriggerTime = ""
                        }
                    )
                    // Schedule card
                    TriggerTypeCard(
                        icon = Icons.Outlined.Schedule,
                        title = "定时执行",
                        subtitle = if (editTriggerTime.isNotEmpty()) "定时 $editTriggerTime" else "按计划自动执行",
                        color = SmartColors.accent(),
                        selected = editTriggerType == "schedule",
                        onClick = {
                            editTriggerType = "schedule"
                        }
                    )
                    // Time picker for schedule
                    if (editTriggerType == "schedule") {
                        OutlinedTextField(
                            value = editTriggerTime,
                            onValueChange = { editTriggerTime = it },
                            label = { Text("执行时间 (如 09:00)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                    // Notification card
                    TriggerTypeCard(
                        icon = Icons.Outlined.Notifications,
                        title = "通知触发",
                        subtitle = "收到通知时自动执行",
                        color = SmartColors.warning(),
                        selected = editTriggerType == "notification",
                        onClick = {
                            editTriggerType = "notification"
                            editTriggerTime = ""
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        val updated = taskData.copy(
                            triggerType = editTriggerType,
                            triggerTime = editTriggerTime
                        )
                        taskRepo.updateTask(updated)
                        task = updated
                        showTriggerTypeSelector = false
                        editingField = null
                    }
                }) { Text("确定", color = SmartColors.accent()) }
            },
            dismissButton = {
                TextButton(onClick = { showTriggerTypeSelector = false }) { Text("取消") }
            }
        )
    }

    // ===== Delete Confirmation Dialog =====
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除任务") },
            text = { Text("确定要删除任务 \"${taskData.name}\" 吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        AlarmScheduler.cancelAlarm(context, taskData.taskId)
                        taskRepo.deleteTask(taskData)
                        showDeleteConfirm = false
                        onBack()
                    }
                }) { Text("删除", color = SmartColors.danger()) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = RoundedCornerShape(14.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    actionOnNewLine = false
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ===== Top App Bar =====
            TopAppBar(
                title = {
                    Text(
                        taskData.name,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = SmartColors.danger())
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // ===== Hero Status Card with Gradient =====
                item {
                    val statusColor = when (taskData.status) {
                        "active" -> SmartColors.success()
                        "paused" -> SmartColors.warning()
                        "draft" -> SmartColors.textTertiary()
                        else -> SmartColors.textTertiary()
                    }
                    val statusLabel = when (taskData.status) {
                        "active" -> "运行中"
                        "paused" -> "已暂停"
                        "draft" -> "草稿"
                        else -> taskData.status
                    }
                    val statusIcon = when (taskData.status) {
                        "active" -> Icons.Outlined.CheckCircle
                        "paused" -> Icons.Outlined.PauseCircle
                        "draft" -> Icons.Outlined.NewReleases
                        else -> Icons.Outlined.Info
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = Color.Transparent
                    ) {
                        Box {
                            // Gradient background
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                statusColor.copy(alpha = 0.12f),
                                                statusColor.copy(alpha = 0.03f)
                                            )
                                        )
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = statusColor.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(24.dp)
                                    )
                            )
                            Column(
                                modifier = Modifier.padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                // Status hero row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            modifier = Modifier.size(48.dp),
                                            shape = CircleShape,
                                            color = statusColor.copy(alpha = 0.15f)
                                        ) {
                                            Icon(
                                                statusIcon,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .padding(10.dp),
                                                tint = statusColor
                                            )
                                        }
                                        Spacer(Modifier.width(14.dp))
                                        Column {
                                            Text(
                                                statusLabel,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = statusColor
                                            )
                                        }
                                    }
                                    StatusPill(
                                        when (taskData.riskLevel) {
                                            "low" -> "低风险"
                                            "medium" -> "中风险"
                                            "high" -> "高风险"
                                            "critical" -> "极高风险"
                                            else -> taskData.riskLevel
                                        },
                                        when (taskData.riskLevel) {
                                            "low" -> SmartColors.success()
                                            "medium" -> SmartColors.warning()
                                            "high" -> SmartColors.danger()
                                            "critical" -> SmartColors.danger()
                                            else -> SmartColors.textTertiary()
                                        }
                                    )
                                }

                                // Inline-editable detail rows
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    // Task name — inline editable
                                    InlineEditableRow(
                                        icon = Icons.Outlined.Label,
                                        label = "任务名称",
                                        value = taskData.name,
                                        isEditing = editingField == "name",
                                        editValue = editName,
                                        onEditValueChange = { editName = it },
                                        onStartEdit = {
                                            editName = taskData.name
                                            editingField = "name"
                                        },
                                        onConfirmEdit = {
                                            coroutineScope.launch {
                                                val updated = taskData.copy(name = editName)
                                                taskRepo.updateTask(updated)
                                                task = updated
                                                editingField = null
                                            }
                                        },
                                        onCancelEdit = { editingField = null }
                                    )

                                    // Description — inline editable
                                    InlineEditableRow(
                                        icon = Icons.Outlined.Description,
                                        label = "描述",
                                        value = taskData.description.ifEmpty { "未设置" },
                                        isEditing = editingField == "description",
                                        editValue = editDescription,
                                        onEditValueChange = { editDescription = it },
                                        onStartEdit = {
                                            editDescription = taskData.description
                                            editingField = "description"
                                        },
                                        onConfirmEdit = {
                                            coroutineScope.launch {
                                                val updated = taskData.copy(description = editDescription)
                                                taskRepo.updateTask(updated)
                                                task = updated
                                                editingField = null
                                            }
                                        },
                                        onCancelEdit = { editingField = null }
                                    )

                                    // Target app — inline editable
                                    InlineEditableRow(
                                        icon = Icons.Outlined.Apps,
                                        label = "目标应用",
                                        value = taskData.targetAppName.ifEmpty { "未设置" },
                                        isEditing = editingField == "targetApp",
                                        editValue = editTargetApp,
                                        onEditValueChange = { editTargetApp = it },
                                        onStartEdit = {
                                            editTargetApp = taskData.targetAppName
                                            editingField = "targetApp"
                                        },
                                        onConfirmEdit = {
                                            coroutineScope.launch {
                                                val updated = taskData.copy(targetAppName = editTargetApp)
                                                taskRepo.updateTask(updated)
                                                task = updated
                                                editingField = null
                                            }
                                        },
                                        onCancelEdit = { editingField = null }
                                    )

                                    // Trigger type — tap to open selector
                                    InlineEditableRow(
                                        icon = Icons.Outlined.TouchApp,
                                        label = "触发方式",
                                        value = when (taskData.triggerType) {
                                            "schedule" -> "定时 ${taskData.triggerTime}"
                                            "notification" -> "通知触发"
                                            else -> "手动执行"
                                        },
                                        isEditing = false,
                                        editValue = "",
                                        onEditValueChange = {},
                                        onStartEdit = {
                                            editTriggerType = taskData.triggerType
                                            editTriggerTime = taskData.triggerTime
                                            showTriggerTypeSelector = true
                                        },
                                        onConfirmEdit = {},
                                        onCancelEdit = {}
                                    )

                                    // Created time — read-only
                                    IconDetailRow(
                                        icon = Icons.Outlined.Schedule,
                                        label = "创建时间",
                                        value = formatDate(taskData.createdAt)
                                    )
                                }
                            }
                        }
                    }
                }

                // ===== Publish Success Banner =====
                item {
                    AnimatedVisibility(
                        visible = publishBanner != null,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { -it })
                    ) {
                        publishBanner?.let { banner ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                color = SmartColors.success().copy(alpha = 0.08f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = SmartColors.success(),
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(Modifier.width(14.dp))
                                    Column {
                                        Text(
                                            "路线发布成功",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp,
                                            color = SmartColors.success()
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            "v${banner.version} · ${banner.stepCount} 个步骤 · ${banner.time}",
                                            fontSize = 12.sp,
                                            color = SmartColors.textSecondary()
                                        )
                                    }
                                    Spacer(Modifier.weight(1f))
                                    IconButton(
                                        onClick = { publishBanner = null },
                                        modifier = Modifier.size(28.dp)
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
                }

                // ===== Template Save Success Banner =====
                item {
                    AnimatedVisibility(
                        visible = templateSaveSuccess,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { -it })
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = SmartColors.accent().copy(alpha = 0.08f)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = SmartColors.accent(),
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Text(
                                        "已保存为模板",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        color = SmartColors.accent()
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "可在模板库中查看和使用",
                                        fontSize = 12.sp,
                                        color = SmartColors.textSecondary()
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                IconButton(
                                    onClick = { templateSaveSuccess = false },
                                    modifier = Modifier.size(28.dp)
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

                // ===== Action Buttons =====
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Primary action: Trial
                        Button(
                            onClick = { onStartTrial(taskData) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SmartColors.accent(),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Outlined.PlayArrow, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("试跑任务", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }

                        // Secondary actions row
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Enable / Pause
                            val isActive = taskData.status == "active"
                            FilledTonalButton(
                                onClick = {
                                    coroutineScope.launch {
                                        if (isActive) {
                                            taskRepo.pauseTask(taskData)
                                            if (taskData.triggerType == "schedule") {
                                                AlarmScheduler.cancelAlarm(context, taskData.taskId)
                                            }
                                        } else {
                                            taskRepo.activateTask(taskData)
                                            if (taskData.triggerType == "schedule") {
                                                val publishedRoute = routeRepo.getLatestPublishedRoute(taskData.taskId)
                                                AlarmScheduler.scheduleAlarm(context, taskData, publishedRoute?.routeId ?: "")
                                            } else {
                                                // manual / notification: immediately execute once
                                                val publishedRoute = routeRepo.getLatestPublishedRoute(taskData.taskId)
                                                if (publishedRoute != null) {
                                                    val intent = Intent(context, RoutePlaybackService::class.java).apply {
                                                        action = RoutePlaybackService.ACTION_PLAY_ROUTE
                                                        putExtra(RoutePlaybackService.EXTRA_ROUTE_ID, publishedRoute.routeId)
                                                    }
                                                    context.startForegroundService(intent)
                                                }
                                            }
                                            // Show snackbar for enable action
                                            if (!isActive) {
                                                snackbarHostState.showSnackbar("任务已启用，正在执行...")
                                            }
                                        }
                                        task = taskData.copy(status = if (isActive) "paused" else "active")
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (isActive)
                                        SmartColors.warning().copy(alpha = 0.12f)
                                    else
                                        SmartColors.success().copy(alpha = 0.12f),
                                    contentColor = if (isActive) SmartColors.warning() else SmartColors.success()
                                )
                            ) {
                                Icon(
                                    if (isActive) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                    null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (isActive) "暂停" else "启用",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }

                            // Save as Template
                            FilledTonalButton(
                                onClick = {
                                    coroutineScope.launch {
                                        if (templateRepo != null) {
                                            try {
                                                templateRepo.createFromTask(
                                                    name = taskData.name,
                                                    description = taskData.description,
                                                    category = "通用",
                                                    taskId = taskData.taskId,
                                                    routeRepo = routeRepo
                                                )
                                                templateSaveSuccess = true
                                            } catch (e: Exception) {
                                                snackbarHostState.showSnackbar("保存模板失败：未找到已发布路线")
                                            }
                                        } else {
                                            snackbarHostState.showSnackbar("模板功能不可用")
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = SmartColors.accent().copy(alpha = 0.12f),
                                    contentColor = SmartColors.accent()
                                )
                            ) {
                                Icon(Icons.Outlined.Save, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("保存为模板", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                        }
                    }
                }

                // ===== Route Versions =====
                item { SectionHeader("路线版本") }
                if (routes.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.Route,
                            title = "还没有路线",
                            subtitle = "完成首次试跑后会自动生成路线"
                        )
                    }
                } else {
                    items(routes) { route ->
                        RouteVersionCard(
                            route = route,
                            routeRepo = routeRepo,
                            templateRepo = templateRepo,
                            isLatestPublished = route.status == "published" &&
                                    routes.filter { it.status == "published" }
                                        .maxByOrNull { it.publishedAt ?: 0L }?.routeId == route.routeId,
                            onClick = { onOpenRouteStudio(route.routeId, task?.name ?: taskId) },
                            onPublish = {
                                coroutineScope.launch {
                                    routeRepo.publishRoute(route)
                                    val stepCount = routeRepo.getStepsForRouteSync(route.routeId).size
                                    publishBanner = PublishBannerInfo(
                                        version = route.version,
                                        stepCount = stepCount,
                                        time = formatDate(System.currentTimeMillis())
                                    )
                                }
                            },
                            onSaveAsTemplate = {
                                coroutineScope.launch {
                                    if (templateRepo != null) {
                                        try {
                                            templateRepo.createFromRoute(
                                                name = taskData.name,
                                                description = taskData.description,
                                                category = "通用",
                                                routeId = route.routeId,
                                                routeRepo = routeRepo
                                            )
                                            templateSaveSuccess = true
                                        } catch (e: Exception) {
                                            snackbarHostState.showSnackbar("保存模板失败")
                                        }
                                    }
                                }
                            },
                            snackbarHostState = snackbarHostState
                        )
                    }
                }

                // ===== Recent Runs =====
                item { SectionHeader("最近运行") }
                if (runs.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.History,
                            title = "还没有运行记录",
                            subtitle = "启用任务后将自动记录运行历史"
                        )
                    }
                } else {
                    items(runs.take(5)) { run ->
                        RunRecordCard(
                            run = run,
                            onViewDetails = { onRunClick(run) }
                        )
                    }
                }

                // Bottom spacing
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

// ===== Data class for publish banner =====
private data class PublishBannerInfo(
    val version: String,
    val stepCount: Int,
    val time: String
)

// ===== Inline Editable Row =====
@Composable
private fun InlineEditableRow(
    icon: ImageVector,
    label: String,
    value: String,
    isEditing: Boolean,
    editValue: String,
    onEditValueChange: (String) -> Unit,
    onStartEdit: () -> Unit,
    onConfirmEdit: () -> Unit,
    onCancelEdit: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .padding(6.dp),
                tint = SmartColors.textSecondary()
            )
        }
        if (isEditing) {
            OutlinedTextField(
                value = editValue,
                onValueChange = onEditValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium)
            )
            IconButton(
                onClick = onConfirmEdit,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = "确认",
                    modifier = Modifier.size(18.dp),
                    tint = SmartColors.success()
                )
            }
            IconButton(
                onClick = onCancelEdit,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "取消",
                    modifier = Modifier.size(18.dp),
                    tint = SmartColors.textTertiary()
                )
            }
        } else {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 11.sp, color = SmartColors.textTertiary(), letterSpacing = 0.3.sp)
                Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            IconButton(
                onClick = onStartEdit,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "编辑",
                    modifier = Modifier.size(14.dp),
                    tint = SmartColors.textTertiary()
                )
            }
        }
    }
}

// ===== Trigger Type Card =====
@Composable
private fun TriggerTypeCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) Modifier.border(
                    width = 2.dp,
                    color = color,
                    shape = RoundedCornerShape(16.dp)
                ) else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) color.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = color.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = color
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) color else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = SmartColors.textSecondary()
                )
            }
            if (selected) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// ===== Icon-based detail row (read-only) =====
@Composable
private fun IconDetailRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .padding(6.dp),
                tint = SmartColors.textSecondary()
            )
        }
        Column {
            Text(label, fontSize = 11.sp, color = SmartColors.textTertiary(), letterSpacing = 0.3.sp)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ===== Route Version Card (Enhanced) =====
@Composable
private fun RouteVersionCard(
    route: RouteVersionEntity,
    routeRepo: RouteRepository,
    templateRepo: TemplateRepository?,
    isLatestPublished: Boolean,
    onClick: () -> Unit,
    onPublish: () -> Unit,
    onSaveAsTemplate: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    // Load step count for this route
    var stepCount by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(route.routeId) {
        stepCount = routeRepo.getStepsForRouteSync(route.routeId).size
    }

    SmartCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Version badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (route.status == "published")
                        SmartColors.success().copy(alpha = 0.1f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        "v${route.version}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (route.status == "published") SmartColors.success()
                        else SmartColors.textSecondary()
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        route.changeSummary.ifEmpty {
                            when (route.source) {
                                "ai_learned" -> "AI 自动学习"
                                "user_edit" -> "用户编辑"
                                "manual_recording" -> "手动录制"
                                else -> route.source
                            }
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Step count
                        Text(
                            "$stepCount 步",
                            fontSize = 11.sp,
                            color = SmartColors.textTertiary()
                        )
                        // Last modified time
                        if (route.publishedAt != null) {
                            Text(
                                formatDate(route.publishedAt),
                                fontSize = 11.sp,
                                color = SmartColors.textTertiary()
                            )
                        } else {
                            Text(
                                formatDate(route.createdAt),
                                fontSize = 11.sp,
                                color = SmartColors.textTertiary()
                            )
                        }
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                if (isLatestPublished) {
                    // Active published badge
                    Surface(
                        shape = RoundedCornerShape(100),
                        color = SmartColors.success().copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(SmartColors.success())
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "当前生效",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SmartColors.success()
                            )
                        }
                    }
                } else if (route.status == "published") {
                    StatusPill("已发布", SmartColors.success())
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill("草稿", SmartColors.textTertiary())
                        // Publish button for drafts
                        TextButton(
                            onClick = onPublish,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                        ) {
                            Text(
                                "发布",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SmartColors.accent()
                            )
                        }
                    }
                }
            }
        }

        // "另存为模板" option for published routes
        if (route.status == "published" && templateRepo != null) {
            Spacer(Modifier.height(10.dp))
            Divider(color = SmartColors.borderSubtle().copy(alpha = 0.5f))
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSaveAsTemplate() },
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Save,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = SmartColors.accent()
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "另存为模板",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = SmartColors.accent()
                )
            }
        }
    }
}

// ===== Run Record Card (Timeline Style, Enhanced) =====
@Composable
private fun RunRecordCard(
    run: RunRecordEntity,
    onViewDetails: () -> Unit
) {
    val isSuccess = run.status == "success"
    val isFailed = run.status == "failed"
    val indicatorColor = when {
        isSuccess -> SmartColors.success()
        isFailed -> SmartColors.danger()
        else -> SmartColors.textTertiary()
    }
    val statusLabel = when (run.status) {
        "success" -> "成功"
        "failed" -> "失败"
        "running" -> "运行中"
        "cancelled" -> "已取消"
        "blocked_by_safety" -> "安全拦截"
        else -> run.status
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Timeline indicator
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(28.dp),
                shape = CircleShape,
                color = indicatorColor.copy(alpha = 0.12f)
            ) {
                Icon(
                    when {
                        isSuccess -> Icons.Outlined.CheckCircle
                        isFailed -> Icons.Outlined.Cancel
                        run.status == "running" -> Icons.Outlined.HourglassTop
                        else -> Icons.Outlined.Info
                    },
                    contentDescription = null,
                    modifier = Modifier
                        .size(28.dp)
                        .padding(5.dp),
                    tint = indicatorColor
                )
            }
            // Timeline line (only show if not last)
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(SmartColors.borderSubtle())
            )
        }

        Spacer(Modifier.width(14.dp))

        // Content
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    run.diagnosisSummary.ifEmpty {
                        when (run.status) {
                            "success" -> "执行完成"
                            "failed" -> "执行失败"
                            else -> "执行中..."
                        }
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(statusLabel, indicatorColor)
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        formatDate(run.startedAt),
                        fontSize = 11.sp,
                        color = SmartColors.textTertiary()
                    )
                    if (run.durationMs > 0) {
                        Text(
                            formatDuration(run.durationMs),
                            fontSize = 11.sp,
                            color = SmartColors.textTertiary()
                        )
                    }
                    if (run.modelCalls > 0) {
                        Text(
                            "${run.modelCalls} 次调用",
                            fontSize = 11.sp,
                            color = SmartColors.textTertiary()
                        )
                    }
                }
                // "查看详情" link for failed runs
                if (isFailed) {
                    Text(
                        "查看详情",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = SmartColors.accent(),
                        modifier = Modifier.clickable { onViewDetails() }
                    )
                }
            }
        }
    }
}

// ===== Date formatting helper =====
private fun formatDate(timestamp: Long): String {
    return try {
        val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
        sdf.format(Date(timestamp))
    } catch (e: Exception) {
        ""
    }
}

// ===== Duration formatting helper =====
private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}
