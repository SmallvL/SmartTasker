package com.smarttasker.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.ui.common.SmartCard
import com.smarttasker.ui.theme.SmartColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToPermissions: () -> Unit = {},
    onNavigateToSafetyPolicy: () -> Unit = {},
    onNavigateToCostBudget: () -> Unit = {},
    onNavigateToModelConfig: () -> Unit = {},
    onNavigateToPromptSettings: () -> Unit = {},
    onNavigateToCoreStart: () -> Unit = {},
    onNavigateToDeviceInfo: () -> Unit = {},
    onNavigateToImportExport: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToDebugLog: () -> Unit = {}
) {
    var darkMode by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }

        item {
            Text("权限与安全", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                color = SmartColors.textTertiary(), modifier = Modifier.padding(start = 4.dp))
        }
        item {
            SmartCard {
                SettingsItem(Icons.Outlined.Security, "权限体检", "检查运行环境", onClick = onNavigateToPermissions)
                Divider(color = SmartColors.borderSubtle(), modifier = Modifier.padding(vertical = 12.dp))
                SettingsItem(Icons.Outlined.Shield, "安全策略", "高风险动作确认", onClick = onNavigateToSafetyPolicy)
                Divider(color = SmartColors.borderSubtle(), modifier = Modifier.padding(vertical = 12.dp))
                SettingsItem(Icons.Outlined.HealthAndSafety, "成本预算", "模型调用费用", onClick = onNavigateToCostBudget)
            }
        }

        item {
            Text("AI 配置", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                color = SmartColors.textTertiary(), modifier = Modifier.padding(start = 4.dp))
        }
        item {
            SmartCard {
                SettingsItem(Icons.Outlined.SmartToy, "模型配置", "API 地址和密钥", onClick = onNavigateToModelConfig)
                Divider(color = SmartColors.borderSubtle(), modifier = Modifier.padding(vertical = 12.dp))
                SettingsItem(Icons.Outlined.Psychology, "Prompt 设置", "自定义提示词", onClick = onNavigateToPromptSettings)
            }
        }

        item {
            Text("Core 引擎", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                color = SmartColors.textTertiary(), modifier = Modifier.padding(start = 4.dp))
        }
        item {
            SmartCard {
                SettingsItem(Icons.Outlined.PowerSettingsNew, "Core 启动", "启动/停止自动化引擎", onClick = onNavigateToCoreStart)
                Divider(color = SmartColors.borderSubtle(), modifier = Modifier.padding(vertical = 12.dp))
                SettingsItem(Icons.Outlined.PhoneAndroid, "设备连接", "ADB 无线调试", onClick = onNavigateToDeviceInfo)
            }
        }

        item {
            Text("通用", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                color = SmartColors.textTertiary(), modifier = Modifier.padding(start = 4.dp))
        }
        item {
            SmartCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.DarkMode, contentDescription = null,
                        tint = SmartColors.textSecondary(), modifier = Modifier.size(22.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("深色模式", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Text("跟随系统或手动切换", fontSize = 13.sp, color = SmartColors.textTertiary())
                    }
                    Switch(
                        checked = darkMode,
                        onCheckedChange = { darkMode = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SmartColors.accent(),
                            checkedTrackColor = SmartColors.accent().copy(alpha = 0.3f)
                        )
                    )
                }
                Divider(color = SmartColors.borderSubtle(), modifier = Modifier.padding(vertical = 12.dp))
                SettingsItem(Icons.Outlined.ImportExport, "导入导出", "备份和恢复数据", onClick = onNavigateToImportExport)
                Divider(color = SmartColors.borderSubtle(), modifier = Modifier.padding(vertical = 12.dp))
                SettingsItem(Icons.Outlined.BugReport, "Debug 日志", "查看运行日志", onClick = onNavigateToDebugLog)
                Divider(color = SmartColors.borderSubtle(), modifier = Modifier.padding(vertical = 12.dp))
                SettingsItem(Icons.Outlined.Info, "关于", "版本 1.0.0", onClick = onNavigateToAbout)
            }
        }
    }
}

@Composable
private fun SettingsItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = SmartColors.textSecondary(),
            modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(subtitle, fontSize = 13.sp, color = SmartColors.textTertiary())
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null,
            tint = SmartColors.textTertiary(), modifier = Modifier.size(18.dp))
    }
}
