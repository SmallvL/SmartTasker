package com.smarttasker.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.data.entity.RunRecordEntity
import com.smarttasker.ui.theme.*

/**
 * 统计界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "数据统计",
                        color = LinearTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = LinearTextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新",
                            tint = LinearTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LinearBgPanel
                )
            )
        },
        containerColor = LinearBgMarketing
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = LinearBrandIndigo)
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
                            imageVector = Icons.Default.Error,
                            contentDescription = "错误",
                            tint = LinearRed,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.error!!,
                            color = LinearTextSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.refresh() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LinearBrandIndigo
                            )
                        ) {
                            Text("重试")
                        }
                    }
                }
            }
            
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
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
                        ChartCard(title = "成功率") {
                            SuccessRateChart(
                                successRate = uiState.successRate,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                    
                    // 趋势图表
                    item {
                        ChartCard(title = "运行趋势") {
                            TrendChart(
                                dailyStats = uiState.dailyStats,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    
                    // 任务排名
                    if (uiState.taskStats.isNotEmpty()) {
                        item {
                            Text(
                                text = "任务排名",
                                color = LinearTextPrimary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp
                            )
                        }
                        
                        items(uiState.taskStats.take(5)) { taskStat ->
                            TaskRankItem(taskStat = taskStat)
                        }
                    }
                    
                    // 最近运行
                    if (uiState.recentRuns.isNotEmpty()) {
                        item {
                            Text(
                                text = "最近运行",
                                color = LinearTextPrimary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp
                            )
                        }
                        
                        items(uiState.recentRuns.take(5)) { run ->
                            RecentRunItem(run = run)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 概览卡片
 */
@Composable
private fun OverviewCards(
    totalRuns: Int,
    successRate: Float,
    avgDurationMs: Long,
    totalModelCalls: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            title = "总运行",
            value = "$totalRuns",
            icon = Icons.Default.PlayArrow,
            color = LinearBrandIndigo,
            modifier = Modifier.weight(1f)
        )
        
        StatCard(
            title = "成功率",
            value = "${(successRate * 100).toInt()}%",
            icon = Icons.Default.CheckCircle,
            color = LinearGreen,
            modifier = Modifier.weight(1f)
        )
        
        StatCard(
            title = "平均耗时",
            value = formatDuration(avgDurationMs),
            icon = Icons.Default.Timer,
            color = LinearAccentViolet,
            modifier = Modifier.weight(1f)
        )
        
        StatCard(
            title = "模型调用",
            value = "$totalModelCalls",
            icon = Icons.Default.Psychology,
            color = LinearYellow,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 统计卡片
 */
@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = LinearBgSurface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = value,
                color = LinearTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            
            Text(
                text = title,
                color = LinearTextTertiary,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * 图表卡片
 */
@Composable
private fun ChartCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = LinearBgSurface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                color = LinearTextPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            content()
        }
    }
}

/**
 * 任务排名项
 */
@Composable
private fun TaskRankItem(taskStat: TaskStat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = LinearBgSurface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 任务信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = taskStat.taskName,
                    color = LinearTextPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                
                Text(
                    text = "${taskStat.totalRuns} 次运行",
                    color = LinearTextTertiary,
                    fontSize = 12.sp
                )
            }
            
            // 成功率
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "${(taskStat.successRate * 100).toInt()}%",
                    color = if (taskStat.successRate >= 0.8f) LinearGreen else LinearRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                
                Text(
                    text = "成功率",
                    color = LinearTextTertiary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * 最近运行项
 */
@Composable
private fun RecentRunItem(run: RunRecordEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = LinearBgSurface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态图标
            Icon(
                imageVector = when (run.status) {
                    "success" -> Icons.Default.CheckCircle
                    "failed" -> Icons.Default.Error
                    else -> Icons.Default.Schedule
                },
                contentDescription = run.status,
                tint = when (run.status) {
                    "success" -> LinearGreen
                    "failed" -> LinearRed
                    else -> LinearYellow
                },
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 运行信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "任务 ${run.taskId.take(8)}",
                    color = LinearTextPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                
                Text(
                    text = formatDuration(run.durationMs),
                    color = LinearTextTertiary,
                    fontSize = 12.sp
                )
            }
            
            // 状态标签
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when (run.status) {
                            "success" -> LinearGreen.copy(alpha = 0.2f)
                            "failed" -> LinearRed.copy(alpha = 0.2f)
                            else -> LinearYellow.copy(alpha = 0.2f)
                        }
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = when (run.status) {
                        "success" -> "成功"
                        "failed" -> "失败"
                        else -> "运行中"
                    },
                    color = when (run.status) {
                        "success" -> LinearGreen
                        "failed" -> LinearRed
                        else -> LinearYellow
                    },
                    fontSize = 12.sp
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
