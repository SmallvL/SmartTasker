package com.smarttasker.ui.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.data.entity.TaskEntity
import com.smarttasker.data.repository.TaskRepository
import com.smarttasker.data.repository.RunRepository
import com.smarttasker.data.repository.RouteRepository
import com.smarttasker.ui.common.*
import com.smarttasker.ui.theme.SmartColors
import com.smarttasker.schedule.AlarmScheduler
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    taskId: String,
    taskRepo: TaskRepository,
    runRepo: RunRepository,
    routeRepo: RouteRepository,
    onBack: () -> Unit,
    onOpenRouteStudio: (routeId: String, taskName: String) -> Unit,
    onStartTrial: (TaskEntity) -> Unit
) {
    var task by remember { mutableStateOf<TaskEntity?>(null) }
    val runs by runRepo.getRunsForTask(taskId).collectAsState(initial = emptyList())
    val routes by routeRepo.getRouteVersions(taskId).collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Edit dialog state
    var showEditDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editDescription by remember { mutableStateOf("") }
    var editTargetApp by remember { mutableStateOf("") }
    var editTriggerType by remember { mutableStateOf("manual") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(taskId) { task = taskRepo.getTaskById(taskId) }

    val taskData = task ?: run {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    // Edit dialog
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("编辑任务", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("任务名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editDescription,
                        onValueChange = { editDescription = it },
                        label = { Text("描述") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    OutlinedTextField(
                        value = editTargetApp,
                        onValueChange = { editTargetApp = it },
                        label = { Text("目标应用") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Trigger type selector
                    Text("触发方式", fontSize = 14.sp, color = SmartColors.textSecondary())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("manual" to "手动", "schedule" to "定时", "notification" to "通知").forEach { (type, label) ->
                            FilterChip(
                                selected = editTriggerType == type,
                                onClick = { editTriggerType = type },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        val updated = taskData.copy(
                            name = editName,
                            description = editDescription,
                            targetAppName = editTargetApp,
                            triggerType = editTriggerType
                        )
                        taskRepo.updateTask(updated)
                        task = updated
                        showEditDialog = false
                    }
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // Delete confirmation dialog
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
                }) {
                    Text("删除", color = SmartColors.danger())
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(taskData.name, fontWeight = FontWeight.SemiBold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "返回") } },
            actions = {
                IconButton(onClick = {
                    editName = taskData.name
                    editDescription = taskData.description
                    editTargetApp = taskData.targetAppName
                    editTriggerType = taskData.triggerType
                    showEditDialog = true
                }) {
                    Icon(Icons.Outlined.Edit, contentDescription = "编辑")
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = SmartColors.danger())
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Task info
            item {
                SmartCard {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            if (taskData.description.isNotEmpty()) Text(taskData.description, fontSize = 14.sp, color = SmartColors.textSecondary())
                        }
                        StatusPill(
                            when(taskData.status) { "active"->"运行中"; "paused"->"已暂停"; "draft"->"草稿"; else->taskData.status },
                            when(taskData.status) { "active"->SmartColors.success(); "paused"->SmartColors.warning(); else->SmartColors.textTertiary() }
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    DetailRow("目标应用", taskData.targetAppName.ifEmpty { "未设置" })
                    DetailRow("触发方式", when(taskData.triggerType) { "schedule"->"定时 ${taskData.triggerTime}"; "notification"->"通知触发"; else->"手动执行" })
                    DetailRow("风险等级", when(taskData.riskLevel) { "low"->"低"; "medium"->"中"; "high"->"高"; else->taskData.riskLevel })
                }
            }

            // Actions
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onStartTrial(taskData) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16),
                        colors = ButtonDefaults.buttonColors(containerColor = SmartColors.accent())) {
                        Icon(Icons.Outlined.PlayArrow, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("试跑")
                    }
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                val wasActive = taskData.status == "active"
                                if (wasActive) {
                                    taskRepo.pauseTask(taskData)
                                    if (taskData.triggerType == "schedule") {
                                        AlarmScheduler.cancelAlarm(context, taskData.taskId)
                                    }
                                } else {
                                    taskRepo.activateTask(taskData)
                                    if (taskData.triggerType == "schedule") {
                                        val publishedRoute = routeRepo.getLatestPublishedRoute(taskData.taskId)
                                        AlarmScheduler.scheduleAlarm(context, taskData, publishedRoute?.routeId ?: "")
                                    }
                                }
                                task = taskData.copy(status = if (wasActive) "paused" else "active")
                            }
                        },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(16)
                    ) {
                        Icon(if (taskData.status == "active") Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text(if (taskData.status == "active") "暂停" else "启用")
                    }
                    OutlinedButton(
                        onClick = {
                            editName = taskData.name
                            editDescription = taskData.description
                            editTargetApp = taskData.targetAppName
                            editTriggerType = taskData.triggerType
                            showEditDialog = true
                        },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(16)
                    ) {
                        Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("编辑")
                    }
                }
            }

            // Routes
            item { SectionHeader("路线版本") }
            if (routes.isEmpty()) {
                item { SmartCard { Text("还没有路线", fontSize = 14.sp, color = SmartColors.textSecondary()); Spacer(Modifier.height(8.dp)); Text("完成首次试跑后会自动生成路线", fontSize = 13.sp, color = SmartColors.textTertiary()) } }
            } else {
                items(routes.size) { index ->
                    val route = routes[index]
                    SmartCard(onClick = { onOpenRouteStudio(route.routeId, task?.name ?: taskId) }) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column { Text("v${route.version}", fontWeight = FontWeight.Medium); Text(route.changeSummary.ifEmpty { route.source }, fontSize = 13.sp, color = SmartColors.textSecondary()) }
                            StatusPill(when(route.status) { "published"->"已发布"; "draft"->"草稿"; else->route.status }, if (route.status == "published") SmartColors.success() else SmartColors.textTertiary())
                        }
                    }
                }
            }

            // Recent runs
            item { SectionHeader("最近运行") }
            if (runs.isEmpty()) {
                item { SmartCard { Text("还没有运行记录", fontSize = 14.sp, color = SmartColors.textSecondary()) } }
            } else {
                items(runs.take(5).size) { index ->
                    val run = runs[index]
                    SmartCard {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column { Text(run.diagnosisSummary.ifEmpty { "执行完成" }, fontSize = 14.sp, fontWeight = FontWeight.Medium); Text("${run.durationMs / 1000}s", fontSize = 12.sp, color = SmartColors.textTertiary()) }
                            StatusPill(when(run.status) { "success"->"成功"; "failed"->"失败"; else->run.status }, when(run.status) { "success"->SmartColors.success(); "failed"->SmartColors.danger(); else->SmartColors.textTertiary() })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp, color = SmartColors.textSecondary())
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
