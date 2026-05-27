package com.smarttasker.ui.settings

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.smarttasker.core.bridge.CoreBridgeManager
import com.smarttasker.core.bridge.CoreStatus
import com.smarttasker.core.bridge.DeviceStatusChecker
import com.smarttasker.core.direct.ShellExecutor
import com.smarttasker.data.repository.SettingsRepository
import com.smarttasker.service.AdbPairingService
import com.smarttasker.ui.common.SmartCard
import com.smarttasker.ui.common.SmartButton
import com.smarttasker.ui.common.SmartSecondaryButton
import com.smarttasker.ui.common.StatusPill
import com.smarttasker.ui.theme.SmartColors
import com.smarttasker.util.DebugLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ──────────────────────────────────────────────────────
// 主页面：诊断仪表盘
// ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoreControlScreen(
    coreBridgeManager: CoreBridgeManager,
    settingsRepo: SettingsRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val coreStatus by coreBridgeManager.coreStatus.collectAsState()
    val shellMode by coreBridgeManager.shellMode.collectAsState()
    val deviceStatus by coreBridgeManager.deviceStatusChecker.status.collectAsState()

    // Service broadcast state
    var serviceState by remember { mutableStateOf("IDLE") }
    var serviceMessage by remember { mutableStateOf("") }
    // Action result
    var actionResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var isWorking by remember { mutableStateOf(false) }

    // ── 监听 AdbPairingService 广播 ──
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == AdbPairingService.ACTION_STATUS) {
                    val state = intent.getStringExtra(AdbPairingService.EXTRA_STATE) ?: "IDLE"
                    val msg = intent.getStringExtra(AdbPairingService.EXTRA_MESSAGE) ?: ""
                    serviceState = state
                    serviceMessage = msg
                    DebugLog.i("CoreCtrl", "Service broadcast: $state - $msg")

                    when (state) {
                        "PAIRED", "CONNECTED" -> {
                            // Auto-connect after pairing
                            scope.launch {
                                DebugLog.i("CoreCtrl", "Pairing done, waiting 2s then connecting...")
                                delay(2000)
                                connectToSavedEndpoint(coreBridgeManager, context) { ok, msg2 ->
                                    actionResult = Pair(ok, msg2)
                                    isWorking = false
                                    // Refresh status
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

    // ── 通知权限 ──
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Core 启动", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(onClick = {
                    scope.launch {
                        coreBridgeManager.deviceStatusChecker.checkAll()
                        coreBridgeManager.refreshStatus()
                    }
                }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 总状态 ──
            OverallStatusCard(coreStatus, shellMode, deviceStatus)

            // ── 诊断项 ──
            Text("环境检测", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                color = SmartColors.textTertiary(), modifier = Modifier.padding(start = 4.dp))

            DiagnosticItem(
                label = "开发者选项",
                ok = deviceStatus.developerOptionsOn,
                okText = "已开启",
                failText = "未开启",
                action = {
                    try { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    catch (_: Exception) { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                },
                actionText = "打开设置"
            )

            DiagnosticItem(
                label = "无线调试",
                ok = deviceStatus.wirelessDebuggingOn,
                okText = "已开启",
                failText = "未开启",
                enabled = deviceStatus.developerOptionsOn,
                action = {
                    try { context.startActivity(Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    catch (_: Exception) { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
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

            // ── 操作按钮 ──
            Spacer(Modifier.height(4.dp))
            Text("操作", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                color = SmartColors.textTertiary(), modifier = Modifier.padding(start = 4.dp))

            // Smart action based on status
            val canPair = deviceStatus.developerOptionsOn && deviceStatus.wirelessDebuggingOn
            val canReconnect = deviceStatus.hasSavedEndpoint && deviceStatus.wirelessDebuggingOn

            if (!deviceStatus.hasSavedEndpoint && canPair) {
                // Need to pair first
                SmartButton(
                    text = if (isWorking) "配对中..." else "开始 ADB 配对",
                    onClick = {
                        requestNotificationAndRun(context, notifPermLauncher) {
                            isWorking = true
                            actionResult = null
                            DebugLog.i("CoreCtrl", "Starting ADB pairing...")
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
                // Has endpoint but not connected - try reconnect
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
                // Wireless debugging not on
                SmartButton(
                    text = "打开无线调试",
                    onClick = { openWirelessDebugging(context) },
                    icon = Icons.Outlined.Wifi
                )
            }

            if (deviceStatus.rootAvailable && !deviceStatus.adbConnected) {
                // Has root, use it
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

            // ── 配对服务状态 ──
            if (serviceState != "IDLE") {
                ServiceStatusCard(serviceState, serviceMessage)
            }

            // ── 操作结果 ──
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

            // ── 连接详情 ──
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

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ──────────────────────────────────────────────────────
// 连接辅助函数
// ──────────────────────────────────────────────────────

private suspend fun connectToSavedEndpoint(
    coreBridgeManager: CoreBridgeManager,
    context: Context,
    onResult: (Boolean, String) -> Unit
) {
    try {
        kotlinx.coroutines.withTimeout(30_000L) {
            try {
                val prefs = context.getSharedPreferences("smarttasker_config", Context.MODE_PRIVATE)
                val host = prefs.getString("adb_host", "127.0.0.1") ?: "127.0.0.1"
                val port = prefs.getInt("adb_port", 0)
                DebugLog.i("CoreCtrl", "Connecting to $host:$port...")

                // Phase 1: Try direct port connection (skip if port==0)
                if (port > 0) {
                    for (attempt in 1..2) {
                        try {
                            val connected = ShellExecutor.connectAdb(host, port)
                            DebugLog.i("CoreCtrl", "Direct attempt $attempt: $connected")
                            if (connected) {
                                coreBridgeManager.refreshStatus()
                                onResult(true, "ADB 连接成功 ($host:$port)")
                                return@withTimeout
                            }
                        } catch (e: Exception) {
                            DebugLog.e("CoreCtrl", "Direct attempt $attempt failed: ${e.message}")
                        }
                        delay(1000)
                    }
                }

                // Phase 2: NSD autoConnect fallback (works even if port changed)
                DebugLog.i("CoreCtrl", "Direct connect failed, trying NSD autoConnect...")
                try {
                    val autoConnected = ShellExecutor.connectAdb(host, 0) // port=0 → autoConnect
                    DebugLog.i("CoreCtrl", "NSD autoConnect result: $autoConnected")
                    if (autoConnected) {
                        coreBridgeManager.refreshStatus()
                        onResult(true, "ADB 自动发现连接成功")
                        return@withTimeout
                    }
                } catch (e: Exception) {
                    DebugLog.e("CoreCtrl", "NSD autoConnect failed: ${e.message}")
                }

                onResult(false, "连接失败: 请确认无线调试仍开启，且设备在同一网络")
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                DebugLog.e("CoreCtrl", "connectToSavedEndpoint timed out after 30s")
                onResult(false, "连接超时: ADB 连接未能在 30 秒内完成")
            } catch (e: Exception) {
                DebugLog.e("CoreCtrl", "connectToSavedEndpoint error: ${e.message}")
                onResult(false, "连接异常: ${e.message}")
            }
        }
    } catch (e: Exception) {
        DebugLog.e("CoreCtrl", "connectToSavedEndpoint outer error: ${e.message}")
        onResult(false, "连接异常: ${e.message}")
    }
}

private fun openWirelessDebugging(context: Context) {
    try {
        context.startActivity(Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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

// ──────────────────────────────────────────────────────
// UI 组件
// ──────────────────────────────────────────────────────

@Composable
private fun OverallStatusCard(
    coreStatus: CoreStatus,
    shellMode: ShellExecutor.ShellMode,
    deviceStatus: DeviceStatusChecker.FullStatus
) {
    val isRunning = coreStatus is CoreStatus.Running
    val isShellOnly = coreStatus is CoreStatus.ShellOnly
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
        // Status icon
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

        // Label + status text
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(
                if (ok) okText else failText,
                fontSize = 12.sp,
                color = if (ok) SmartColors.success() else if (isOptional) SmartColors.textTertiary() else SmartColors.danger()
            )
        }

        // Action button
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
