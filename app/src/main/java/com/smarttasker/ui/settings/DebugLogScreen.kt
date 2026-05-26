package com.smarttasker.ui.settings

import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.smarttasker.ui.common.SmartCard
import com.smarttasker.ui.theme.SmartColors
import com.smarttasker.util.DebugLog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val logs by DebugLog.logs.collectAsState()
    val listState = rememberLazyListState()

    // Read persisted crash log (SharedPreferences first, then file fallback)
    var crashMsg: String? = null
    var crashTime: Long = 0L
    val crashLog = remember {
        val prefs = context.getSharedPreferences("smarttasker_crash", android.content.Context.MODE_PRIVATE)
        val msg = prefs.getString("last_crash", "") ?: ""
        val time = prefs.getLong("last_crash_time", 0L)
        if (msg.isNotBlank()) {
            crashMsg = msg
            crashTime = time
            true
        } else {
            // Fallback: check crash file
            try {
                val crashFile = java.io.File(context.filesDir, "last_crash.txt")
                if (crashFile.exists() && crashFile.length() > 0) {
                    crashMsg = crashFile.readText()
                    crashTime = crashFile.lastModified()
                    true
                } else null
            } catch (_: Exception) { null }
        }
    }

    // Read persistent crash trace (survives crashes)
    val crashTrace = remember {
        try { com.smarttasker.util.CrashLog.read() } catch (_: Exception) { "" }
    }

    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Debug 日志", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                // Copy all logs to clipboard
                IconButton(onClick = {
                    val allText = buildString {
                        // Crash trace
                        val trace = try { com.smarttasker.util.CrashLog.read() } catch (_: Exception) { "" }
                        if (trace.isNotBlank()) {
                            appendLine("=== 执行追踪日志 ===")
                            appendLine(trace)
                        }
                        // Crash log
                        if (crashMsg != null) {
                            appendLine("=== 上次闪退 ===")
                            appendLine(crashMsg)
                        }
                        // Debug logs
                        if (logs.isNotEmpty()) {
                            appendLine("=== Debug 日志 ===")
                            logs.forEach { appendLine(it.formatted()) }
                        }
                    }
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("SmartTasker Debug Log", allText))
                    android.widget.Toast.makeText(context, "已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "复制")
                }
                IconButton(onClick = {
                    DebugLog.clear()
                    com.smarttasker.util.CrashLog.clear()
                    context.getSharedPreferences("smarttasker_crash", android.content.Context.MODE_PRIVATE)
                        .edit().remove("last_crash").remove("last_crash_time").apply()
                    try { java.io.File(context.filesDir, "last_crash.txt").delete() } catch (_: Exception) {}
                }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "清除")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Show crash log at top if exists
            if (crashLog != null && crashMsg != null) {
                item {
                    CrashLogCard(crashMsg!!, crashTime)
                }
            }

            // Show persistent crash trace log (survives crashes)
            if (crashTrace.isNotBlank()) {
                item {
                    CrashTraceCard(crashTrace)
                }
            }

            if (logs.isEmpty() && crashLog == null && crashTrace.isBlank()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无日志", color = SmartColors.textTertiary(), fontSize = 14.sp)
                    }
                }
            }

            items(logs) { entry ->
                LogEntryRow(entry)
            }
        }
    }
}

@Composable
private fun CrashLogCard(msg: String, time: Long) {
    val dateStr = java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault())
        .format(java.util.Date(time))
    SmartCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Error, contentDescription = null,
                    tint = SmartColors.danger(), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("上次闪退日志 ($dateStr)", fontWeight = FontWeight.Bold,
                    color = SmartColors.danger(), fontSize = 14.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(msg, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                color = SmartColors.danger())
        }
    }
}

@Composable
private fun CrashTraceCard(trace: String) {
    SmartCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.BugReport, contentDescription = null,
                    tint = SmartColors.warning(), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("执行追踪日志", fontWeight = FontWeight.Bold,
                    color = SmartColors.warning(), fontSize = 14.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(trace, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                color = SmartColors.textSecondary(), maxLines = 50)
        }
    }
}

@Composable
private fun LogEntryRow(entry: DebugLog.LogEntry) {
    val bgColor = when (entry.level) {
        DebugLog.LogEntry.Level.ERROR -> Color(0xFF3D1F1F)
        DebugLog.LogEntry.Level.WARN -> Color(0xFF3D351F)
        DebugLog.LogEntry.Level.DEBUG -> Color(0xFF1A1A2E)
        DebugLog.LogEntry.Level.INFO -> Color.Transparent
    }
    val textColor = when (entry.level) {
        DebugLog.LogEntry.Level.ERROR -> Color(0xFFFF6B6B)
        DebugLog.LogEntry.Level.WARN -> Color(0xFFFFD93D)
        DebugLog.LogEntry.Level.DEBUG -> Color(0xFF6B9BFF)
        DebugLog.LogEntry.Level.INFO -> Color(0xFFCCCCCC)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = entry.formatted(),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = textColor,
            lineHeight = 14.sp
        )
    }
}
