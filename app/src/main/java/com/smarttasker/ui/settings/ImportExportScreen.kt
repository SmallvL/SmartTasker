package com.smarttasker.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.smarttasker.data.repository.SettingsRepository
import com.smarttasker.ui.common.SmartCard
import com.smarttasker.ui.common.SmartButton
import com.smarttasker.ui.common.SmartSecondaryButton
import com.smarttasker.ui.theme.SmartColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportExportScreen(
    settingsRepo: SettingsRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportResult by remember { mutableStateOf<String?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    // Get DB file info
    val dbPath = context.getDatabasePath("smarttask_db")
    val dbExists = dbPath?.exists() == true
    val dbSize = if (dbExists) dbPath.length() / 1024 else 0

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("导入导出", fontWeight = FontWeight.SemiBold) },
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
            // Explanation
            item {
                SmartCard {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.ImportExport, contentDescription = null,
                            tint = SmartColors.accent(), modifier = Modifier.size(32.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("数据备份与恢复", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                            Text("导出任务和配置，或从备份恢复", fontSize = 13.sp, color = SmartColors.textTertiary())
                        }
                    }
                }
            }

            // Export
            item {
                Text("导出", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    color = SmartColors.textTertiary(), modifier = Modifier.padding(start = 4.dp))
            }
            item {
                SmartCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(Icons.Outlined.FileUpload, contentDescription = null,
                            tint = SmartColors.accent(), modifier = Modifier.size(22.dp))
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
            }

            // Export result
            if (exportResult != null) {
                item {
                    SmartCard {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (exportResult!!.startsWith("成功")) Icons.Outlined.CheckCircle else Icons.Outlined.Error,
                                contentDescription = null,
                                tint = if (exportResult!!.startsWith("成功")) SmartColors.success() else SmartColors.danger(),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(exportResult!!, fontSize = 14.sp)
                        }
                    }
                }
            }

            // Import
            item {
                Text("导入", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    color = SmartColors.textTertiary(), modifier = Modifier.padding(start = 4.dp))
            }
            item {
                SmartCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = null,
                            tint = SmartColors.accent(), modifier = Modifier.size(22.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("从备份恢复", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Text("将覆盖当前所有数据，请先备份", fontSize = 13.sp, color = SmartColors.warning())
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "将备份文件放入下载目录的 SmartTask 文件夹，命名为 smarttask_backup.db",
                        fontSize = 13.sp, color = SmartColors.textTertiary()
                    )
                }
            }

            // Data stats
            item {
                Text("数据统计", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    color = SmartColors.textTertiary(), modifier = Modifier.padding(start = 4.dp))
            }
            item {
                SmartCard {
                    InfoRow("数据库路径", dbPath?.absolutePath ?: "未知")
                    Divider(color = SmartColors.borderSubtle(), modifier = Modifier.padding(vertical = 8.dp))
                    InfoRow("数据库大小", "${dbSize} KB")
                    Divider(color = SmartColors.borderSubtle(), modifier = Modifier.padding(vertical = 8.dp))
                    InfoRow("数据库状态", if (dbExists) "正常" else "未创建")
                }
            }

            // Danger zone
            item {
                Spacer(Modifier.height(8.dp))
                SmartCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null,
                            tint = SmartColors.danger(), modifier = Modifier.size(22.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("清除所有设置", fontWeight = FontWeight.Medium, fontSize = 15.sp,
                                color = SmartColors.danger())
                            Text("重置所有配置到默认值（不影响任务数据）", fontSize = 13.sp,
                                color = SmartColors.textTertiary())
                        }
                        OutlinedButton(
                            onClick = { showClearDialog = true },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12)
                        ) {
                            Text("清除", color = SmartColors.danger(), fontSize = 13.sp)
                        }
                    }
                }
            }
        }
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

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = SmartColors.textSecondary(), modifier = Modifier.weight(1f), fontSize = 14.sp)
        Text(value, fontSize = 14.sp)
    }
}

private suspend fun exportDatabase(context: Context): String = withContext(Dispatchers.IO) {
    try {
        val dbFile = context.getDatabasePath("smarttask_db")
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

        // Also copy WAL and SHM if they exist
        val walFile = File(dbFile.parent, "${dbFile.name}-wal")
        val shmFile = File(dbFile.parent, "${dbFile.name}-shm")
        if (walFile.exists()) walFile.copyTo(File(backupDir, "smarttask_backup_$timestamp.db-wal"), overwrite = true)
        if (shmFile.exists()) shmFile.copyTo(File(backupDir, "smarttask_backup_$timestamp.db-shm"), overwrite = true)

        "成功：已导出到 ${backupFile.absolutePath}"
    } catch (e: Exception) {
        "错误：${e.message}"
    }
}
