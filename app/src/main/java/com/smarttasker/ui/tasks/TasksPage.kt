package com.smarttasker.ui.tasks

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 任务数据类
 */
data class TaskItem(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val type: TaskType,
    val status: AutoLxbTaskItemStatus,
    val lastRunTime: String?,
    val nextRunTime: String?,
    val runCount: Int,
    val successRate: Float
)

/**
 * 任务类型
 */
enum class TaskType {
    QUICK,      // 快速任务
    SCHEDULED,  // 定时任务
    NOTIFICATION // 通知触发
}

/**
 * 任务状态
 */
enum class AutoLxbTaskItemStatus {
    IDLE,
    RUNNING,
    SUCCESS,
    FAILED,
    SCHEDULED,
    PAUSED
}

/**
 * 任务筛选类型
 */
enum class AutoLxbTaskFilter {
    ALL,
    QUICK,
    SCHEDULED,
    NOTIFICATION,
    RUNNING,
    COMPLETED,
    FAILED
}

/**
 * 任务列表页面
 */
@Composable
fun TasksPage(
    tasks: List<TaskItem>,
    currentFilter: AutoLxbTaskFilter,
    taskCounts: Map<AutoLxbTaskFilter, Int> = emptyMap(),
    onFilterChange: (AutoLxbTaskFilter) -> Unit,
    onTaskClick: (String) -> Unit,
    onCreateTask: () -> Unit,
    onQuickTask: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 顶部操作栏
        TaskActionBar(
            onCreateTask = onCreateTask,
            onQuickTask = onQuickTask
        )
        
        // 筛选标签
        TaskFilterTabs(
            currentFilter = currentFilter,
            onFilterChange = onFilterChange,
            taskCounts = taskCounts.ifEmpty { getTaskCounts(tasks) }
        )
        
        // 任务列表
        val filteredTasks = filterTasks(tasks, currentFilter)
        
        if (filteredTasks.isEmpty()) {
            EmptyTasksView(
                filter = currentFilter,
                onCreateTask = onCreateTask
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredTasks) { task ->
                    TaskCard(
                        task = task,
                        onClick = { onTaskClick(task.id) }
                    )
                }
            }
        }
    }
}

/**
 * 任务操作栏
 */
@Composable
private fun TaskActionBar(
    onCreateTask: () -> Unit,
    onQuickTask: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 快速任务按钮
            OutlinedButton(
                onClick = onQuickTask,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "⚡ 快速任务",
                    fontSize = 14.sp
                )
            }
            
            // 创建任务按钮
            Button(
                onClick = onCreateTask,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "+ 创建任务",
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * 任务筛选标签
 */
@Composable
private fun TaskFilterTabs(
    currentFilter: AutoLxbTaskFilter,
    onFilterChange: (AutoLxbTaskFilter) -> Unit,
    taskCounts: Map<AutoLxbTaskFilter, Int>
) {
    ScrollableTabRow(
        selectedTabIndex = AutoLxbTaskFilter.entries.indexOf(currentFilter),
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        edgePadding = 16.dp
    ) {
        AutoLxbTaskFilter.entries.forEach { filter ->
            Tab(
                selected = currentFilter == filter,
                onClick = { onFilterChange(filter) },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = getFilterLabel(filter),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        val count = taskCounts[filter] ?: 0
                        if (count > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = CircleShape,
                                color = if (currentFilter == filter) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Text(
                                    text = count.toString(),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (currentFilter == filter) {
                                        Color.White
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}

/**
 * 任务卡片
 */
@Composable
private fun TaskCard(
    task: TaskItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 头部：图标、名称、状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 图标
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when (task.type) {
                                TaskType.QUICK -> Color(0xFF1565C0).copy(alpha = 0.1f)
                                TaskType.SCHEDULED -> Color(0xFF2E7D32).copy(alpha = 0.1f)
                                TaskType.NOTIFICATION -> Color(0xFFEF6C00).copy(alpha = 0.1f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = task.icon,
                        fontSize = 24.sp
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // 名称和描述
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = task.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // Only show description if it differs from the name
                    if (task.description != task.name && task.description.isNotBlank()) {
                        Text(
                            text = task.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                // 状态指示器
                TaskStatusIndicator(status = task.status)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 底部信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 任务类型标签
                TaskTypeTag(type = task.type)
                
                // 统计信息
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (task.runCount > 0) {
                        TaskStatItem(
                            label = "运行次数",
                            value = task.runCount.toString()
                        )
                    }
                    
                    if (task.successRate > 0) {
                        TaskStatItem(
                            label = "成功率",
                            value = "${(task.successRate * 100).toInt()}%"
                        )
                    }
                }
            }
            
            // 时间信息
            if (task.lastRunTime != null || task.nextRunTime != null) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (task.lastRunTime != null) {
                        Text(
                            text = "上次运行: ${task.lastRunTime}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    
                    if (task.nextRunTime != null) {
                        Text(
                            text = "下次运行: ${task.nextRunTime}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * 任务状态指示器
 */
@Composable
private fun TaskStatusIndicator(status: AutoLxbTaskItemStatus) {
    val (color, text) = when (status) {
        AutoLxbTaskItemStatus.IDLE -> Color.Gray to "空闲"
        AutoLxbTaskItemStatus.RUNNING -> Color(0xFFEF6C00) to "运行中"
        AutoLxbTaskItemStatus.SUCCESS -> Color(0xFF2E7D32) to "成功"
        AutoLxbTaskItemStatus.FAILED -> Color(0xFFC62828) to "失败"
        AutoLxbTaskItemStatus.SCHEDULED -> Color(0xFF1565C0) to "已计划"
        AutoLxbTaskItemStatus.PAUSED -> Color.Gray to "已暂停"
    }
    
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            
            Spacer(modifier = Modifier.width(4.dp))
            
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

/**
 * 任务类型标签
 */
@Composable
private fun TaskTypeTag(type: TaskType) {
    val (color, text) = when (type) {
        TaskType.QUICK -> Color(0xFF1565C0) to "快速任务"
        TaskType.SCHEDULED -> Color(0xFF2E7D32) to "定时任务"
        TaskType.NOTIFICATION -> Color(0xFFEF6C00) to "通知触发"
    }
    
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

/**
 * 任务统计项
 */
@Composable
private fun TaskStatItem(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

/**
 * 空任务视图
 */
@Composable
private fun EmptyTasksView(
    filter: AutoLxbTaskFilter,
    onCreateTask: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = when (filter) {
                AutoLxbTaskFilter.ALL -> "📋"
                AutoLxbTaskFilter.QUICK -> "⚡"
                AutoLxbTaskFilter.SCHEDULED -> "⏰"
                AutoLxbTaskFilter.NOTIFICATION -> "🔔"
                AutoLxbTaskFilter.RUNNING -> "▶"
                AutoLxbTaskFilter.COMPLETED -> "✓"
                AutoLxbTaskFilter.FAILED -> "✗"
            },
            fontSize = 64.sp
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = when (filter) {
                AutoLxbTaskFilter.ALL -> "暂无任务"
                AutoLxbTaskFilter.QUICK -> "暂无快速任务"
                AutoLxbTaskFilter.SCHEDULED -> "暂无定时任务"
                AutoLxbTaskFilter.NOTIFICATION -> "暂无通知触发任务"
                AutoLxbTaskFilter.RUNNING -> "暂无运行中的任务"
                AutoLxbTaskFilter.COMPLETED -> "暂无已完成的任务"
                AutoLxbTaskFilter.FAILED -> "暂无失败的任务"
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "点击下方按钮创建您的第一个任务",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onCreateTask,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "创建任务",
                fontSize = 16.sp
            )
        }
    }
}

/**
 * 获取筛选标签
 */
private fun getFilterLabel(filter: AutoLxbTaskFilter): String {
    return when (filter) {
        AutoLxbTaskFilter.ALL -> "全部"
        AutoLxbTaskFilter.QUICK -> "快速任务"
        AutoLxbTaskFilter.SCHEDULED -> "定时任务"
        AutoLxbTaskFilter.NOTIFICATION -> "通知触发"
        AutoLxbTaskFilter.RUNNING -> "运行中"
        AutoLxbTaskFilter.COMPLETED -> "已完成"
        AutoLxbTaskFilter.FAILED -> "失败"
    }
}

/**
 * 获取任务计数
 */
private fun getTaskCounts(tasks: List<TaskItem>): Map<AutoLxbTaskFilter, Int> {
    return mapOf(
        AutoLxbTaskFilter.ALL to tasks.size,
        AutoLxbTaskFilter.QUICK to tasks.count { it.type == TaskType.QUICK },
        AutoLxbTaskFilter.SCHEDULED to tasks.count { it.type == TaskType.SCHEDULED },
        AutoLxbTaskFilter.NOTIFICATION to tasks.count { it.type == TaskType.NOTIFICATION },
        AutoLxbTaskFilter.RUNNING to tasks.count { it.status == AutoLxbTaskItemStatus.RUNNING },
        AutoLxbTaskFilter.COMPLETED to tasks.count { it.status == AutoLxbTaskItemStatus.SUCCESS },
        AutoLxbTaskFilter.FAILED to tasks.count { it.status == AutoLxbTaskItemStatus.FAILED }
    )
}

/**
 * 筛选任务
 */
private fun filterTasks(tasks: List<TaskItem>, filter: AutoLxbTaskFilter): List<TaskItem> {
    return when (filter) {
        AutoLxbTaskFilter.ALL -> tasks
        AutoLxbTaskFilter.QUICK -> tasks.filter { it.type == TaskType.QUICK }
        AutoLxbTaskFilter.SCHEDULED -> tasks.filter { it.type == TaskType.SCHEDULED }
        AutoLxbTaskFilter.NOTIFICATION -> tasks.filter { it.type == TaskType.NOTIFICATION }
        AutoLxbTaskFilter.RUNNING -> tasks.filter { it.status == AutoLxbTaskItemStatus.RUNNING }
        AutoLxbTaskFilter.COMPLETED -> tasks.filter { it.status == AutoLxbTaskItemStatus.SUCCESS }
        AutoLxbTaskFilter.FAILED -> tasks.filter { it.status == AutoLxbTaskItemStatus.FAILED }
    }
}
