package com.smarttasker.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.smarttasker.data.repository.SettingsRepository
import com.smarttasker.ui.common.IconBox
import com.smarttasker.ui.common.SectionHeaderWithIcon
import com.smarttasker.ui.common.SettingsDivider
import com.smarttasker.ui.common.SmartCard
import com.smarttasker.ui.common.StatusPill
import com.smarttasker.ui.theme.SmartColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyPolicyScreen(
    settingsRepo: SettingsRepository,
    onBack: () -> Unit
) {
    val safetySettings by settingsRepo.safetySettings.collectAsState(initial = SettingsRepository.SafetySettings())
    val scope = rememberCoroutineScope()
    var settings by remember(safetySettings) { mutableStateOf(safetySettings) }

    val sectionColor = Color(0xFF5B6EF5)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("安全策略", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Explanation card with icon box
            item {
                SmartCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IconBox(
                            icon = Icons.Outlined.Security,
                            color = sectionColor
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "安全策略配置",
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp
                            )
                            Text(
                                "配置不同风险等级任务的处理方式",
                                fontSize = 13.sp,
                                color = SmartColors.textTertiary()
                            )
                        }
                    }
                }
            }

            // Section: 高风险操作
            item {
                SectionHeaderWithIcon(
                    icon = Icons.Outlined.Warning,
                    title = "高风险操作",
                    color = Color(0xFFEF4444)
                )
            }

            item {
                SmartCard {
                    // 发送消息
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBox(
                            icon = Icons.Outlined.Send,
                            color = Color(0xFFF59E0B)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("发送消息", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Text("允许任务代为发送消息", fontSize = 13.sp, color = SmartColors.textTertiary())
                        }
                        Switch(
                            checked = settings.allowSend,
                            onCheckedChange = {
                                settings = settings.copy(allowSend = it)
                                scope.launch { settingsRepo.saveSafetySettings(settings.copy(allowSend = it)) }
                            }
                        )
                    }

                    SettingsDivider()

                    // 删除内容
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBox(
                            icon = Icons.Outlined.Delete,
                            color = Color(0xFFEF4444)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("删除内容", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Text("允许任务删除文件或记录", fontSize = 13.sp, color = SmartColors.textTertiary())
                        }
                        Switch(
                            checked = settings.allowDelete,
                            onCheckedChange = {
                                settings = settings.copy(allowDelete = it)
                                scope.launch { settingsRepo.saveSafetySettings(settings.copy(allowDelete = it)) }
                            }
                        )
                    }

                    SettingsDivider()

                    // 下单支付
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBox(
                            icon = Icons.Outlined.Payment,
                            color = Color(0xFFEC4899)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("下单支付", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Text("允许任务进行支付操作", fontSize = 13.sp, color = SmartColors.textTertiary())
                        }
                        Switch(
                            checked = settings.allowPayment,
                            onCheckedChange = {},
                            enabled = false
                        )
                    }
                }
            }

            // Section: 确认策略
            item {
                SectionHeaderWithIcon(
                    icon = Icons.Outlined.VerifiedUser,
                    title = "确认策略",
                    color = Color(0xFF5B6EF5)
                )
            }

            item {
                SmartCard {
                    // 每次执行前确认
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBox(
                            icon = Icons.Outlined.CheckCircle,
                            color = Color(0xFF5B6EF5)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("每次执行前确认", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Text("高风险操作需要手动确认", fontSize = 13.sp, color = SmartColors.textTertiary())
                        }
                        Switch(
                            checked = settings.confirmHighRisk,
                            onCheckedChange = {
                                settings = settings.copy(confirmHighRisk = it)
                                scope.launch { settingsRepo.saveSafetySettings(settings.copy(confirmHighRisk = it)) }
                            }
                        )
                    }

                    SettingsDivider()

                    // 自动确认低风险
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBox(
                            icon = Icons.Outlined.AutoAwesome,
                            color = Color(0xFF8B5CF6)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("自动确认低风险", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Text("低风险任务自动执行无需确认", fontSize = 13.sp, color = SmartColors.textTertiary())
                        }
                        Switch(
                            checked = settings.autoConfirmLowRisk,
                            onCheckedChange = {
                                settings = settings.copy(autoConfirmLowRisk = it)
                                scope.launch { settingsRepo.saveSafetySettings(settings.copy(autoConfirmLowRisk = it)) }
                            }
                        )
                    }
                }
            }

            // Section: 禁止操作
            item {
                SectionHeaderWithIcon(
                    icon = Icons.Outlined.Block,
                    title = "禁止操作",
                    color = SmartColors.danger()
                )
            }

            item {
                SmartCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "以下操作无论如何都不允许自动执行：",
                            fontSize = 14.sp,
                            color = SmartColors.textSecondary()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusPill(text = "转账", color = SmartColors.danger())
                            StatusPill(text = "贷款", color = SmartColors.danger())
                            StatusPill(text = "注销账户", color = SmartColors.danger())
                        }
                    }
                }
            }

            // Bottom spacer
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
