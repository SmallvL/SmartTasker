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

    LaunchedEffect(taskId) { task = taskRepo.getTaskById(taskId) }

    if (task == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(task!!.name, fontWeight = FontWeight.SemiBold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "返回") } },
            actions = {
                IconButton(onClick = {
                    coroutineScope.launch {
                        // Cancel any pending alarm before deleting
                        AlarmScheduler.cancelAlarm(context, task!!.taskId)
                        taskRepo.deleteTask(task!!)
                        onBack()
                    }
                }) {
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
                            if (task!!.description.isNotEmpty()) Text(task!!.description, fontSize = 14.sp, color = SmartColors.textSecondary())
                        }
                        StatusPill(
                            when(task!!.status) { "active"->"运行中"; "paused"->"已暂停"; "draft"->"草稿"; else->task!!.status },
                            when(task!!.status) { "active"->SmartColors.success(); "paused"->SmartColors.warning(); else->SmartColors.textTertiary() }
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    DetailRow("目标应用", task!!.targetAppName.ifEmpty { "未设置" })
                    DetailRow("触发方式", when(task!!.triggerType) { "schedule"->"定时 ${task!!.triggerTime}"; "notification"->"通知触发"; else->"手动执行" })
                    DetailRow("风险等级", when(task!!.riskLevel) { "low"->"低"; "medium"->"中"; "high"->"高"; else->task!!.riskLevel })
                }
            }

            // Actions
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onStartTrial(task!!) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16),
                        colors = ButtonDefaults.buttonColors(containerColor = SmartColors.accent())) {
                        Icon(Icons.Outlined.PlayArrow, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("试跑")
                    }
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                val wasActive = task!!.status == "active"
                                if (wasActive) {
                                    taskRepo.pauseTask(task!!)
                                    // Cancel alarm when pausing
                                    if (task!!.triggerType == "schedule") {
                                        AlarmScheduler.cancelAlarm(context, task!!.taskId)
                                    }
                                } else {
                                    taskRepo.activateTask(task!!)
                                    // Schedule alarm when activating a scheduled task
                                    if (task!!.triggerType == "schedule") {
                                        val publishedRoute = routeRepo.getLatestPublishedRoute(task!!.taskId)
                                        AlarmScheduler.scheduleAlarm(context, task!!, publishedRoute?.routeId ?: "")
                                    }
                                }
                                task = task!!.copy(status = if (wasActive) "paused" else "active")
                            }
                        },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(16)
                    ) {
                        Icon(if (task!!.status == "active") Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text(if (task!!.status == "active") "暂停" else "启用")
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
