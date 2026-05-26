package com.smarttasker.ui.trialrun

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.core.record.model.*
import com.smarttasker.core.record.ui.RecordingOverlayService
import com.smarttasker.data.entity.TaskEntity
import com.smarttasker.ui.common.SmartButton
import com.smarttasker.ui.common.SmartCard
import com.smarttasker.ui.theme.SmartColors
import com.smarttasker.util.DebugLog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualRecordingScreen(
    task: TaskEntity,
    onRecordingComplete: (RouteDraft) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var hasOverlayPermission by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingStopped by remember { mutableStateOf(false) }
    var recordedSteps by remember { mutableStateOf<List<RecordedStep>>(emptyList()) }
    var routeDraft by remember { mutableStateOf<RouteDraft?>(null) }
    var shellMode by remember { mutableStateOf<String?>(null) }
    var canRecord by remember { mutableStateOf(false) }
    var recordingError by remember { mutableStateOf<String?>(null) }

    // Check overlay permission and shell mode
    LaunchedEffect(Unit) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
        kotlinx.coroutines.Dispatchers.IO.let { dispatcher ->
            kotlinx.coroutines.withContext(dispatcher) {
                shellMode = com.smarttasker.core.direct.ShellExecutor.getCapabilityDescription()
                canRecord = com.smarttasker.core.direct.ShellExecutor.canRecord()
            }
        }
    }

    // Load saved route when recording stops — poll up to 10s for file to appear
    LaunchedEffect(recordingStopped) {
        if (recordingStopped) {
            val store = com.smarttasker.core.record.RouteDraftStore(context)
            var attempts = 0
            var loaded = false
            while (attempts < 20 && !loaded) { // 20 * 500ms = 10s max
                kotlinx.coroutines.delay(500)
                val drafts = store.listAll()
                if (drafts.isNotEmpty()) {
                    routeDraft = drafts.first()
                    recordedSteps = routeDraft?.steps ?: emptyList()
                    DebugLog.i("ManualRec", "Loaded route with ${recordedSteps.size} steps")
                    loaded = true
                }
                attempts++
            }
            if (!loaded) {
                DebugLog.e("ManualRec", "Route file not found after ${attempts * 500}ms")
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("手动录制", fontWeight = FontWeight.SemiBold) },
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
                Text(task.name, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Spacer(Modifier.height(4.dp))
                if (task.targetAppName.isNotEmpty()) {
                    Text(
                        "目标应用: ${task.targetAppName}",
                        fontSize = 13.sp,
                        color = SmartColors.textSecondary()
                    )
                }
            }

            when {
                // State 1: Need overlay permission
                !hasOverlayPermission -> {
                    SmartCard {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Outlined.Warning,
                                contentDescription = null,
                                tint = SmartColors.warning(),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "需要悬浮窗权限",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "录制功能需要悬浮窗权限来显示控制条",
                                fontSize = 13.sp,
                                color = SmartColors.textSecondary()
                            )
                            Spacer(Modifier.height(16.dp))
                            SmartButton(
                                text = "去授权",
                                onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                },
                                icon = Icons.Outlined.OpenInNew
                            )
                        }
                    }
                }

                // State 2: Ready to record
                !isRecording && !recordingStopped -> {
                    SmartCard {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                if (canRecord) Icons.Outlined.FiberManualRecord else Icons.Outlined.Warning,
                                contentDescription = null,
                                tint = if (canRecord) SmartColors.success() else SmartColors.warning(),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                if (canRecord) "准备录制" else "无法录制",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = if (canRecord) MaterialTheme.colorScheme.onSurface else SmartColors.warning()
                            )
                            Spacer(Modifier.height(8.dp))
                            
                            // Shell mode info
                            shellMode?.let {
                                Text(
                                    it,
                                    fontSize = 12.sp,
                                    color = SmartColors.textTertiary()
                                )
                                Spacer(Modifier.height(4.dp))
                            }
                            
                            if (!canRecord) {
                                Text(
                                    "录制需要 ADB 无线调试连接\n请在开发者选项中开启无线调试",
                                    fontSize = 14.sp,
                                    color = SmartColors.warning(),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 22.sp
                                )
                                Spacer(Modifier.height(16.dp))
                                SmartButton(
                                    text = "去设置",
                                    onClick = {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                        context.startActivity(intent)
                                    },
                                    icon = Icons.Outlined.OpenInNew
                                )
                            } else {
                                Text(
                                    "点击开始后，屏幕顶部会出现录制控制条。\n你正常操作手机，系统会录制你的所有操作。",
                                    fontSize = 14.sp,
                                    color = SmartColors.textSecondary(),
                                    lineHeight = 22.sp
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "支持: 点击、长按、滑动、返回、Home、音量键",
                                    fontSize = 12.sp,
                                    color = SmartColors.textTertiary()
                                )
                                Spacer(Modifier.height(24.dp))

                                // Launch target app first if specified
                                if (task.targetPackage.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = {
                                            val launchIntent = context.packageManager.getLaunchIntentForPackage(task.targetPackage)
                                            if (launchIntent != null) {
                                                context.startActivity(launchIntent)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12)
                                    ) {
                                        Icon(Icons.Outlined.Launch, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("先打开 ${task.targetAppName.ifEmpty { task.targetPackage }}")
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }

                                SmartButton(
                                    text = "开始录制",
                                    onClick = {
                                        val intent = Intent(context, RecordingOverlayService::class.java)
                                            .setAction(RecordingOverlayService.ACTION_START)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            context.startForegroundService(intent)
                                        } else {
                                            context.startService(intent)
                                        }
                                        isRecording = true
                                        DebugLog.i("ManualRec", "Recording started for task: ${task.name}")
                                    },
                                    icon = Icons.Outlined.FiberManualRecord
                                )
                            }
                        }
                    }
                }

                // State 3: Recording in progress
                isRecording && !recordingStopped -> {
                    SmartCard {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = SmartColors.danger(),
                                strokeWidth = 4.dp
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "录制中...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = SmartColors.danger()
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "请操作手机完成任务\n控制条在屏幕顶部（可拖拽）",
                                fontSize = 14.sp,
                                color = SmartColors.textSecondary()
                            )
                            Spacer(Modifier.height(24.dp))
                            OutlinedButton(
                                onClick = {
                                    // Stop recording via overlay service
                                    val intent = Intent(context, RecordingOverlayService::class.java)
                                        .setAction(RecordingOverlayService.ACTION_STOP)
                                    context.startService(intent)
                                    isRecording = false
                                    recordingStopped = true
                                    // Note: Route will be saved by RecordingOverlayService.stopRecording()
                                    // We'll load it after a short delay to ensure save completes
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = SmartColors.danger()
                                )
                            ) {
                                Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("停止录制")
                            }
                        }
                    }
                }

                // State 4: Recording complete - show results
                recordingStopped -> {
                    SmartCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = SmartColors.success(),
                                modifier = Modifier.size(32.dp)
                            )
                            Column {
                                Text("录制完成", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                Text(
                                    "共 ${recordedSteps.size} 个步骤",
                                    fontSize = 13.sp,
                                    color = SmartColors.textSecondary()
                                )
                            }
                        }
                    }

                    // Step list
                    if (recordedSteps.isNotEmpty()) {
                        Text(
                            "录制步骤",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(recordedSteps) { index, step ->
                                RecordedStepCard(index + 1, step)
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                        Text(
                            "未录制到任何步骤",
                            fontSize = 14.sp,
                            color = SmartColors.textTertiary(),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.weight(1f))
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                // Reset to re-record
                                recordingStopped = false
                                recordedSteps = emptyList()
                                routeDraft = null
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12)
                        ) {
                            Text("重新录制")
                        }
                        SmartButton(
                            text = "保存路线",
                            onClick = {
                                routeDraft?.let { onRecordingComplete(it) }
                            },
                            icon = Icons.Outlined.Save,
                            enabled = routeDraft != null && recordedSteps.isNotEmpty()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordedStepCard(index: Int, step: RecordedStep) {
    SmartCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Step number
            Text(
                "$index",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = SmartColors.accent()
            )

            // Icon
            Icon(
                imageVector = when (step.type) {
                    RecordedStepType.TAP -> Icons.Outlined.TouchApp
                    RecordedStepType.LONG_PRESS -> Icons.Outlined.TouchApp
                    RecordedStepType.SWIPE -> Icons.Outlined.Swipe
                    RecordedStepType.BACK -> Icons.Outlined.ArrowBack
                    RecordedStepType.HOME -> Icons.Outlined.Home
                    RecordedStepType.VOLUME_UP -> Icons.Outlined.VolumeUp
                    RecordedStepType.VOLUME_DOWN -> Icons.Outlined.VolumeDown
                    RecordedStepType.KEY_EVENT -> Icons.Outlined.Keyboard
                    RecordedStepType.TEXT_INPUT -> Icons.Outlined.TextFields
                    RecordedStepType.WAIT -> Icons.Outlined.Timer
                    RecordedStepType.SCREENSHOT -> Icons.Outlined.CameraAlt
                    RecordedStepType.APP_START -> Icons.Outlined.Launch
                    else -> Icons.Outlined.Circle
                },
                contentDescription = null,
                tint = SmartColors.textSecondary(),
                modifier = Modifier.size(20.dp)
            )

            // Description
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stepSummary(step),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                if (step.delayFromPreviousMs > 100) {
                    Text(
                        "等待 ${step.delayFromPreviousMs}ms",
                        fontSize = 11.sp,
                        color = SmartColors.textTertiary()
                    )
                }
            }

            // Confidence
            if (step.confidence < 0.8f) {
                Text(
                    "${(step.confidence * 100).toInt()}%",
                    fontSize = 11.sp,
                    color = SmartColors.warning()
                )
            }
        }
    }
}

private fun stepSummary(step: RecordedStep): String {
    return when (val action = step.action) {
        is StepAction.Tap -> "点击 (${action.x}, ${action.y})"
        is StepAction.LongPress -> "长按 (${action.x}, ${action.y}) ${action.durationMs}ms"
        is StepAction.Swipe -> "滑动 (${action.startX},${action.startY}) → (${action.endX},${action.endY})"
        is StepAction.Key -> when (action.keyName) {
            "BACK" -> "按返回键"
            "HOME" -> "按主页键"
            "VOLUME_UP" -> "按音量+"
            "VOLUME_DOWN" -> "按音量-"
            else -> "按键 ${action.keyName}"
        }
        is StepAction.TextInput -> "输入 '${action.text.take(20)}'"
        is StepAction.Wait -> "等待 ${action.durationMs}ms"
        is StepAction.Screenshot -> "截图"
        is StepAction.AppStart -> "打开 ${action.packageName}"
    }
}
