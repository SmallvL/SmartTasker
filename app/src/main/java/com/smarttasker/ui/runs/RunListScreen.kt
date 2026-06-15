package com.smarttasker.ui.runs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.data.entity.RunRecordEntity
import com.smarttasker.data.repository.RunRepository
import com.smarttasker.data.repository.TaskRepository
import com.smarttasker.ui.common.*
import com.smarttasker.ui.stats.StatsScreen
import com.smarttasker.ui.stats.StatsViewModel
import com.smarttasker.ui.theme.SmartColors
import java.text.SimpleDateFormat
import java.util.*

enum class RunFilter(val label: String) {
    ALL("全部"), SUCCESS("成功"), FAILED("失败"), MODEL("AI 调用")
}

enum class RunTab(val label: String, val icon: ImageVector) {
    RUNS("运行记录", Icons.Outlined.History),
    STATS("数据统计", Icons.Outlined.BarChart)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunListScreen(
    runRepo: RunRepository,
    taskRepo: TaskRepository? = null,
    onRunClick: (RunRecordEntity) -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(RunTab.RUNS) }
    var selectedFilter by remember { mutableStateOf(RunFilter.ALL) }

    // ── Stats ViewModel (created lazily when stats tab is selected) ──
    val context = LocalContext.current
    val statsViewModel = remember {
        if (taskRepo != null) {
            StatsViewModel(
                application = context.applicationContext as android.app.Application,
                runRepo = runRepo,
                taskRepo = taskRepo
            )
        } else null
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Top App Bar ──
        TopAppBar(
            title = {
                Text(
                    selectedTab.label,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        // ── Tab Row ──
        ScrollableTabRow(
            selectedTabIndex = RunTab.values().indexOf(selectedTab),
            containerColor = Color.Transparent,
            edgePadding = 20.dp,
            divider = {},
            indicator = {}
        ) {
            RunTab.values().forEach { tab ->
                val isSelected = selectedTab == tab
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) SmartColors.accent() else SmartColors.accent().copy(alpha = 0.08f),
                    animationSpec = tween(200), label = "tabBg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else SmartColors.textSecondary(),
                    animationSpec = tween(200), label = "tabText"
                )

                Tab(
                    selected = isSelected,
                    onClick = { selectedTab = tab },
                    text = {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = bgColor
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    tab.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = textColor
                                )
                                Text(
                                    text = tab.label,
                                    color = textColor,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Tab Content ──
        when (selectedTab) {
            RunTab.RUNS -> {
                RunListContent(
                    runRepo = runRepo,
                    selectedFilter = selectedFilter,
                    onFilterSelected = { selectedFilter = it },
                    onRunClick = onRunClick
                )
            }
            RunTab.STATS -> {
                if (statsViewModel != null) {
                    StatsScreen(
                        viewModel = statsViewModel,
                        onBack = {},
                        showBackButton = false
                    )
                } else {
                    // No TaskRepository available — show empty state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(
                            icon = Icons.Outlined.BarChart,
                            title = "暂无统计数据",
                            subtitle = "需要提供 TaskRepository 才能显示统计信息"
                        )
                    }
                }
            }
        }
    }
}

// ── Run List Content (extracted from original RunListScreen) ──

@Composable
private fun RunListContent(
    runRepo: RunRepository,
    selectedFilter: RunFilter,
    onFilterSelected: (RunFilter) -> Unit,
    onRunClick: (RunRecordEntity) -> Unit
) {
    val allRuns by runRepo.getAllRuns().collectAsState(initial = emptyList())
    val filteredRuns by when (selectedFilter) {
        RunFilter.ALL -> runRepo.getAllRuns()
        RunFilter.SUCCESS -> runRepo.getRunsByStatus("success")
        RunFilter.FAILED -> runRepo.getRunsByStatus("failed")
        RunFilter.MODEL -> runRepo.getAllRuns()
    }.collectAsState(initial = emptyList())

    val successCount by runRepo.getRunsByStatus("success").collectAsState(initial = emptyList())
    val failedCount by runRepo.getRunsByStatus("failed").collectAsState(initial = emptyList())

    val filterCounts = mapOf(
        RunFilter.ALL to allRuns.size,
        RunFilter.SUCCESS to successCount.size,
        RunFilter.FAILED to failedCount.size,
        RunFilter.MODEL to allRuns.count { it.modelCalls > 0 }
    )

    // Pill-style filter tabs
    RunPillFilterTabs(
        filters = RunFilter.values().toList(),
        selectedFilter = selectedFilter,
        onFilterSelected = onFilterSelected,
        filterCounts = filterCounts,
        filterLabels = { it.label }
    )

    Spacer(Modifier.height(8.dp))

    if (filteredRuns.isEmpty()) {
        RunEmptyState(filter = selectedFilter)
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filteredRuns, key = { it.runId }) { run ->
                TimelineRunCard(
                    run = run,
                    isLast = filteredRuns.last() == run,
                    onClick = { onRunClick(run) }
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ── Pill-style filter tabs ──

@Composable
private fun <T> RunPillFilterTabs(
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
                    RunFilterPill(
                        label = filterLabels(filter),
                        count = count,
                        isSelected = isSelected,
                        accentColor = when (filter) {
                            is RunFilter -> when (filter) {
                                RunFilter.SUCCESS -> SmartColors.success()
                                RunFilter.FAILED -> SmartColors.danger()
                                RunFilter.MODEL -> Color(0xFF8B5CF6)
                                else -> SmartColors.accent()
                            }
                            else -> SmartColors.accent()
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun RunFilterPill(
    label: String,
    count: Int,
    isSelected: Boolean,
    accentColor: Color
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else accentColor.copy(alpha = 0.08f),
        animationSpec = tween(200), label = "pillBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else SmartColors.textSecondary(),
        animationSpec = tween(200), label = "pillText"
    )
    val badgeBg by animateColorAsState(
        targetValue = if (isSelected) Color.White.copy(alpha = 0.25f) else accentColor.copy(alpha = 0.12f),
        animationSpec = tween(200), label = "badgeBg"
    )
    val badgeText by animateColorAsState(
        targetValue = if (isSelected) Color.White else accentColor,
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

// ── Timeline-style Run Card ──

@Composable
private fun TimelineRunCard(
    run: RunRecordEntity,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }
    val isFailed = run.status == "failed"
    val isSuccess = run.status == "success"
    val isRunning = run.status == "running"

    val (statusColor, statusIcon, statusLabel) = when (run.status) {
        "success" -> Triple(SmartColors.success(), Icons.Outlined.CheckCircle, "成功")
        "failed" -> Triple(SmartColors.danger(), Icons.Outlined.Error, "失败")
        "running" -> Triple(SmartColors.accent(), Icons.Outlined.Sync, "运行中")
        "cancelled" -> Triple(SmartColors.textTertiary(), Icons.Outlined.Cancel, "已取消")
        else -> Triple(SmartColors.textTertiary(), Icons.Outlined.Help, run.status)
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        // ── Left: Timeline indicator ──
        val connectorColor = SmartColors.borderSubtle()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp)
        ) {
            // Top connector line
            Canvas(modifier = Modifier.width(2.dp).height(8.dp)) {
                drawLine(
                    color = connectorColor,
                    start = Offset(size.width / 2f, 0f),
                    end = Offset(size.width / 2f, size.height),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round
                )
            }

            // Status dot
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )

            // Bottom connector line
            if (!isLast) {
                Canvas(modifier = Modifier.width(2.dp).height(32.dp)) {
                    drawLine(
                        color = connectorColor,
                        start = Offset(size.width / 2f, 0f),
                        end = Offset(size.width / 2f, size.height),
                        strokeWidth = 2f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        // ── Right: Card content ──
        SmartCard(
            onClick = onClick,
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Status icon
                Icon(
                    statusIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = statusColor
                )

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Diagnosis summary
                    Text(
                        run.diagnosisSummary.ifEmpty { "任务执行" },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(4.dp))

                    // Time + duration row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            dateFormat.format(Date(run.startedAt)),
                            fontSize = 12.sp,
                            color = SmartColors.textTertiary()
                        )
                        if (run.durationMs > 0) {
                            Text(
                                "·",
                                fontSize = 12.sp,
                                color = SmartColors.textTertiary()
                            )
                            Text(
                                formatDuration(run.durationMs),
                                fontSize = 12.sp,
                                color = SmartColors.textTertiary()
                            )
                        }
                    }

                    // Model calls count
                    if (run.modelCalls > 0) {
                        Spacer(Modifier.height(6.dp))
                        ModelCallsBadge(count = run.modelCalls)
                    }

                    // Failed run: red accent with "查看详情" hint
                    if (isFailed) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SmartColors.danger().copy(alpha = 0.06f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    run.diagnosisSuggestion.ifEmpty { "执行失败" },
                                    fontSize = 12.sp,
                                    color = SmartColors.danger(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "查看详情",
                                    fontSize = 12.sp,
                                    color = SmartColors.danger(),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Status pill on right
                Column(horizontalAlignment = Alignment.End) {
                    StatusPill(statusLabel, statusColor)
                }
            }
        }
    }
}

@Composable
private fun ModelCallsBadge(count: Int) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF8B5CF6).copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                Icons.Outlined.SmartToy,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = Color(0xFF8B5CF6)
            )
            Text(
                "$count 次 AI 调用",
                fontSize = 11.sp,
                color = Color(0xFF8B5CF6),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}

// ── Empty state with illustration ──

@Composable
private fun RunEmptyState(filter: RunFilter) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Illustration: timeline-style decorative element
        Box(contentAlignment = Alignment.Center) {
            // Background decorative circle
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(SmartColors.accent().copy(alpha = 0.06f))
            )
            Icon(
                Icons.Outlined.History,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint = SmartColors.accent().copy(alpha = 0.4f)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            when (filter) {
                RunFilter.ALL -> "没有运行记录"
                RunFilter.SUCCESS -> "没有成功的运行"
                RunFilter.FAILED -> "没有失败的运行"
                RunFilter.MODEL -> "没有 AI 调用记录"
            },
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = SmartColors.textSecondary()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            when (filter) {
                RunFilter.ALL -> "运行任务后会在这里显示"
                RunFilter.SUCCESS -> "成功完成的运行会在这里显示"
                RunFilter.FAILED -> "失败的运行会在这里显示"
                RunFilter.MODEL -> "包含 AI 调用的运行会在这里显示"
            },
            fontSize = 13.sp,
            color = SmartColors.textTertiary()
        )
    }
}
