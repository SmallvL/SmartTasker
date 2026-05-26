package com.smarttasker.ui.trialrun

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.data.entity.RouteStepEntity
import com.smarttasker.data.entity.TaskEntity
import com.smarttasker.ui.common.*
import com.smarttasker.ui.theme.SmartColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteLearningResultScreen(
    task: TaskEntity,
    steps: List<TrialStep>,
    onSaveAndEnable: () -> Unit,
    onOpenRouteStudio: () -> Unit,
    onDiscard: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("学习结果", fontWeight = FontWeight.SemiBold) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Success banner
            item {
                SmartCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = SmartColors.success()
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "已学到路线",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 22.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "后续执行将优先使用路线，必要时才调用 AI",
                            fontSize = 14.sp,
                            color = SmartColors.textSecondary()
                        )
                    }
                }
            }

            // Route summary
            item {
                SmartCard {
                    Text("路线摘要", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    steps.forEachIndexed { index, step ->
                        if (step.status == TrialStepStatus.SUCCESS) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12),
                                    color = SmartColors.accent().copy(alpha = 0.1f),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            "${index + 1}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = SmartColors.accent()
                                        )
                                    }
                                }
                                Text(step.summary, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }

            // Stats
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = "成功步骤",
                        value = "${steps.count { it.status == TrialStepStatus.SUCCESS }}",
                        color = SmartColors.success()
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = "风险等级",
                        value = when (task.riskLevel) {
                            "low" -> "低"
                            "medium" -> "中"
                            "high" -> "高"
                            else -> task.riskLevel
                        },
                        color = when (task.riskLevel) {
                            "high" -> SmartColors.danger()
                            "medium" -> SmartColors.warning()
                            else -> SmartColors.success()
                        }
                    )
                }
            }

            // Action buttons
            item {
                Spacer(Modifier.height(8.dp))
                SmartButton(
                    text = "保存并启用",
                    onClick = onSaveAndEnable,
                    icon = Icons.Outlined.CheckCircle
                )
                Spacer(Modifier.height(8.dp))
                SmartSecondaryButton(
                    text = "进入 Route Studio 编辑",
                    onClick = onOpenRouteStudio,
                    icon = Icons.Outlined.Edit
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDiscard,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = SmartColors.textSecondary()
                    )
                ) {
                    Text("放弃路线")
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    color: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = color)
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 13.sp, color = SmartColors.textSecondary())
        }
    }
}
