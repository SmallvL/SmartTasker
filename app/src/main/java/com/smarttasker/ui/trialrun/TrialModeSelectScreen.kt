package com.smarttasker.ui.trialrun

import androidx.compose.foundation.layout.*
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
import com.smarttasker.ui.common.SmartCard
import com.smarttasker.ui.theme.SmartColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrialModeSelectScreen(
    taskName: String,
    onAiMode: () -> Unit,
    onManualMode: () -> Unit,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("首次试跑", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Outlined.Close, contentDescription = "取消")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Task info
            SmartCard {
                Text(taskName, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "选择执行模式",
                    fontSize = 14.sp,
                    color = SmartColors.textSecondary()
                )
            }

            Spacer(Modifier.height(8.dp))

            // AI Mode card
            Card(
                onClick = onAiMode,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16),
                colors = CardDefaults.cardColors(
                    containerColor = SmartColors.accent().copy(alpha = 0.08f)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = SmartColors.accent(),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("AI 自动执行", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text(
                                "AI 分析屏幕并自主操作",
                                fontSize = 13.sp,
                                color = SmartColors.textSecondary()
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "• AI 截图分析屏幕内容\n• 自动决定下一步操作\n• 需要配置 VLM API\n• 适合复杂/未知流程",
                        fontSize = 13.sp,
                        color = SmartColors.textSecondary(),
                        lineHeight = 20.sp
                    )
                }
            }

            // Manual Recording Mode card
            Card(
                onClick = onManualMode,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16),
                colors = CardDefaults.cardColors(
                    containerColor = SmartColors.success().copy(alpha = 0.08f)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.FiberManualRecord,
                            contentDescription = null,
                            tint = SmartColors.success(),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("手动录制", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text(
                                "你操作，我学习",
                                fontSize = 13.sp,
                                color = SmartColors.textSecondary()
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "• 你手动操作手机\n• 系统录制你的操作\n• 无需 API，零成本\n• 适合固定流程",
                        fontSize = 13.sp,
                        color = SmartColors.textSecondary(),
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
