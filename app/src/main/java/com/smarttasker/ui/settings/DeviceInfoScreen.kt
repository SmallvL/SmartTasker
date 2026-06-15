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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.core.bridge.*
import com.smarttasker.ui.common.IconBox
import com.smarttasker.ui.common.SectionHeaderWithIcon
import com.smarttasker.ui.common.SettingsDivider
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
                title = { Text("设备信息", fontWeight = FontWeight.SemiBold) },
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
            // Device summary card with icon box
            item {
                SmartCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBox(
                            icon = Icons.Outlined.PhoneAndroid,
                            color = Color(0xFFF59E0B)
                        )
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
            }

            // Section: Device details
            item {
                SectionHeaderWithIcon(
                    icon = Icons.Outlined.Info,
                    title = "设备详情",
                    color = Color(0xFF5B6EF5)
                )
            }

            item {
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconBox(
                                icon = icon,
                                color = Color(0xFF5B6EF5)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(label, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                Text(value, fontSize = 13.sp, color = SmartColors.textTertiary())
                            }
                        }
                    }
                }
            }

            // Section: Core status
            item {
                SectionHeaderWithIcon(
                    icon = Icons.Outlined.PowerSettingsNew,
                    title = "Core 状态",
                    color = Color(0xFFF59E0B)
                )
            }

            item {
                SmartCard {
                    // CoreBridge 连接
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBox(
                            icon = Icons.Outlined.PowerSettingsNew,
                            color = Color(0xFFF59E0B)
                        )
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

                    // 无障碍服务
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBox(
                            icon = Icons.Outlined.Accessibility,
                            color = Color(0xFF8B5CF6)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("无障碍服务", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            StatusPill(text = "需手动检查", color = SmartColors.warning())
                        }
                    }
                }
            }

            // Section: Device inspection
            item {
                SectionHeaderWithIcon(
                    icon = Icons.Outlined.Search,
                    title = "设备检查",
                    color = Color(0xFF06B6D4)
                )
            }

            item {
                SmartCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBox(
                            icon = Icons.Outlined.AccountTree,
                            color = Color(0xFF06B6D4)
                        )
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
                                color = SmartColors.textTertiary()
                            )
                        }
                    }
                }
            }

            // Hierarchy result
            if (hierarchyXml != null) {
                item {
                    SmartCard {
                        Text(
                            text = "页面结构",
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp
                        )
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

            // Section: ADB Setup Guide
            item {
                SectionHeaderWithIcon(
                    icon = Icons.Outlined.Usb,
                    title = "开启 ADB 调试",
                    color = Color(0xFFF59E0B)
                )
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

            // Bottom spacer
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
