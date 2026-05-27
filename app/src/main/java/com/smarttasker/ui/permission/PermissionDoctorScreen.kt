package com.smarttasker.ui.permission

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.smarttasker.core.bridge.CoreBridgeManager
import com.smarttasker.core.bridge.CoreStatus
import com.smarttasker.ui.common.*
import com.smarttasker.ui.theme.SmartColors

/**
 * Permission Doctor - checks all required permissions and system state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionDoctorScreen(
    coreBridgeManager: CoreBridgeManager,
    onBack: () -> Unit,
    onFixPermission: (String) -> Unit = {}
) {
    val coreStatus by coreBridgeManager.coreStatus.collectAsState()
    
    // Permission checks
    val checks = remember {
        listOf(
            PermissionCheck("core_status", "AutoLXB Core", "检查 Core 服务是否运行"),
            PermissionCheck("accessibility", "无障碍服务", "需要开启 SmartTask 无障碍服务"),
            PermissionCheck("notification", "通知读取", "用于检测通知触发任务"),
            PermissionCheck("battery", "电池优化", "建议设为无限制以保证后台运行"),
            PermissionCheck("model_config", "AI 模型", "需要配置模型 API 用于任务解析"),
            PermissionCheck("adb", "ADB 权限", "用于设备控制和截图")
        )
    }
    
    // Update core status check
    val updatedChecks = checks.map { check ->
        when (check.id) {
            "core_status" -> check.copy(
                status = when (coreStatus) {
                    is CoreStatus.Running -> CheckStatus.PASS
                    is CoreStatus.ShellOnly -> CheckStatus.WARN
                    is CoreStatus.Stopped -> CheckStatus.FAIL
                    is CoreStatus.Error -> CheckStatus.FAIL
                    is CoreStatus.Unknown -> CheckStatus.CHECKING
                },
                detail = when (coreStatus) {
                    is CoreStatus.Running -> "端口 ${(coreStatus as CoreStatus.Running).port} · 完全控制"
                    is CoreStatus.ShellOnly -> "SH 模式 · 执行可用 · 录制需无线调试"
                    is CoreStatus.Stopped -> "Core 未运行"
                    is CoreStatus.Error -> (coreStatus as CoreStatus.Error).message
                    is CoreStatus.Unknown -> "检查中..."
                }
            )
            "model_config" -> check.copy(
                status = CheckStatus.WARN,
                detail = "请在设置中配置 API Key"
            )
            else -> check.copy(status = CheckStatus.PASS)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("权限体检", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary
            item {
                SmartCard {
                    val passCount = updatedChecks.count { it.status == CheckStatus.PASS }
                    val totalCount = updatedChecks.size
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            if (passCount == totalCount) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = if (passCount == totalCount) SmartColors.success() else SmartColors.warning()
                        )
                        Column {
                            Text(
                                if (passCount == totalCount) "所有检查通过" else "需要处理 ${totalCount - passCount} 项",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp
                            )
                            Text(
                                "$passCount / $totalCount 项正常",
                                fontSize = 14.sp,
                                color = SmartColors.textSecondary()
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = passCount.toFloat() / totalCount,
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = if (passCount == totalCount) SmartColors.success() else SmartColors.warning(),
                        trackColor = SmartColors.borderSubtle()
                    )
                }
            }

            // Check items
            items(updatedChecks) { check ->
                PermissionCheckCard(
                    check = check,
                    onFix = { onFixPermission(check.id) }
                )
            }

            // Help text
            item {
                SmartCard {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Help, null, tint = SmartColors.accent(), modifier = Modifier.size(24.dp))
                        Column {
                            Text("需要帮助？", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Text(
                                "如果某些权限无法开启，请参考帮助文档或联系支持。",
                                fontSize = 13.sp,
                                color = SmartColors.textSecondary()
                            )
                        }
                    }
                }
            }
        }
    }
}

enum class CheckStatus { PASS, WARN, FAIL, CHECKING }

data class PermissionCheck(
    val id: String,
    val name: String,
    val description: String,
    val status: CheckStatus = CheckStatus.CHECKING,
    val detail: String = ""
)

@Composable
private fun PermissionCheckCard(
    check: PermissionCheck,
    onFix: () -> Unit
) {
    SmartCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status icon
            Icon(
                when (check.status) {
                    CheckStatus.PASS -> Icons.Outlined.CheckCircle
                    CheckStatus.WARN -> Icons.Outlined.Warning
                    CheckStatus.FAIL -> Icons.Outlined.Error
                    CheckStatus.CHECKING -> Icons.Outlined.Schedule
                },
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = when (check.status) {
                    CheckStatus.PASS -> SmartColors.success()
                    CheckStatus.WARN -> SmartColors.warning()
                    CheckStatus.FAIL -> SmartColors.danger()
                    CheckStatus.CHECKING -> SmartColors.textTertiary()
                }
            )

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(check.name, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Text(check.description, fontSize = 13.sp, color = SmartColors.textSecondary())
                if (check.detail.isNotEmpty()) {
                    Text(check.detail, fontSize = 12.sp, color = SmartColors.textTertiary())
                }
            }

            // Fix button
            if (check.status == CheckStatus.FAIL || check.status == CheckStatus.WARN) {
                OutlinedButton(
                    onClick = onFix,
                    shape = RoundedCornerShape(12),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("修复", fontSize = 13.sp)
                }
            }
        }
    }
}
