package com.smarttasker.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.core.bridge.*
import com.smarttasker.ui.common.SmartCard
import com.smarttasker.ui.common.SmartButton
import com.smarttasker.ui.common.StatusPill
import com.smarttasker.ui.theme.SmartColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoScreen(
    coreBridgeManager: CoreBridgeManager,
    onBack: () -> Unit
) {
    val coreStatus by coreBridgeManager.coreStatus.collectAsState()
    val scope = rememberCoroutineScope()
    var hierarchyXml by remember { mutableStateOf<String?>(null) }
    var isLoadingHierarchy by remember { mutableStateOf(false) }
    var screenInfo by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设备信息") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = SmartColors.textSecondary()
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Device summary card
            item {
                SmartCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.PhoneAndroid,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = SmartColors.accent()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "${Build.MANUFACTURER} ${Build.MODEL}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SmartColors.textSecondary()
                        )
                        Text(
                            text = "Android ${Build.VERSION.RELEASE}",
                            fontSize = 14.sp,
                            color = SmartColors.textSecondary()
                        )
                    }
                }
            }

            // Device info section
            item {
                SmartCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "设备详情",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SmartColors.textSecondary()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = SmartColors.borderSubtle())
                        Spacer(modifier = Modifier.height(12.dp))

                        val deviceInfo = listOf(
                            "设备型号" to Build.MODEL,
                            "制造商" to Build.MANUFACTURER,
                            "Android 版本" to Build.VERSION.RELEASE,
                            "SDK 版本" to "${Build.VERSION.SDK_INT}",
                            "设备 ID" to Build.ID
                        )

                        deviceInfo.forEachIndexed { index, (label, value) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = label,
                                    color = SmartColors.textSecondary(),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = value,
                                    color = SmartColors.textSecondary()
                                )
                            }
                            if (index < deviceInfo.lastIndex) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }

            // ADB/Core status section
            item {
                SmartCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Core 状态",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SmartColors.textSecondary()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = SmartColors.borderSubtle())
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "CoreBridge 连接",
                                color = SmartColors.textSecondary()
                            )
                            if (coreStatus is CoreStatus.Running) {
                                StatusPill(
                                    text = "Core 已连接",
                                    color = SmartColors.success()
                                )
                            } else if (coreStatus is CoreStatus.ShellOnly) {
                                StatusPill(
                                    text = "基础模式",
                                    color = SmartColors.warning()
                                )
                            } else {
                                StatusPill(
                                    text = "Core 未连接",
                                    color = SmartColors.warning()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "无障碍服务",
                                color = SmartColors.textSecondary()
                            )
                            StatusPill(
                                text = "需手动检查",
                                color = SmartColors.warning()
                            )
                        }
                    }
                }
            }

            // Device inspection buttons
            item {
                SmartCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "设备检查",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SmartColors.textSecondary()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = SmartColors.borderSubtle())
                        Spacer(modifier = Modifier.height(12.dp))

                        SmartButton(
                            text = "获取页面结构",
                            onClick = {
                                isLoadingHierarchy = true
                                scope.launch {
                                    when (val result = coreBridgeManager.bridge.dumpHierarchy()) {
                                        is HierarchyResult.Success -> hierarchyXml = result.xml.take(2000)
                                        is HierarchyResult.Error -> hierarchyXml = "错误: ${result.message}"
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
                                Text(
                                    text = "正在获取...",
                                    fontSize = 14.sp,
                                    color = SmartColors.textSecondary()
                                )
                            }
                        }
                    }
                }
            }

            // Hierarchy result
            if (hierarchyXml != null) {
                item {
                    SmartCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "页面结构",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SmartColors.textSecondary()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = SmartColors.borderSubtle())
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
                }
            }

            // ADB Setup Guide
            item {
                Text("开启 ADB 调试", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    color = SmartColors.textTertiary(), modifier = Modifier.padding(start = 4.dp))
            }
            item {
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
        }
    }
}
