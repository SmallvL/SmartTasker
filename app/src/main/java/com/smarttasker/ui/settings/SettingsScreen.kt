package com.smarttasker.ui.settings

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.smarttasker.core.bridge.CoreBridgeManager
import com.smarttasker.core.bridge.CoreStatus
import com.smarttasker.core.bridge.DeviceStatusChecker
import com.smarttasker.core.direct.ShellExecutor
import com.smarttasker.core.parser.LlmTaskSpecParser
import com.smarttasker.data.repository.RunRepository
import com.smarttasker.data.repository.SettingsRepository
import com.smarttasker.service.AdbPairingService
import com.smarttasker.ui.common.IconBox
import com.smarttasker.ui.common.SectionHeaderWithIcon
import com.smarttasker.ui.common.SettingsDivider
import com.smarttasker.ui.common.SmartButton
import com.smarttasker.ui.common.SmartCard
import com.smarttasker.ui.common.SmartSecondaryButton
import com.smarttasker.ui.common.StatusPill
import com.smarttasker.ui.theme.SmartColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ──────────────────────────────────────────────────────
// 可展开/折叠的设置区域组件
// ──────────────────────────────────────────────────────

@Composable
fun ExpandableSettingsSection(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconColor: Color,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "arrowRotation"
    )

    Column {
        // Header row - clickable to toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12))
                .clickable { expanded = !expanded }
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBox(icon = icon, color = iconColor)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Text(subtitle, fontSize = 13.sp, color = SmartColors.textTertiary())
            }
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "折叠" else "展开",
                tint = SmartColors.textTertiary().copy(alpha = 0.5f),
                modifier = Modifier
                    .size(24.dp)
                    .rotate(arrowRotation)
            )
        }

        // Expandable content
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content()
            }
        }
    }
}

// ──────────────────────────────────────────────────────
// 主设置页面
// ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepo: SettingsRepository,
    runRepo: RunRepository,
    coreBridgeManager: CoreBridgeManager,
    onNavigateToPermissions: () -> Unit = {},
    onNavigateToDebugLog: () -> Unit = {}
) {
    var darkMode by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }

        // ════════════════════════════════════════════
        // 权限与安全
        // ════════════════════════════════════════════
        item {
            SectionHeaderWithIcon(
                icon = Icons.Outlined.Shield,
                title = "权限与安全",
                color = Color(0xFF5B6EF5)
            )
        }
        item {
            SmartCard {
                // 权限体检 - 保留跳转
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12))
                        .clickable(onClick = onNavigateToPermissions)
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBox(icon = Icons.Outlined.Security, color = Color(0xFF5B6EF5))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("权限体检", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Text("检查运行环境", fontSize = 13.sp, color = SmartColors.textTertiary())
                    }
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = SmartColors.textTertiary().copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                SettingsDivider()

                // 安全策略 - 内联展开
                ExpandableSettingsSection(
                    icon = Icons.Outlined.Shield,
                    title = "安全策略",
                    subtitle = "高风险动作确认",
                    iconColor = Color(0xFF5B6EF5)
                ) {
                    SafetyPolicyContent(settingsRepo = settingsRepo)
                }

                SettingsDivider()

                // 成本预算 - 内联展开
                ExpandableSettingsSection(
                    icon = Icons.Outlined.HealthAndSafety,
                    title = "成本预算",
                    subtitle = "模型调用费用",
                    iconColor = Color(0xFF5B6EF5)
                ) {
                    CostBudgetContent(runRepo = runRepo, settingsRepo = settingsRepo)
                }
            }
        }

        // ════════════════════════════════════════════
        // AI 配置
        // ════════════════════════════════════════════
        item {
            SectionHeaderWithIcon(
                icon = Icons.Outlined.AutoAwesome,
                title = "AI 配置",
                color = Color(0xFF8B5CF6)
            )
        }
        item {
            SmartCard {
                // 模型配置 - 内联展开
                ExpandableSettingsSection(
                    icon = Icons.Outlined.SmartToy,
                    title = "模型配置",
                    subtitle = "API 地址和密钥",
                    iconColor = Color(0xFF8B5CF6)
                ) {
                    ModelConfigContent(settingsRepo = settingsRepo)
                }

                SettingsDivider()

                // Prompt 设置 - 内联展开
                ExpandableSettingsSection(
                    icon = Icons.Outlined.Psychology,
                    title = "Prompt 设置",
                    subtitle = "自定义提示词",
                    iconColor = Color(0xFF8B5CF6)
                ) {
                    PromptSettingsContent(settingsRepo = settingsRepo)
                }
            }
        }

        // ════════════════════════════════════════════
        // Core 引擎
        // ════════════════════════════════════════════
        item {
            SectionHeaderWithIcon(
                icon = Icons.Outlined.PowerSettingsNew,
                title = "Core 引擎",
                color = Color(0xFFF59E0B)
            )
        }
        item {
            SmartCard {
                // Core 启动 - 内联展开
                ExpandableSettingsSection(
                    icon = Icons.Outlined.PowerSettingsNew,
                    title = "Core 启动",
                    subtitle = "启动/停止自动化引擎",
                    iconColor = Color(0xFFF59E0B)
                ) {
                    CoreControlContent(
                        coreBridgeManager = coreBridgeManager
                    )
                }

                SettingsDivider()

                // 设备连接 - 内联展开
                ExpandableSettingsSection(
                    icon = Icons.Outlined.PhoneAndroid,
                    title = "设备连接",
                    subtitle = "ADB 无线调试",
                    iconColor = Color(0xFFF59E0B)
                ) {
                    DeviceInfoContent(coreBridgeManager = coreBridgeManager)
                }
            }
        }

        // ════════════════════════════════════════════
        // 通用
        // ════════════════════════════════════════════
        item {
            SectionHeaderWithIcon(
                icon = Icons.Outlined.Tune,
                title = "通用",
                color = SmartColors.accent()
            )
        }
        item {
            SmartCard {
                // Dark mode switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12))
                        .clickable { darkMode = !darkMode }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBox(
                        icon = Icons.Outlined.DarkMode,
                        color = Color(0xFF6366F1)
                    )
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

                SettingsDivider()

                // 导入导出 - 内联展开
                ExpandableSettingsSection(
                    icon = Icons.Outlined.ImportExport,
                    title = "导入导出",
                    subtitle = "备份和恢复数据",
                    iconColor = Color(0xFF06B6D4)
                ) {
                    ImportExportContent(settingsRepo = settingsRepo)
                }

                SettingsDivider()

                // Debug 日志 - 保留跳转
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12))
                        .clickable(onClick = onNavigateToDebugLog)
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBox(icon = Icons.Outlined.BugReport, color = Color(0xFFEC4899))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Debug 日志", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Text("查看运行日志", fontSize = 13.sp, color = SmartColors.textTertiary())
                    }
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = SmartColors.textTertiary().copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                SettingsDivider()

                // 关于 - 内联展开
                ExpandableSettingsSection(
                    icon = Icons.Outlined.Info,
                    title = "关于",
                    subtitle = "版本 1.0.0",
                    iconColor = SmartColors.textTertiary()
                ) {
                    AboutContent()
                }
            }
        }

        // Bottom spacer
        item {
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ──────────────────────────────────────────────────────
// 安全策略内联内容
// ──────────────────────────────────────────────────────

@Composable
private fun SafetyPolicyContent(settingsRepo: SettingsRepository) {
    val safetySettings by settingsRepo.safetySettings.collectAsState(initial = SettingsRepository.SafetySettings())
    val scope = rememberCoroutineScope()
    var settings by remember(safetySettings) { mutableStateOf(safetySettings) }

    // 高风险操作
    SectionHeaderWithIcon(
        icon = Icons.Outlined.Warning,
        title = "高风险操作",
        color = Color(0xFFEF4444)
    )
    Spacer(Modifier.height(4.dp))

    SmartCard {
        // 发送消息
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBox(icon = Icons.Outlined.Send, color = Color(0xFFF59E0B))
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
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBox(icon = Icons.Outlined.Delete, color = Color(0xFFEF4444))
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
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBox(icon = Icons.Outlined.Payment, color = Color(0xFFEC4899))
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

    Spacer(Modifier.height(8.dp))

    // 确认策略
    SectionHeaderWithIcon(
        icon = Icons.Outlined.VerifiedUser,
        title = "确认策略",
        color = Color(0xFF5B6EF5)
    )
    Spacer(Modifier.height(4.dp))

    SmartCard {
        // 每次执行前确认
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBox(icon = Icons.Outlined.CheckCircle, color = Color(0xFF5B6EF5))
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
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBox(icon = Icons.Outlined.AutoAwesome, color = Color(0xFF8B5CF6))
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

    Spacer(Modifier.height(8.dp))

    // 禁止操作
    SectionHeaderWithIcon(
        icon = Icons.Outlined.Block,
        title = "禁止操作",
        color = SmartColors.danger()
    )
    Spacer(Modifier.height(4.dp))

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

// ──────────────────────────────────────────────────────
// 成本预算内联内容
// ──────────────────────────────────────────────────────

@Composable
private fun CostBudgetContent(runRepo: RunRepository, settingsRepo: SettingsRepository) {
    val todaySuccess by runRepo.getTodaySuccessCount().collectAsState(initial = 0)
    val todayFailed by runRepo.getTodayFailedCount().collectAsState(initial = 0)
    val todayModelCalls by runRepo.getTodayModelCalls().collectAsState(initial = 0)

    val budgetSettings by settingsRepo.budgetSettings.collectAsState(initial = SettingsRepository.BudgetSettings())
    val scope = rememberCoroutineScope()

    var showBudgetDialog by remember { mutableStateOf(false) }
    var budgetInput by remember { mutableStateOf("") }

    // Summary card
    SmartCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(10),
                    color = SmartColors.success().copy(alpha = 0.12f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "$todaySuccess",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = SmartColors.success()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "成功", fontSize = 12.sp, color = SmartColors.textTertiary())
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(10),
                    color = SmartColors.danger().copy(alpha = 0.12f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "$todayFailed",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = SmartColors.danger()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "失败", fontSize = 12.sp, color = SmartColors.textTertiary())
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(10),
                    color = SmartColors.accent().copy(alpha = 0.12f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${todayModelCalls ?: 0}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = SmartColors.accent()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "AI 调用", fontSize = 12.sp, color = SmartColors.textTertiary())
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    // 预算设置
    SectionHeaderWithIcon(
        icon = Icons.Outlined.AccountBalanceWallet,
        title = "预算设置",
        color = Color(0xFFF59E0B)
    )
    Spacer(Modifier.height(4.dp))

    SmartCard {
        // 每日预算
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconBox(icon = Icons.Outlined.AccountBalanceWallet, color = Color(0xFFF59E0B))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "每日预算", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Text(
                    text = "¥${"%.2f".format(budgetSettings.dailyBudgetYuan)}",
                    fontSize = 13.sp,
                    color = SmartColors.textTertiary()
                )
            }
            IconButton(onClick = {
                budgetInput = "%.2f".format(budgetSettings.dailyBudgetYuan)
                showBudgetDialog = true
            }) {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = SmartColors.textTertiary().copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        SettingsDivider()

        // 超支提醒
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconBox(icon = Icons.Outlined.Notifications, color = Color(0xFF8B5CF6))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "超支提醒", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Text(text = "消费达到80%时通知", fontSize = 13.sp, color = SmartColors.textTertiary())
            }
            Switch(
                checked = budgetSettings.alertOnThreshold,
                onCheckedChange = {
                    scope.launch { settingsRepo.saveBudgetSettings(budgetSettings.copy(alertOnThreshold = it)) }
                }
            )
        }

        SettingsDivider()

        // 超支自动停止
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconBox(icon = Icons.Outlined.Block, color = SmartColors.danger())
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "超支自动停止", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Text(text = "达到预算上限后停止AI调用", fontSize = 13.sp, color = SmartColors.textTertiary())
            }
            Switch(
                checked = budgetSettings.stopOnBudget,
                onCheckedChange = {
                    scope.launch { settingsRepo.saveBudgetSettings(budgetSettings.copy(stopOnBudget = it)) }
                }
            )
        }
    }

    Spacer(Modifier.height(8.dp))

    // 模型单价参考
    SectionHeaderWithIcon(
        icon = Icons.Outlined.Receipt,
        title = "模型单价参考",
        color = Color(0xFF06B6D4)
    )
    Spacer(Modifier.height(4.dp))

    SmartCard {
        data class ModelPrice(val name: String, val price: String, val icon: ImageVector, val color: Color)
        val models = listOf(
            ModelPrice("GPT-4o-mini", "$0.15 / 1M tokens", Icons.Outlined.SmartToy, Color(0xFF10A37F)),
            ModelPrice("GPT-4o", "$2.50 / 1M tokens", Icons.Outlined.SmartToy, Color(0xFF8B5CF6)),
            ModelPrice("Claude 3.5 Sonnet", "$3.00 / 1M tokens", Icons.Outlined.Psychology, Color(0xFF3B82F6))
        )

        models.forEachIndexed { index, model ->
            if (index > 0) {
                SettingsDivider()
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconBox(icon = model.icon, color = model.color)
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = model.name, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Text(text = model.price, fontSize = 13.sp, color = SmartColors.textTertiary())
                }
            }
        }
    }

    // Budget edit dialog
    if (showBudgetDialog) {
        AlertDialog(
            onDismissRequest = { showBudgetDialog = false },
            title = { Text("设置每日预算") },
            text = {
                OutlinedTextField(
                    value = budgetInput,
                    onValueChange = { budgetInput = it },
                    label = { Text("预算金额 (元)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val value = budgetInput.toFloatOrNull()
                    if (value != null && value > 0f) {
                        scope.launch {
                            settingsRepo.saveBudgetSettings(budgetSettings.copy(dailyBudgetYuan = value))
                        }
                    }
                    showBudgetDialog = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBudgetDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

// ──────────────────────────────────────────────────────
// 模型配置内联内容
// ──────────────────────────────────────────────────────

private sealed class InlineTestResult {
    object Success : InlineTestResult()
    data class Error(val msg: String) : InlineTestResult()
}

@Composable
private fun ModelConfigContent(settingsRepo: SettingsRepository) {
    val savedUrl by settingsRepo.apiUrl.collectAsState(initial = "https://api.openai.com/v1/chat/completions")
    val savedKey by settingsRepo.apiKey.collectAsState(initial = "")
    val savedModel by settingsRepo.modelName.collectAsState(initial = "gpt-4o-mini")
    val scope = rememberCoroutineScope()

    var apiUrl by remember(savedUrl) { mutableStateOf(savedUrl) }
    var apiKey by remember(savedKey) { mutableStateOf(savedKey) }
    var modelName by remember(savedModel) { mutableStateOf(savedModel) }
    var showKey by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<InlineTestResult?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = SmartColors.accent(),
        unfocusedBorderColor = SmartColors.borderSubtle()
    )
    val fieldShape = RoundedCornerShape(16)

    val sectionColor = Color(0xFF8B5CF6)

    // API 配置
    SectionHeaderWithIcon(
        icon = Icons.Outlined.SmartToy,
        title = "API 配置",
        color = sectionColor
    )
    Spacer(Modifier.height(4.dp))

    SmartCard {
        OutlinedTextField(
            value = apiUrl,
            onValueChange = { apiUrl = it },
            label = { Text("API 地址") },
            modifier = Modifier.fillMaxWidth(),
            shape = fieldShape,
            colors = fieldColors,
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) }
        )

        Text(
            "支持格式：https://api.example.com/v1 或完整地址 /v1/chat/completions",
            fontSize = 11.sp,
            color = SmartColors.textTertiary(),
            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth(),
            shape = fieldShape,
            colors = fieldColors,
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(
                        if (showKey) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        contentDescription = if (showKey) "隐藏" else "显示"
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = modelName,
            onValueChange = { modelName = it },
            label = { Text("模型名称") },
            modifier = Modifier.fillMaxWidth(),
            shape = fieldShape,
            colors = fieldColors,
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.SmartToy, contentDescription = null) }
        )
    }

    Spacer(Modifier.height(8.dp))

    // 连接测试
    SectionHeaderWithIcon(
        icon = Icons.Outlined.NetworkCheck,
        title = "连接测试",
        color = Color(0xFF10B981)
    )
    Spacer(Modifier.height(4.dp))

    SmartCard {
        SmartButton(
            text = if (isTesting) "测试中..." else "测试连接",
            onClick = {
                isTesting = true
                testResult = null
                scope.launch {
                    try {
                        val normalizedUrl = LlmTaskSpecParser.normalizeApiUrl(apiUrl)
                        val client = OkHttpClient.Builder()
                            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                            .build()
                        val messages = JSONArray().put(
                            JSONObject().apply {
                                put("role", "user")
                                put("content", "hi")
                            }
                        )
                        val body = JSONObject().apply {
                            put("model", modelName)
                            put("messages", messages)
                            put("max_tokens", 5)
                        }
                        val req = Request.Builder()
                            .url(normalizedUrl)
                            .addHeader("Authorization", "Bearer $apiKey")
                            .addHeader("Content-Type", "application/json")
                            .post(body.toString().toRequestBody("application/json".toMediaType()))
                            .build()
                        val resp = client.newCall(req).execute()
                        testResult = if (resp.isSuccessful) {
                            InlineTestResult.Success
                        } else {
                            InlineTestResult.Error("HTTP ${resp.code}: ${resp.body?.string()?.take(200)}")
                        }
                    } catch (e: Exception) {
                        testResult = InlineTestResult.Error(e.message ?: "连接失败")
                    }
                    isTesting = false
                }
            },
            icon = Icons.Outlined.NetworkCheck,
            enabled = !isTesting && apiUrl.isNotBlank() && apiKey.isNotBlank()
        )

        if (isTesting) {
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = SmartColors.accent()
            )
        }

        testResult?.let { result ->
            Spacer(modifier = Modifier.height(12.dp))
            when (result) {
                is InlineTestResult.Success -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12))
                            .background(SmartColors.success().copy(alpha = 0.08f))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBox(icon = Icons.Outlined.CheckCircle, color = SmartColors.success())
                        Column(modifier = Modifier.weight(1f)) {
                            Text("连接成功", fontWeight = FontWeight.Medium, fontSize = 15.sp, color = SmartColors.success())
                            Text("API 可正常访问", fontSize = 13.sp, color = SmartColors.textTertiary())
                        }
                    }
                }
                is InlineTestResult.Error -> {
                    Column {
                        StatusPill("连接失败", SmartColors.danger())
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(result.msg, fontSize = 12.sp, color = SmartColors.textSecondary())
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    // 快捷模板
    SectionHeaderWithIcon(
        icon = Icons.Outlined.AutoAwesome,
        title = "快捷模板",
        color = Color(0xFF06B6D4)
    )
    Spacer(Modifier.height(4.dp))

    SmartCard {
        data class Preset(val name: String, val url: String, val icon: ImageVector, val color: Color)
        val presets = listOf(
            Preset("OpenAI", "https://api.openai.com/v1/chat/completions", Icons.Outlined.Public, Color(0xFF10A37F)),
            Preset("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", Icons.Outlined.Cloud, Color(0xFF6366F1)),
            Preset("DeepSeek", "https://api.deepseek.com/v1/chat/completions", Icons.Outlined.Psychology, Color(0xFF3B82F6)),
            Preset("智谱 GLM", "https://open.bigmodel.cn/api/paas/v4/chat/completions", Icons.Outlined.SmartToy, Color(0xFF1A56DB)),
            Preset("MiMo (小米)", "https://api.mlm.com/v1/chat/completions", Icons.Outlined.PhoneAndroid, Color(0xFFEA580C))
        )

        presets.forEachIndexed { index, preset ->
            if (index > 0) {
                SettingsDivider()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12))
                    .clickable {
                        apiUrl = preset.url
                        testResult = null
                    }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconBox(icon = preset.icon, color = preset.color)
                Column(modifier = Modifier.weight(1f)) {
                    Text(preset.name, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Text(preset.url, fontSize = 11.sp, color = SmartColors.textTertiary())
                }
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = SmartColors.textTertiary().copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    // 即时保存按钮
    SmartButton(
        text = "保存配置",
        onClick = {
            scope.launch {
                settingsRepo.saveModelConfig(apiUrl, apiKey, modelName)
            }
        },
        icon = Icons.Outlined.Save,
        modifier = Modifier.fillMaxWidth()
    )
}

// ──────────────────────────────────────────────────────
// Prompt 设置内联内容
// ──────────────────────────────────────────────────────

@Composable
private fun PromptSettingsContent(settingsRepo: SettingsRepository) {
    val savedPrompt by settingsRepo.customPrompt.collectAsState(initial = "")
    val scope = rememberCoroutineScope()

    val defaultPrompt = "你是一个安卓自动化任务解析器。用户会用自然语言描述一个手机操作任务，你需要将其解析为结构化的 JSON 格式。输出必须包含 name, description, target_app, trigger, risk, playbook 字段。风险等级: low=普通查看, medium=确认操作, high=不可逆操作, critical=金融操作(禁止)。只输出JSON。"

    var promptText by remember(savedPrompt) { mutableStateOf(savedPrompt.ifEmpty { defaultPrompt }) }

    val variablePairs = listOf(
        "{input}" to "用户输入的自然语言描述",
        "{app_list}" to "本机已安装的应用列表",
        "{time}" to "当前时间"
    )

    val sectionColor = Color(0xFF8B5CF6)

    // 系统提示词
    SectionHeaderWithIcon(
        icon = Icons.Outlined.EditNote,
        title = "系统提示词",
        color = sectionColor
    )
    Spacer(Modifier.height(4.dp))

    SmartCard {
        OutlinedTextField(
            value = promptText,
            onValueChange = { promptText = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp),
            label = { Text("System Prompt") },
            shape = RoundedCornerShape(16),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SmartColors.accent(),
                unfocusedBorderColor = SmartColors.borderSubtle()
            )
        )
    }

    Spacer(Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            SmartSecondaryButton(
                text = "恢复默认",
                onClick = { promptText = defaultPrompt },
                icon = Icons.Outlined.Restore
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            SmartButton(
                text = "保存",
                onClick = {
                    scope.launch {
                        settingsRepo.saveCustomPrompt(promptText)
                    }
                },
                icon = Icons.Outlined.Save
            )
        }
    }

    Spacer(Modifier.height(8.dp))

    // 变量说明
    SectionHeaderWithIcon(
        icon = Icons.Outlined.DataObject,
        title = "变量说明",
        color = Color(0xFF06B6D4)
    )
    Spacer(Modifier.height(4.dp))

    SmartCard {
        Text(
            text = "在提示词中可以使用以下变量：",
            fontSize = 14.sp,
            color = SmartColors.textSecondary()
        )
        Spacer(modifier = Modifier.height(12.dp))
        variablePairs.forEach { (name, desc) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8))
                    .border(
                        width = 1.dp,
                        color = SmartColors.accent().copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8)
                    )
                    .background(
                        SmartColors.accent().copy(alpha = 0.04f),
                        RoundedCornerShape(8)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(2))
                        .background(SmartColors.accent())
                )
                Surface(
                    shape = RoundedCornerShape(6),
                    color = SmartColors.accent().copy(alpha = 0.12f)
                ) {
                    Text(
                        text = name,
                        fontSize = 12.sp,
                        color = SmartColors.accent(),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = desc,
                    fontSize = 13.sp,
                    color = SmartColors.textSecondary()
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ──────────────────────────────────────────────────────
// Core 控制内联内容
// ──────────────────────────────────────────────────────

@Composable
private fun CoreControlContent(
    coreBridgeManager: CoreBridgeManager
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val coreStatus by coreBridgeManager.coreStatus.collectAsState()
    val shellMode by coreBridgeManager.shellMode.collectAsState()
    val deviceStatus by coreBridgeManager.deviceStatusChecker.status.collectAsState()

    var serviceState by remember { mutableStateOf("IDLE") }
    var serviceMessage by remember { mutableStateOf("") }
    var actionResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var isWorking by remember { mutableStateOf(false) }

    // 监听 AdbPairingService 广播
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == AdbPairingService.ACTION_STATUS) {
                    val state = intent.getStringExtra(AdbPairingService.EXTRA_STATE) ?: "IDLE"
                    val msg = intent.getStringExtra(AdbPairingService.EXTRA_MESSAGE) ?: ""
                    serviceState = state
                    serviceMessage = msg
                    com.smarttasker.util.DebugLog.i("CoreCtrl", "Service broadcast: $state - $msg")

                    when (state) {
                        "PAIRED", "CONNECTED" -> {
                            scope.launch {
                                com.smarttasker.util.DebugLog.i("CoreCtrl", "Pairing done, waiting 2s then connecting...")
                                delay(2000)
                                connectToSavedEndpoint(coreBridgeManager, context) { ok, msg2 ->
                                    actionResult = Pair(ok, msg2)
                                    isWorking = false
                                    scope.launch { coreBridgeManager.deviceStatusChecker.checkAll() }
                                }
                            }
                        }
                        "FAILED" -> {
                            actionResult = Pair(false, msg)
                            isWorking = false
                        }
                    }
                }
            }
        }
        val filter = IntentFilter(AdbPairingService.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // 总状态
    OverallStatusCard(coreStatus, shellMode, deviceStatus)

    Spacer(Modifier.height(8.dp))

    // 诊断项
    Text("环境检测", fontSize = 13.sp, fontWeight = FontWeight.Medium,
        color = SmartColors.textTertiary(), modifier = Modifier.padding(start = 4.dp))

    DiagnosticItem(
        label = "开发者选项",
        ok = deviceStatus.developerOptionsOn || shellMode == ShellExecutor.ShellMode.ADB_LOCAL,
        okText = "已开启",
        failText = if (shellMode == ShellExecutor.ShellMode.ADB_LOCAL) "ADB 本地模式" else "未开启",
        action = {
            try { context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            catch (_: Exception) { context.startActivity(Intent(AndroidSettings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        },
        actionText = "打开设置"
    )

    DiagnosticItem(
        label = "无线调试",
        ok = deviceStatus.wirelessDebuggingOn || shellMode == ShellExecutor.ShellMode.ADB_LOCAL,
        okText = "已开启",
        failText = if (shellMode == ShellExecutor.ShellMode.ADB_LOCAL) "ADB 本地模式" else "未开启",
        enabled = deviceStatus.developerOptionsOn,
        action = {
            try { context.startActivity(Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            catch (_: Exception) { context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        },
        actionText = "打开无线调试"
    )

    DiagnosticItem(
        label = "ADB 配对",
        ok = deviceStatus.hasSavedEndpoint,
        okText = "已配对 (${deviceStatus.savedEndpoint})",
        failText = "未配对"
    )

    DiagnosticItem(
        label = "ADB 端口连通",
        ok = deviceStatus.adbPortReachable,
        okText = "端口可达",
        failText = "端口不可达",
        enabled = deviceStatus.hasSavedEndpoint && deviceStatus.wirelessDebuggingOn
    )

    DiagnosticItem(
        label = "ADB Shell 连接",
        ok = deviceStatus.adbConnected,
        okText = "已连接",
        failText = "未连接"
    )

    DiagnosticItem(
        label = "Root 权限",
        ok = deviceStatus.rootAvailable,
        okText = "已获取",
        failText = "未获取",
        isOptional = true
    )

    // 操作按钮
    Spacer(Modifier.height(4.dp))
    Text("操作", fontSize = 13.sp, fontWeight = FontWeight.Medium,
        color = SmartColors.textTertiary(), modifier = Modifier.padding(start = 4.dp))

    val canPair = deviceStatus.developerOptionsOn && deviceStatus.wirelessDebuggingOn
    val canReconnect = deviceStatus.hasSavedEndpoint && deviceStatus.wirelessDebuggingOn

    if (!deviceStatus.hasSavedEndpoint && canPair) {
        SmartButton(
            text = if (isWorking) "配对中..." else "开始 ADB 配对",
            onClick = {
                requestNotificationAndRun(context, notifPermLauncher) {
                    isWorking = true
                    actionResult = null
                    com.smarttasker.util.DebugLog.i("CoreCtrl", "Starting ADB pairing...")
                    val intent = Intent(context, AdbPairingService::class.java)
                        .setAction(AdbPairingService.ACTION_START_GUIDE)
                    context.startForegroundService(intent)
                    openWirelessDebugging(context)
                }
            },
            enabled = !isWorking,
            icon = Icons.Outlined.PlayArrow
        )
    }

    if (canReconnect && !deviceStatus.adbConnected) {
        SmartButton(
            text = if (isWorking) "连接中..." else "重新连接 ADB",
            onClick = {
                isWorking = true
                actionResult = null
                scope.launch {
                    connectToSavedEndpoint(coreBridgeManager, context) { ok, msg ->
                        actionResult = Pair(ok, msg)
                        isWorking = false
                        scope.launch { coreBridgeManager.deviceStatusChecker.checkAll() }
                    }
                }
            },
            enabled = !isWorking,
            icon = Icons.Outlined.Link
        )
    }

    if (!canPair && !deviceStatus.wirelessDebuggingOn) {
        SmartButton(
            text = "打开无线调试",
            onClick = { openWirelessDebugging(context) },
            icon = Icons.Outlined.Wifi
        )
    }

    if (deviceStatus.rootAvailable && !deviceStatus.adbConnected) {
        SmartButton(
            text = "使用 Root 模式",
            onClick = {
                scope.launch {
                    coreBridgeManager.forceResetAndRefresh()
                    delay(300)
                    coreBridgeManager.deviceStatusChecker.checkAll()
                    if (ShellExecutor.detectMode() == ShellExecutor.ShellMode.ROOT) {
                        actionResult = Pair(true, "Root 模式已就绪")
                    }
                }
            },
            icon = Icons.Outlined.Security
        )
    }

    // 配对服务状态
    if (serviceState != "IDLE") {
        ServiceStatusCard(serviceState, serviceMessage)
    }

    // 操作结果
    actionResult?.let { (ok, msg) ->
        SmartCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.Error,
                    contentDescription = null,
                    tint = if (ok) SmartColors.success() else SmartColors.danger(),
                    modifier = Modifier.size(28.dp)
                )
                Text(msg, fontSize = 14.sp, color = if (ok) SmartColors.success() else SmartColors.danger())
            }
        }
    }

    // 连接详情
    if (deviceStatus.adbConnected || deviceStatus.rootAvailable) {
        SmartCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null,
                    tint = SmartColors.success(), modifier = Modifier.size(32.dp))
                Column {
                    Text("Core 运行中", fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                        color = SmartColors.success())
                    Text(
                        if (deviceStatus.activeMode == "root") "Root 模式"
                        else if (deviceStatus.activeMode == "adb" && deviceStatus.hasSavedEndpoint) "ADB 模式 (${deviceStatus.savedEndpoint})"
                        else if (deviceStatus.activeMode == "adb") "ADB 模式"
                        else "Shell 模式 (App 权限)",
                        fontSize = 13.sp, color = SmartColors.textSecondary()
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────
// 设备信息内联内容
// ──────────────────────────────────────────────────────

@Composable
private fun DeviceInfoContent(coreBridgeManager: CoreBridgeManager) {
    val coreStatus by coreBridgeManager.coreStatus.collectAsState()
    val scope = rememberCoroutineScope()
    var hierarchyXml by remember { mutableStateOf<String?>(null) }
    var isLoadingHierarchy by remember { mutableStateOf(false) }

    // 设备摘要
    SmartCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBox(icon = Icons.Outlined.PhoneAndroid, color = Color(0xFFF59E0B))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${Build.MANUFACTURER} ${Build.MODEL}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Android ${Build.VERSION.RELEASE}",
                    fontSize = 14.sp,
                    color = SmartColors.textTertiary()
                )
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    // 设备详情
    SectionHeaderWithIcon(
        icon = Icons.Outlined.Info,
        title = "设备详情",
        color = Color(0xFF5B6EF5)
    )
    Spacer(Modifier.height(4.dp))

    SmartCard {
        val deviceInfo = listOf(
            Triple(Icons.Outlined.PhoneAndroid, "设备型号", Build.MODEL),
            Triple(Icons.Outlined.Business, "制造商", Build.MANUFACTURER),
            Triple(Icons.Outlined.Android, "Android 版本", Build.VERSION.RELEASE),
            Triple(Icons.Outlined.Code, "SDK 版本", "${Build.VERSION.SDK_INT}"),
            Triple(Icons.Outlined.Fingerprint, "设备 ID", Build.ID)
        )

        deviceInfo.forEachIndexed { index, (icon, label, value) ->
            if (index > 0) {
                SettingsDivider()
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBox(icon = icon, color = Color(0xFF5B6EF5))
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Text(value, fontSize = 13.sp, color = SmartColors.textTertiary())
                }
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    // Core 状态
    SectionHeaderWithIcon(
        icon = Icons.Outlined.PowerSettingsNew,
        title = "Core 状态",
        color = Color(0xFFF59E0B)
    )
    Spacer(Modifier.height(4.dp))

    SmartCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBox(icon = Icons.Outlined.PowerSettingsNew, color = Color(0xFFF59E0B))
            Column(modifier = Modifier.weight(1f)) {
                Text("CoreBridge 连接", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                if (coreStatus is CoreStatus.Running) {
                    StatusPill(text = "Core 已连接", color = SmartColors.success())
                } else if (coreStatus is CoreStatus.ShellOnly) {
                    StatusPill(text = "基础模式", color = SmartColors.warning())
                } else {
                    StatusPill(text = "Core 未连接", color = SmartColors.warning())
                }
            }
        }

        SettingsDivider()

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBox(icon = Icons.Outlined.Accessibility, color = Color(0xFF8B5CF6))
            Column(modifier = Modifier.weight(1f)) {
                Text("无障碍服务", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                StatusPill(text = "需手动检查", color = SmartColors.warning())
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    // 设备检查
    SectionHeaderWithIcon(
        icon = Icons.Outlined.Search,
        title = "设备检查",
        color = Color(0xFF06B6D4)
    )
    Spacer(Modifier.height(4.dp))

    SmartCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBox(icon = Icons.Outlined.AccountTree, color = Color(0xFF06B6D4))
            Column(modifier = Modifier.weight(1f)) {
                Text("获取页面结构", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Text("查看当前界面的 UI 层级", fontSize = 13.sp, color = SmartColors.textTertiary())
            }
        }

        Spacer(Modifier.height(12.dp))

        SmartButton(
            text = "获取页面结构",
            onClick = {
                isLoadingHierarchy = true
                scope.launch {
                    when (val result = coreBridgeManager.bridge.dumpHierarchy()) {
                        is com.smarttasker.core.bridge.HierarchyResult.Success -> hierarchyXml = result.xml.take(2000)
                        is com.smarttasker.core.bridge.HierarchyResult.Error -> hierarchyXml = "错误: ${result.message}"
                    }
                    isLoadingHierarchy = false
                }
            },
            icon = Icons.Outlined.AccountTree,
            enabled = !isLoadingHierarchy && (coreStatus is CoreStatus.Running || coreStatus is CoreStatus.ShellOnly)
        )

        if (isLoadingHierarchy) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = SmartColors.accent()
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "正在获取...", fontSize = 14.sp, color = SmartColors.textTertiary())
            }
        }
    }

    // Hierarchy result
    if (hierarchyXml != null) {
        Spacer(Modifier.height(8.dp))
        SmartCard {
            Text(text = "页面结构", fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = hierarchyXml ?: "",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = SmartColors.textSecondary(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
            )
        }
    }

    Spacer(Modifier.height(8.dp))

    // ADB 设置指南
    SectionHeaderWithIcon(
        icon = Icons.Outlined.Usb,
        title = "开启 ADB 调试",
        color = Color(0xFFF59E0B)
    )
    Spacer(Modifier.height(4.dp))

    SmartCard {
        Text("ADB 是连接 Core 的必要条件", fontWeight = FontWeight.Medium, fontSize = 15.sp)
        Spacer(Modifier.height(12.dp))

        val steps = listOf(
            "1" to "打开 设置 → 关于手机",
            "2" to "连续点击「版本号」7 次，开启开发者模式",
            "3" to "返回 设置 → 系统 → 开发者选项",
            "4" to "开启「USB 调试」",
            "5" to "开启「无线调试」（Android 11+）",
            "6" to "点击「使用配对码配对设备」，输入配对码"
        )
        steps.forEach { (num, text) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = SmartColors.accent().copy(alpha = 0.12f),
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(num, fontSize = 12.sp, color = SmartColors.accent(),
                            fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(text, fontSize = 14.sp, color = SmartColors.textSecondary(),
                    modifier = Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(12.dp))
        Divider(color = SmartColors.borderSubtle())
        Spacer(Modifier.height(12.dp))

        Text("Root 模式（可选）", fontWeight = FontWeight.Medium, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "如果设备已 Root，可以直接使用 Root 权限运行 Core，无需 ADB。" +
            "在 Core 启动页切换为 Root 模式即可。",
            fontSize = 13.sp, color = SmartColors.textTertiary()
        )
    }
}

// ──────────────────────────────────────────────────────
// 导入导出内联内容
// ──────────────────────────────────────────────────────

@Composable
private fun ImportExportContent(settingsRepo: SettingsRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportResult by remember { mutableStateOf<String?>(null) }
    var importResult by remember { mutableStateOf<String?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showImportConfirmDialog by remember { mutableStateOf<Uri?>(null) }
    var showRestartDialog by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            showImportConfirmDialog = uri
        }
    }

    val dbPath = context.getDatabasePath("smarttask.db")
    val dbExists = dbPath?.exists() == true
    val dbSize = if (dbExists) dbPath.length() / 1024 else 0

    // Header
    SmartCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBox(icon = Icons.Outlined.ImportExport, color = Color(0xFF06B6D4))
            Column(modifier = Modifier.weight(1f)) {
                Text("数据备份与恢复", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Text("导出任务和配置，或从备份恢复", fontSize = 13.sp, color = SmartColors.textTertiary())
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    // 导出
    SectionHeaderWithIcon(
        icon = Icons.Outlined.FileUpload,
        title = "导出",
        color = Color(0xFF10B981)
    )
    Spacer(Modifier.height(4.dp))

    SmartCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconBox(icon = Icons.Outlined.FileUpload, color = Color(0xFF10B981))
            Column(modifier = Modifier.weight(1f)) {
                Text("导出数据库", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Text("将数据库文件复制到下载目录", fontSize = 13.sp, color = SmartColors.textTertiary())
            }
        }
        Spacer(Modifier.height(12.dp))
        SmartButton(
            text = if (isExporting) "导出中..." else "导出到下载目录",
            onClick = {
                isExporting = true
                exportResult = null
                scope.launch {
                    val result = exportDatabase(context)
                    exportResult = result
                    isExporting = false
                }
            },
            enabled = !isExporting && dbExists,
            icon = Icons.Outlined.Save
        )
    }

    // Export result
    exportResult?.let { result ->
        Spacer(Modifier.height(8.dp))
        SmartCard {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (result.startsWith("成功")) Icons.Outlined.CheckCircle else Icons.Outlined.Error,
                    contentDescription = null,
                    tint = if (result.startsWith("成功")) SmartColors.success() else SmartColors.danger(),
                    modifier = Modifier.size(24.dp)
                )
                Text(result, fontSize = 14.sp)
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    // 导入
    SectionHeaderWithIcon(
        icon = Icons.Outlined.FileDownload,
        title = "导入",
        color = Color(0xFF8B5CF6)
    )
    Spacer(Modifier.height(4.dp))

    SmartCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconBox(icon = Icons.Outlined.FileDownload, color = Color(0xFF8B5CF6))
            Column(modifier = Modifier.weight(1f)) {
                Text("从备份恢复", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Text("将覆盖当前所有数据，请先备份", fontSize = 13.sp, color = SmartColors.warning())
            }
        }
        Spacer(Modifier.height(12.dp))
        SmartButton(
            text = if (isImporting) "导入中..." else "选择备份文件",
            onClick = {
                importResult = null
                importLauncher.launch(arrayOf(
                    "application/octet-stream",
                    "application/x-sqlite3",
                    "application/vnd.sqlite3"
                ))
            },
            enabled = !isImporting,
            icon = Icons.Outlined.FolderOpen
        )
    }

    // Import result
    importResult?.let { result ->
        Spacer(Modifier.height(8.dp))
        SmartCard {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (result.startsWith("成功")) Icons.Outlined.CheckCircle else Icons.Outlined.Error,
                    contentDescription = null,
                    tint = if (result.startsWith("成功")) SmartColors.success() else SmartColors.danger(),
                    modifier = Modifier.size(24.dp)
                )
                Text(result, fontSize = 14.sp)
            }
            if (result.startsWith("成功")) {
                Spacer(Modifier.height(12.dp))
                SmartButton(
                    text = "重启应用",
                    onClick = { showRestartDialog = true },
                    icon = Icons.Outlined.Refresh
                )
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    // 数据统计
    SectionHeaderWithIcon(
        icon = Icons.Outlined.Storage,
        title = "数据统计",
        color = Color(0xFF5B6EF5)
    )
    Spacer(Modifier.height(4.dp))

    SmartCard {
        InfoRowWithIcon(Icons.Outlined.Folder, "数据库路径", dbPath?.absolutePath ?: "未知", Color(0xFF5B6EF5))
        SettingsDivider()
        InfoRowWithIcon(Icons.Outlined.DataUsage, "数据库大小", "${dbSize} KB", Color(0xFF06B6D4))
        SettingsDivider()
        InfoRowWithIcon(
            Icons.Outlined.CheckCircle,
            "数据库状态",
            if (dbExists) "正常" else "未创建",
            if (dbExists) SmartColors.success() else SmartColors.danger()
        )
    }

    Spacer(Modifier.height(8.dp))

    // 危险区域
    SmartCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12))
                .border(
                    width = 1.dp,
                    color = SmartColors.danger().copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12)
                )
                .background(
                    SmartColors.danger().copy(alpha = 0.04f),
                    RoundedCornerShape(12)
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(2))
                    .background(SmartColors.danger())
            )
            IconBox(icon = Icons.Outlined.Delete, color = SmartColors.danger())
            Column(modifier = Modifier.weight(1f)) {
                Text("清除所有设置", fontWeight = FontWeight.Medium, fontSize = 15.sp,
                    color = SmartColors.danger())
                Text("重置所有配置到默认值（不影响任务数据）", fontSize = 13.sp,
                    color = SmartColors.textTertiary())
            }
            OutlinedButton(
                onClick = { showClearDialog = true },
                shape = RoundedCornerShape(12)
            ) {
                Text("清除", color = SmartColors.danger(), fontSize = 13.sp)
            }
        }
    }

    // Import confirmation dialog
    showImportConfirmDialog?.let { uri ->
        AlertDialog(
            onDismissRequest = { showImportConfirmDialog = null },
            title = { Text("确认导入") },
            text = { Text("导入将覆盖当前所有任务和运行记录数据。建议先导出当前数据作为备份。\n\n导入后需要重启应用才能生效。") },
            confirmButton = {
                Button(
                    onClick = {
                        showImportConfirmDialog = null
                        isImporting = true
                        importResult = null
                        scope.launch {
                            val result = importDatabase(context, uri)
                            importResult = result
                            isImporting = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SmartColors.accent())
                ) { Text("确认导入") }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirmDialog = null }) { Text("取消") }
            }
        )
    }

    // Restart confirmation dialog
    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text("重启应用") },
            text = { Text("导入的数据库将在重启后生效。点击确认将关闭应用，请手动重新打开。") },
            confirmButton = {
                Button(
                    onClick = {
                        showRestartDialog = false
                        Runtime.getRuntime().exit(0)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SmartColors.accent())
                ) { Text("确认重启") }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) { Text("稍后手动重启") }
            }
        )
    }

    // Clear confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("确认清除") },
            text = { Text("将重置所有设置到默认值。任务数据和运行记录不会受影响。") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            settingsRepo.clearAll()
                            showClearDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SmartColors.danger())
                ) { Text("确认清除") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            }
        )
    }
}

// ──────────────────────────────────────────────────────
// 关于内联内容
// ──────────────────────────────────────────────────────

@Composable
private fun AboutContent() {
    // App Info
    SmartCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                SmartColors.accent(),
                                Color(0xFF8B5CF6)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.SmartToy,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = Color.White
                )
            }
            Text(
                text = "SmartTask",
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            )
            Text(
                text = "版本 0.5.0",
                fontSize = 14.sp,
                color = SmartColors.textSecondary()
            )
            Text(
                text = "AI 驱动的安卓自动化助手",
                fontSize = 13.sp,
                color = SmartColors.textTertiary()
            )
        }
    }

    Spacer(Modifier.height(8.dp))

    // 技术栈
    SectionHeaderWithIcon(
        icon = Icons.Outlined.Code,
        title = "技术栈",
        color = Color(0xFF8B5CF6)
    )
    Spacer(Modifier.height(4.dp))

    SmartCard {
        data class TechItem(val label: String, val value: String, val icon: ImageVector, val color: Color)
        val techStack = listOf(
            TechItem("架构", "CoreBridge + Route-Then-Act", Icons.Outlined.AccountTree, Color(0xFF8B5CF6)),
            TechItem("UI", "Jetpack Compose + Material 3", Icons.Outlined.Palette, Color(0xFF5B6EF5)),
            TechItem("数据库", "Room (SQLite)", Icons.Outlined.Storage, Color(0xFF06B6D4)),
            TechItem("引擎", "AutoLXB Core", Icons.Outlined.SettingsSuggest, Color(0xFFF59E0B)),
            TechItem("AI", "OpenAI 兼容接口", Icons.Outlined.Psychology, Color(0xFF10B981))
        )

        techStack.forEachIndexed { index, item ->
            if (index > 0) {
                SettingsDivider()
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconBox(icon = item.icon, color = item.color)
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.label, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Text(item.value, fontSize = 13.sp, color = SmartColors.textTertiary())
                }
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    // 开源许可
    SectionHeaderWithIcon(
        icon = Icons.Outlined.Description,
        title = "开源许可",
        color = Color(0xFF5B6EF5)
    )
    Spacer(Modifier.height(4.dp))

    SmartCard {
        listOf(
            "Jetpack Compose" to "Apache 2.0",
            "Material 3" to "Apache 2.0",
            "OkHttp" to "Apache 2.0",
            "Room" to "Apache 2.0",
            "Hilt" to "Apache 2.0",
            "Kotlin" to "Apache 2.0"
        ).forEachIndexed { index, (name, license) ->
            if (index > 0) {
                SettingsDivider()
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconBox(icon = Icons.Outlined.Verified, color = Color(0xFF5B6EF5))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = name, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Text(text = license, fontSize = 13.sp, color = SmartColors.textTertiary())
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    Text(
        text = "Made with \u2764\uFE0F",
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
        textAlign = TextAlign.Center,
        fontSize = 13.sp,
        color = SmartColors.textTertiary()
    )
}

// ──────────────────────────────────────────────────────
// Core 控制辅助组件（从 CoreControlScreen.kt 提取）
// ──────────────────────────────────────────────────────

@Composable
private fun OverallStatusCard(
    coreStatus: CoreStatus,
    shellMode: ShellExecutor.ShellMode,
    deviceStatus: DeviceStatusChecker.FullStatus
) {
    val isRunning = coreStatus is CoreStatus.Running
    val isShellOnly = coreStatus is CoreStatus.ShellOnly
    @Suppress("UNUSED_PARAMETER") val unused = shellMode
    val isOperational = isRunning || isShellOnly
    SmartCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isOperational) Icons.Outlined.CheckCircle else Icons.Outlined.PowerSettingsNew,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = if (isRunning) SmartColors.success() else if (isShellOnly) SmartColors.warning() else SmartColors.warning()
            )
            Column {
                Text(
                    when {
                        isRunning -> "Core 运行中"
                        isShellOnly -> "基础模式"
                        else -> "Core 未运行"
                    },
                    fontWeight = FontWeight.Bold, fontSize = 20.sp,
                    color = when {
                        isRunning -> SmartColors.success()
                        isShellOnly -> SmartColors.warning()
                        else -> SmartColors.warning()
                    }
                )
                Spacer(Modifier.height(4.dp))
                when {
                    deviceStatus.activeMode == "root" -> StatusPill("Root 模式 · 完全控制", SmartColors.success())
                    deviceStatus.activeMode == "adb" -> StatusPill("ADB 模式 · 完全控制", SmartColors.accent())
                    deviceStatus.activeMode == "sh" -> StatusPill("SH 模式 · 执行可用·录制不可用", SmartColors.warning())
                    !deviceStatus.developerOptionsOn -> StatusPill("需要开启开发者选项", SmartColors.danger())
                    !deviceStatus.wirelessDebuggingOn -> StatusPill("需要开启无线调试", SmartColors.warning())
                    !deviceStatus.hasSavedEndpoint -> StatusPill("需要配对", SmartColors.warning())
                    !deviceStatus.adbConnected -> StatusPill("连接断开", SmartColors.danger())
                    else -> StatusPill("未连接", SmartColors.textTertiary())
                }
            }
        }
    }
}

@Composable
private fun DiagnosticItem(
    label: String,
    ok: Boolean,
    okText: String,
    failText: String,
    enabled: Boolean = true,
    isOptional: Boolean = false,
    action: (() -> Unit)? = null,
    actionText: String = ""
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    when {
                        ok -> SmartColors.success().copy(alpha = 0.12f)
                        isOptional -> SmartColors.textTertiary().copy(alpha = 0.12f)
                        else -> SmartColors.danger().copy(alpha = 0.12f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (ok) Icons.Outlined.CheckCircle else if (isOptional) Icons.Outlined.Remove else Icons.Outlined.Close,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = when {
                    ok -> SmartColors.success()
                    isOptional -> SmartColors.textTertiary()
                    else -> SmartColors.danger()
                }
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(
                if (ok) okText else failText,
                fontSize = 12.sp,
                color = if (ok) SmartColors.success() else if (isOptional) SmartColors.textTertiary() else SmartColors.danger()
            )
        }

        if (!ok && action != null && enabled) {
            TextButton(onClick = action, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                Text(actionText, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ServiceStatusCard(state: String, message: String) {
    val (icon, color, title) = when (state) {
        "PAIRING" -> Triple(Icons.Outlined.Sync, SmartColors.accent(), "正在配对...")
        "PAIRED", "CONNECTED" -> Triple(Icons.Outlined.CheckCircle, SmartColors.success(), "配对成功")
        "CONNECTING" -> Triple(Icons.Outlined.Sync, SmartColors.accent(), "正在连接...")
        "FAILED" -> Triple(Icons.Outlined.Error, SmartColors.danger(), "配对失败")
        "WAIT_INPUT" -> Triple(Icons.Outlined.Edit, SmartColors.accent(), "等待输入配对码")
        else -> Triple(Icons.Outlined.Info, SmartColors.textTertiary(), state)
    }

    SmartCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state == "PAIRING" || state == "CONNECTING") {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp, color = color)
            } else {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            }
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = color)
                if (message.isNotBlank()) {
                    Text(message, fontSize = 13.sp, color = SmartColors.textSecondary())
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────
// 导入导出辅助组件和函数
// ──────────────────────────────────────────────────────

@Composable
private fun InfoRowWithIcon(icon: ImageVector, label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        IconBox(icon = icon, color = color)
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(value, fontSize = 13.sp, color = SmartColors.textTertiary())
        }
    }
}

private suspend fun exportDatabase(context: Context): String = withContext(Dispatchers.IO) {
    try {
        val dbFile = context.getDatabasePath("smarttask.db")
        if (dbFile == null || !dbFile.exists()) {
            return@withContext "错误：数据库文件不存在"
        }

        val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        val backupDir = File(downloadDir, "SmartTask")
        if (!backupDir.exists()) backupDir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val backupFile = File(backupDir, "smarttask_backup_$timestamp.db")

        dbFile.copyTo(backupFile, overwrite = true)

        val walFile = File(dbFile.parent, "${dbFile.name}-wal")
        val shmFile = File(dbFile.parent, "${dbFile.name}-shm")
        if (walFile.exists()) walFile.copyTo(File(backupDir, "smarttask_backup_$timestamp.db-wal"), overwrite = true)
        if (shmFile.exists()) shmFile.copyTo(File(backupDir, "smarttask_backup_$timestamp.db-shm"), overwrite = true)

        "成功：已导出到 ${backupFile.absolutePath}"
    } catch (e: Exception) {
        "错误：${e.message}"
    }
}

private suspend fun importDatabase(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val header = ByteArray(16)
            val bytesRead = input.read(header)
            if (bytesRead < 16) {
                return@withContext "错误：文件太小，不是有效的数据库文件"
            }
            val magic = String(header, Charsets.US_ASCII)
            if (!magic.startsWith("SQLite format 3")) {
                return@withContext "错误：不是有效的 SQLite 数据库文件"
            }
        } ?: return@withContext "错误：无法读取所选文件"

        try {
            val db = com.smarttasker.data.database.AppDatabase.getInstance(context)
            db.close()
            val field = com.smarttasker.data.database.AppDatabase.Companion::class.java
                .getDeclaredField("INSTANCE")
            field.isAccessible = true
            field.set(null, null)
        } catch (e: Exception) {
            android.util.Log.w("ImportExport", "Could not close existing DB: ${e.message}")
        }

        val dbFile = context.getDatabasePath("smarttask.db")
        dbFile.parentFile?.mkdirs()

        context.contentResolver.openInputStream(uri)?.use { input ->
            dbFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: return@withContext "错误：无法读取所选文件"

        val walFile = File(dbFile.parent, "${dbFile.name}-wal")
        val shmFile = File(dbFile.parent, "${dbFile.name}-shm")
        if (walFile.exists()) walFile.delete()
        if (shmFile.exists()) shmFile.delete()

        "成功：数据库已导入，请重启应用以生效"
    } catch (e: Exception) {
        "错误：${e.message}"
    }
}

// ──────────────────────────────────────────────────────
// Core 控制辅助函数
// ──────────────────────────────────────────────────────

private suspend fun connectToSavedEndpoint(
    coreBridgeManager: CoreBridgeManager,
    context: Context,
    onResult: (Boolean, String) -> Unit
) {
    try {
        kotlinx.coroutines.withTimeout(20_000L) {
            try {
                val prefs = context.getSharedPreferences("smarttasker_config", Context.MODE_PRIVATE)
                val host = prefs.getString("adb_host", "127.0.0.1") ?: "127.0.0.1"
                val port = prefs.getInt("adb_port", 0)
                com.smarttasker.util.DebugLog.i("CoreCtrl", "Connecting to $host:$port...")

                if (port > 0) {
                    try {
                        val connected = ShellExecutor.connectAdb(host, port)
                        com.smarttasker.util.DebugLog.i("CoreCtrl", "Direct attempt: $connected")
                        if (connected) {
                            coreBridgeManager.refreshStatus()
                            onResult(true, "ADB 连接成功 ($host:$port)")
                            return@withTimeout
                        }
                    } catch (e: Exception) {
                        com.smarttasker.util.DebugLog.e("CoreCtrl", "Direct attempt failed: ${e.message}")
                    }
                }

                com.smarttasker.util.DebugLog.i("CoreCtrl", "Direct connect failed, trying NSD autoConnect...")
                try {
                    val autoConnected = ShellExecutor.connectAdb(host, 0)
                    com.smarttasker.util.DebugLog.i("CoreCtrl", "NSD autoConnect result: $autoConnected")
                    if (autoConnected) {
                        coreBridgeManager.refreshStatus()
                        onResult(true, "ADB 自动发现连接成功")
                        return@withTimeout
                    }
                } catch (e: Exception) {
                    com.smarttasker.util.DebugLog.e("CoreCtrl", "NSD autoConnect failed: ${e.message}")
                }

                onResult(false, "连接失败: 请确认无线调试仍开启，且设备在同一网络")
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                com.smarttasker.util.DebugLog.e("CoreCtrl", "connectToSavedEndpoint timed out after 20s")
                onResult(false, "连接超时: ADB 连接未能在 20 秒内完成")
            } catch (e: Exception) {
                com.smarttasker.util.DebugLog.e("CoreCtrl", "connectToSavedEndpoint error: ${e.message}")
                onResult(false, "连接异常: ${e.message}")
            }
        }
    } catch (e: CancellationException) {
        com.smarttasker.util.DebugLog.w("CoreCtrl", "connectToSavedEndpoint cancelled (scope left composition)")
    } catch (e: Exception) {
        com.smarttasker.util.DebugLog.e("CoreCtrl", "connectToSavedEndpoint outer error: ${e.message}")
        onResult(false, "连接异常: ${e.message}")
    }
}

private fun openWirelessDebugging(context: Context) {
    try {
        context.startActivity(Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {}
    }
}

private fun requestNotificationAndRun(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<String>,
    onGranted: () -> Unit
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    onGranted()
}
