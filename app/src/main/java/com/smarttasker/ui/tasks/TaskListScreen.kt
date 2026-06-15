package com.smarttasker.ui.tasks

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

    val allTasks by taskRepo.getAllTasks().collectAsState(initial = emptyList())
    val filteredTasks by when (selectedFilter) {
        TaskFilter.ALL -> taskRepo.getAllTasks()
        TaskFilter.ACTIVE -> taskRepo.getTasksByTrigger("manual")
        TaskFilter.SCHEDULE -> taskRepo.getTasksByTrigger("schedule")
        TaskFilter.PAUSED -> taskRepo.getPausedTasks()
    }.collectAsState(initial = emptyList())

    // Count map for badges
    val manualCount by taskRepo.getTasksByTrigger("manual").collectAsState(initial = emptyList())
    val scheduleCount by taskRepo.getTasksByTrigger("schedule").collectAsState(initial = emptyList())
    val pausedCount by taskRepo.getPausedTasks().collectAsState(initial = emptyList())

    val filterCounts = mapOf(
        TaskFilter.ALL to allTasks.size,
        TaskFilter.ACTIVE to manualCount.size,
        TaskFilter.SCHEDULE to scheduleCount.size,
        TaskFilter.PAUSED to pausedCount.size
    )

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    "任务",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        // Pill-style filter tabs
        PillFilterTabs(
            filters = TaskFilter.values().toList(),
            selectedFilter = selectedFilter,
            onFilterSelected = { selectedFilter = it },
            filterCounts = filterCounts,
            filterLabels = { it.label }
        )

        Spacer(Modifier.height(8.dp))

        if (filteredTasks.isEmpty()) {
            TaskEmptyState(filter = selectedFilter)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredTasks, key = { it.taskId }) { task ->
                    ModernTaskCard(task, onClick = { onTaskClick(task) })
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

// ── Pill-style filter tabs with count badges ──

@Composable
private fun <T> PillFilterTabs(
    filters: List<T>,
    selectedFilter: T,
    onFilterSelected: (T) -> Unit,
    filterCounts: Map<T, Int>,
    filterLabels: (T) -> String
) {
    ScrollableTabRow(
        selectedTabIndex = filters.indexOf(selectedFilter).coerceAtLeast(0),
        containerColor = Color.Transparent,
        edgePadding = 20.dp,
        divider = {},
        indicator = {}
    ) {
        filters.forEach { filter ->
            val isSelected = selectedFilter == filter
            val count = filterCounts[filter] ?: 0

            Tab(
                selected = isSelected,
                onClick = { onFilterSelected(filter) },
                text = {
                    FilterPill(
                        label = filterLabels(filter),
                        count = count,
                        isSelected = isSelected
                    )
                }
            )
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    count: Int,
    isSelected: Boolean
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) SmartColors.accent() else SmartColors.accent().copy(alpha = 0.08f),
        animationSpec = tween(200), label = "pillBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else SmartColors.textSecondary(),
        animationSpec = tween(200), label = "pillText"
    )
    val badgeBg by animateColorAsState(
        targetValue = if (isSelected) Color.White.copy(alpha = 0.25f) else SmartColors.accent().copy(alpha = 0.12f),
        animationSpec = tween(200), label = "badgeBg"
    )
    val badgeText by animateColorAsState(
        targetValue = if (isSelected) Color.White else SmartColors.accent(),
        animationSpec = tween(200), label = "badgeText"
    )

    Surface(
        shape = RoundedCornerShape(50),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
            )
            if (count > 0) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = badgeBg
                ) {
                    Text(
                        text = "$count",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        color = badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ── Modern Task Card ──

@Composable
private fun ModernTaskCard(task: TaskEntity, onClick: () -> Unit) {
    SmartCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App icon letter in colored circle
            val iconLetter = if (task.targetAppName.isNotEmpty()) {
                task.targetAppName.first().uppercaseChar().toString()
            } else {
                task.name.first().uppercaseChar().toString()
            }
            val circleColor = appIconColor(task.targetAppName.ifEmpty { task.name })

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(circleColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = iconLetter,
                    color = circleColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(14.dp))

            // Main content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    task.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (task.description.isNotEmpty() && task.description != task.name) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        task.description,
                        fontSize = 13.sp,
                        color = SmartColors.textSecondary(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Trigger type icon badge
                    val (triggerIcon, triggerLabel) = when (task.triggerType) {
                        "schedule" -> Icons.Outlined.Schedule to "定时"
                        "notification" -> Icons.Outlined.Notifications to "通知"
                        else -> Icons.Outlined.TouchApp to "手动"
                    }
                    TriggerBadge(icon = triggerIcon, label = triggerLabel)

                    if (task.targetAppName.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            task.targetAppName,
                            fontSize = 12.sp,
                            color = SmartColors.textTertiary(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // Status dot indicator
            val (dotColor, statusLabel) = when (task.status) {
                "active" -> SmartColors.success() to "运行中"
                "paused" -> SmartColors.warning() to "已暂停"
                "draft" -> SmartColors.textTertiary() to "草稿"
                else -> SmartColors.textTertiary() to task.status
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusDot(color = dotColor)
                Spacer(Modifier.height(4.dp))
                Text(
                    statusLabel,
                    fontSize = 11.sp,
                    color = dotColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun TriggerBadge(icon: ImageVector, label: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = SmartColors.accent().copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = SmartColors.accent()
            )
            Text(
                label,
                fontSize = 11.sp,
                color = SmartColors.accent(),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}

// ── Empty state with illustration ──

@Composable
private fun TaskEmptyState(filter: TaskFilter) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Illustration: stacked cards icon
        Box(contentAlignment = Alignment.Center) {
            // Background decorative circle
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(SmartColors.accent().copy(alpha = 0.06f))
            )
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint = SmartColors.accent().copy(alpha = 0.4f)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "没有${filter.label}任务",
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = SmartColors.textSecondary()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            when (filter) {
                TaskFilter.ALL -> "在首页创建你的第一个任务"
                TaskFilter.ACTIVE -> "手动触发的任务会在这里显示"
                TaskFilter.SCHEDULE -> "定时任务会在这里显示"
                TaskFilter.PAUSED -> "暂停的任务会在这里显示"
            },
            fontSize = 13.sp,
            color = SmartColors.textTertiary()
        )
    }
}

// ── Utility: deterministic color for app icon ──

private fun appIconColor(name: String): Color {
    val colors = listOf(
        Color(0xFF10A37F), // accent green
        Color(0xFF3B82F6), // blue
        Color(0xFF8B5CF6), // violet
        Color(0xFFF59E0B), // amber
        Color(0xFFEF4444), // red
        Color(0xFFEC4899), // pink
        Color(0xFF06B6D4), // cyan
        Color(0xFF84CC16)  // lime
    )
    val hash = name.hashCode().absoluteValue
    return colors[hash % colors.size]
}

private val Int.absoluteValue: Int get() = if (this < 0) -this else this
