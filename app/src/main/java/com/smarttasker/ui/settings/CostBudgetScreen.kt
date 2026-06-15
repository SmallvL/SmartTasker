package com.smarttasker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.data.repository.RunRepository
import com.smarttasker.data.repository.SettingsRepository
import com.smarttasker.ui.common.IconBox
import com.smarttasker.ui.common.SectionHeaderWithIcon
import com.smarttasker.ui.common.SettingsDivider
import com.smarttasker.ui.common.SmartCard
import com.smarttasker.ui.theme.SmartColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CostBudgetScreen(
    runRepo: RunRepository,
    settingsRepo: SettingsRepository,
    onBack: () -> Unit
) {
    val todaySuccess by runRepo.getTodaySuccessCount().collectAsState(initial = 0)
    val todayFailed by runRepo.getTodayFailedCount().collectAsState(initial = 0)
    val todayModelCalls by runRepo.getTodayModelCalls().collectAsState(initial = 0)

    val budgetSettings by settingsRepo.budgetSettings.collectAsState(initial = SettingsRepository.BudgetSettings())
    val scope = rememberCoroutineScope()

    var showBudgetDialog by remember { mutableStateOf(false) }
    var budgetInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("成本预算", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
                            contentDescription = "返回"
                        )
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
            // Summary card with colored icon backgrounds behind numbers
            item {
                SmartCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 成功
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
                            Text(
                                text = "成功",
                                fontSize = 12.sp,
                                color = SmartColors.textTertiary()
                            )
                        }
                        // 失败
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
                            Text(
                                text = "失败",
                                fontSize = 12.sp,
                                color = SmartColors.textTertiary()
                            )
                        }
                        // AI 调用
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
                            Text(
                                text = "AI 调用",
                                fontSize = 12.sp,
                                color = SmartColors.textTertiary()
                            )
                        }
                    }
                }
            }

            // Section: 预算设置
            item {
                SectionHeaderWithIcon(
                    icon = Icons.Outlined.AccountBalanceWallet,
                    title = "预算设置",
                    color = Color(0xFFF59E0B)
                )
            }

            item {
                SmartCard {
                    // 每日预算
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IconBox(
                            icon = Icons.Outlined.AccountBalanceWallet,
                            color = Color(0xFFF59E0B)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "每日预算",
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp
                            )
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IconBox(
                            icon = Icons.Outlined.Notifications,
                            color = Color(0xFF8B5CF6)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "超支提醒",
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "消费达到80%时通知",
                                fontSize = 13.sp,
                                color = SmartColors.textTertiary()
                            )
                        }
                        Switch(
                            checked = budgetSettings.alertOnThreshold,
                            onCheckedChange = {
                                scope.launch {
                                    settingsRepo.saveBudgetSettings(budgetSettings.copy(alertOnThreshold = it))
                                }
                            }
                        )
                    }

                    SettingsDivider()

                    // 超支自动停止
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IconBox(
                            icon = Icons.Outlined.Block,
                            color = SmartColors.danger()
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "超支自动停止",
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "达到预算上限后停止AI调用",
                                fontSize = 13.sp,
                                color = SmartColors.textTertiary()
                            )
                        }
                        Switch(
                            checked = budgetSettings.stopOnBudget,
                            onCheckedChange = {
                                scope.launch {
                                    settingsRepo.saveBudgetSettings(budgetSettings.copy(stopOnBudget = it))
                                }
                            }
                        )
                    }
                }
            }

            // Section: 模型单价参考
            item {
                SectionHeaderWithIcon(
                    icon = Icons.Outlined.Receipt,
                    title = "模型单价参考",
                    color = Color(0xFF06B6D4)
                )
            }

            item {
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            IconBox(
                                icon = model.icon,
                                color = model.color
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = model.name, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                Text(
                                    text = model.price,
                                    fontSize = 13.sp,
                                    color = SmartColors.textTertiary()
                                )
                            }
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
