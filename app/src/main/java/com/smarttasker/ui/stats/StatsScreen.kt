package com.smarttasker.ui.stats

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.data.entity.RunRecordEntity
import com.smarttasker.ui.common.EmptyState
import com.smarttasker.ui.common.SmartCard
import com.smarttasker.ui.theme.SmartColors

/**
 * 统计界面 — 现代仪表盘风格
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onBack: () -> Unit = {},
    showBackButton: Boolean = true
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "数据统计",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = SmartColors.accent())
                }
            }

            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = "错误",
                            tint = SmartColors.danger(),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.error!!,
                            color = SmartColors.textSecondary()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.refresh() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SmartColors.accent()
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("重试")
                        }
                    }
                }
            }

            uiState.totalRuns == 0 -> {
                // 空状态
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = Icons.Outlined.BarChart,
                        title = "暂无统计数据",
                        subtitle = "运行任务后，这里将展示详细的运行统计和分析"
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 概览卡片
                    item {
                        OverviewCards(
                            totalRuns = uiState.totalRuns,
                            successRate = uiState.successRate,
                            avgDurationMs = uiState.avgDurationMs,
                            totalModelCalls = uiState.totalModelCalls
                        )
                    }

                    // 成功率图表
                    item {
                        SmartCard {
                            Text(
                                text = "成功率",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            SuccessRateChart(
                                successRate = uiState.successRate,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }

                    // 趋势图表
                    item {
                        SmartCard {
                            Text(
                                text = "运行趋势",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            TrendChart(
                                dailyStats = uiState.dailyStats,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // 任务排名
                    if (uiState.taskStats.isNotEmpty()) {
                        item {
                            SectionTitle(
                                icon = Icons.Outlined.Leaderboard,
                                title = "任务排名"
                            )
                        }

                        item {
                            TaskRankingList(taskStats = uiState.taskStats.take(5))
                        }
                    }

                    // 最近运行
                    if (uiState.recentRuns.isNotEmpty()) {
                        item {
                            SectionTitle(
                                icon = Icons.Outlined.History,
                                title = "最近运行"
                            )
                        }

                        items(uiState.recentRuns.take(5)) { run ->
                            RecentRunTimelineItem(run = run)
                        }
                    }

                    // 底部间距
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

/**
 * 概览卡片 — 2×2 网格，大数字 + 彩色图标背景
 */
@Composable
private fun OverviewCards(
    totalRuns: Int,
    successRate: Float,
    avgDurationMs: Long,
    totalModelCalls: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "总运行",
                value = "$totalRuns",
                icon = Icons.Outlined.PlayArrow,
                accentColor = SmartColors.accent(),
                trend = if (totalRuns > 0) "次" else null,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "成功率",
                value = "${(successRate * 100).toInt()}%",
                icon = Icons.Outlined.CheckCircle,
                accentColor = SmartColors.success(),
                trend = when {
                    successRate >= 0.8f -> "优秀"
                    successRate >= 0.5f -> "良好"
                    else -> "需改善"
                },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "平均耗时",
                value = formatDuration(avgDurationMs),
                icon = Icons.Outlined.Timer,
                accentColor = SmartColors.warning(),
                trend = null,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "模型调用",
                value = "$totalModelCalls",
                icon = Icons.Outlined.Psychology,
                accentColor = SmartColors.danger(),
                trend = if (totalModelCalls > 0) "次" else null,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 统计卡片 — 大数字 + 彩色图标背景 + 趋势标签
 */
@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    trend: String?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = SmartColors.textSecondary(),
                    fontSize = 13.sp
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                lineHeight = 30.sp
            )

            if (trend != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = trend,
                    color = SmartColors.textTertiary(),
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * 分区标题
 */
@Composable
private fun SectionTitle(
    icon: ImageVector,
    title: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = SmartColors.textSecondary()
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = SmartColors.textSecondary()
        )
    }
}

/**
 * 任务排名列表 — 带进度条，按成功率排序
 */
@Composable
private fun TaskRankingList(taskStats: List<TaskStat>) {
    val sortedStats = taskStats.sortedByDescending { it.successRate }

    SmartCard {
        sortedStats.forEachIndexed { index, taskStat ->
            if (index > 0) {
                Divider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = SmartColors.borderSubtle().copy(alpha = 0.5f)
                )
            }
            TaskRankItem(
                taskStat = taskStat,
                rank = index + 1
            )
        }
    }
}

/**
 * 任务排名项 — 排名 + 进度条
 */
@Composable
private fun TaskRankItem(taskStat: TaskStat, rank: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 排名
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    when (rank) {
                        1 -> SmartColors.accent().copy(alpha = 0.15f)
                        2 -> SmartColors.accent().copy(alpha = 0.10f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$rank",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = when (rank) {
                    1 -> SmartColors.accent()
                    2 -> SmartColors.accent()
                    else -> SmartColors.textSecondary()
                }
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 任务信息 + 进度条
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = taskStat.taskName,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1
                )
                Text(
                    text = "${(taskStat.successRate * 100).toInt()}%",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = when {
                        taskStat.successRate >= 0.8f -> SmartColors.success()
                        taskStat.successRate >= 0.5f -> SmartColors.warning()
                        else -> SmartColors.danger()
                    }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 进度条
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(taskStat.successRate.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    when {
                                        taskStat.successRate >= 0.8f -> SmartColors.success()
                                        taskStat.successRate >= 0.5f -> SmartColors.warning()
                                        else -> SmartColors.danger()
                                    },
                                    when {
                                        taskStat.successRate >= 0.8f -> SmartColors.success().copy(alpha = 0.7f)
                                        taskStat.successRate >= 0.5f -> SmartColors.warning().copy(alpha = 0.7f)
                                        else -> SmartColors.danger().copy(alpha = 0.7f)
                                    }
                                )
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${taskStat.totalRuns} 次运行 · 平均 ${formatDuration(taskStat.avgDurationMs)}",
                color = SmartColors.textTertiary(),
                fontSize = 12.sp
            )
        }
    }
}

/**
 * 最近运行项 — 时间线风格
 */
@Composable
private fun RecentRunTimelineItem(run: RunRecordEntity) {
    val statusColor = when (run.status) {
        "success" -> SmartColors.success()
        "failed" -> SmartColors.danger()
        else -> SmartColors.warning()
    }

    val statusIcon = when (run.status) {
        "success" -> Icons.Outlined.CheckCircle
        "failed" -> Icons.Outlined.Cancel
        else -> Icons.Outlined.Schedule
    }

    val statusLabel = when (run.status) {
        "success" -> "成功"
        "failed" -> "失败"
        else -> "运行中"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 时间线指示器
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = statusLabel,
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // 运行信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "任务 ${run.taskId.take(8)}",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatDuration(run.durationMs),
                    color = SmartColors.textTertiary(),
                    fontSize = 12.sp
                )
            }

            // 状态标签
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = statusColor.copy(alpha = 0.10f)
            ) {
                Text(
                    text = statusLabel,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    color = statusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * 格式化时长
 */
private fun formatDuration(ms: Long): String {
    return when {
        ms < 1000 -> "${ms}ms"
        ms < 60000 -> "${ms / 1000}s"
        else -> "${ms / 60000}m ${(ms % 60000) / 1000}s"
    }
}
