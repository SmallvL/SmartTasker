package com.smarttasker.ui.runs

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
import com.smarttasker.data.entity.RunRecordEntity
import com.smarttasker.data.repository.RunRepository
import com.smarttasker.ui.common.*
import com.smarttasker.ui.theme.SmartColors
import java.text.SimpleDateFormat
import java.util.*

enum class RunFilter(val label: String) {
    ALL("全部"), SUCCESS("成功"), FAILED("失败"), MODEL("AI 调用")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunListScreen(
    runRepo: RunRepository,
    onRunClick: (RunRecordEntity) -> Unit = {}
) {
    var selectedFilter by remember { mutableStateOf(RunFilter.ALL) }

    val runs by when (selectedFilter) {
        RunFilter.ALL -> runRepo.getAllRuns()
        RunFilter.SUCCESS -> runRepo.getRunsByStatus("success")
        RunFilter.FAILED -> runRepo.getRunsByStatus("failed")
        RunFilter.MODEL -> runRepo.getAllRuns()
    }.collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("运行记录", fontWeight = FontWeight.SemiBold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        ScrollableTabRow(
            selectedTabIndex = selectedFilter.ordinal,
            containerColor = MaterialTheme.colorScheme.background,
            edgePadding = 20.dp,
            divider = {}
        ) {
            RunFilter.values().forEach { filter ->
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

        if (runs.isEmpty()) {
            EmptyState(icon = Icons.Outlined.History, title = "没有运行记录", subtitle = "运行任务后会在这里显示")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(runs) { run ->
                    RunCard(run, onClick = { onRunClick(run) })
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun RunCard(run: RunRecordEntity, onClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }

    SmartCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(run.diagnosisSummary.ifEmpty { "任务执行" }, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(dateFormat.format(Date(run.startedAt)), fontSize = 13.sp, color = SmartColors.textTertiary())
                if (run.status == "failed" && run.diagnosisSuggestion.isNotEmpty()) {
                    Text(run.diagnosisSuggestion, fontSize = 13.sp, color = SmartColors.warning(), modifier = Modifier.padding(top = 4.dp))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusPill(
                    when (run.status) {
                        "success" -> "成功"
                        "failed" -> "失败"
                        "running" -> "运行中"
                        "cancelled" -> "已取消"
                        else -> run.status
                    },
                    when (run.status) {
                        "success" -> SmartColors.success()
                        "failed" -> SmartColors.danger()
                        "running" -> SmartColors.accent()
                        else -> SmartColors.textTertiary()
                    }
                )
                if (run.durationMs > 0) {
                    Text("${run.durationMs / 1000}s", fontSize = 12.sp, color = SmartColors.textTertiary(), modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
