package com.smarttasker.ui.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.smarttasker.ui.common.*
import com.smarttasker.ui.theme.SmartColors

enum class TaskFilter(val label: String) {
    ALL("全部"), ACTIVE("手动"), SCHEDULE("定时"), PAUSED("已暂停")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    taskRepo: TaskRepository,
    onTaskClick: (TaskEntity) -> Unit = {}
) {
    var selectedFilter by remember { mutableStateOf(TaskFilter.ALL) }

    val tasks by when (selectedFilter) {
        TaskFilter.ALL -> taskRepo.getAllTasks()
        TaskFilter.ACTIVE -> taskRepo.getTasksByTrigger("manual")
        TaskFilter.SCHEDULE -> taskRepo.getTasksByTrigger("schedule")
        TaskFilter.PAUSED -> taskRepo.getPausedTasks()
    }.collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("任务", fontWeight = FontWeight.SemiBold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        ScrollableTabRow(
            selectedTabIndex = selectedFilter.ordinal,
            containerColor = MaterialTheme.colorScheme.background,
            edgePadding = 20.dp,
            divider = {}
        ) {
            TaskFilter.values().forEach { filter ->
                Tab(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    text = {
                        Text(
                            filter.label,
                            fontWeight = if (selectedFilter == filter) FontWeight.Medium else FontWeight.Normal,
                            color = if (selectedFilter == filter) SmartColors.accent() else SmartColors.textSecondary()
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (tasks.isEmpty()) {
            EmptyState(icon = Icons.Outlined.CheckCircle, title = "没有${selectedFilter.label}任务", subtitle = "在首页创建你的第一个任务")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(tasks) { task ->
                    TaskCard(task, onClick = { onTaskClick(task) })
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun TaskCard(task: TaskEntity, onClick: () -> Unit) {
    SmartCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(task.name, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                if (task.description != task.name) {
                    Text(task.description.ifEmpty { "无描述" }, fontSize = 13.sp, color = SmartColors.textSecondary(), maxLines = 2)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(
                        when (task.triggerType) {
                            "schedule" -> "定时"
                            "notification" -> "通知"
                            else -> "手动"
                        },
                        SmartColors.textTertiary()
                    )
                    Spacer(Modifier.width(8.dp))
                    if (task.targetAppName.isNotEmpty()) {
                        StatusPill(task.targetAppName, SmartColors.accent())
                    }
                }
            }
            StatusPill(
                when (task.status) {
                    "active" -> "运行中"
                    "paused" -> "已暂停"
                    "draft" -> "草稿"
                    else -> task.status
                },
                when (task.status) {
                    "active" -> SmartColors.success()
                    "paused" -> SmartColors.warning()
                    else -> SmartColors.textTertiary()
                }
            )
        }
    }
}
