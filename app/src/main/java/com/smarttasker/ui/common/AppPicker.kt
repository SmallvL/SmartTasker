package com.smarttasker.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.ui.theme.SmartColors
import com.smarttasker.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ════════════════════════════════════════════════════════════════
// APP PICKER — Shared Composable for app selection
// ════════════════════════════════════════════════════════════════

data class AppInfo(
    val name: String,
    val packageName: String,
    val isSystem: Boolean
)

@Composable
fun AppPickerChip(onAppSelected: (String) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }

    Surface(
        onClick = { showPicker = true },
        shape = RoundedCornerShape(10),
        color = Color(0xFF8B5CF6).copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Outlined.Apps,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color(0xFF8B5CF6)
            )
            Text(
                "从已安装应用选择",
                fontSize = 13.sp,
                color = Color(0xFF8B5CF6),
                fontWeight = FontWeight.Medium
            )
        }
    }

    if (showPicker) {
        AppPickerDialog(
            onAppSelected = { appName ->
                onAppSelected(appName)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerDialog(
    onAppSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    // Load installed apps
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val installed = pm.getInstalledApplications(0)
                val appList = installed.mapNotNull { appInfo ->
                    try {
                        val label = pm.getApplicationLabel(appInfo).toString()
                        if (label.isNotBlank()) {
                            AppInfo(
                                name = label,
                                packageName = appInfo.packageName,
                                isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                            )
                        } else null
                    } catch (e: Exception) { null }
                }.sortedWith(compareBy({ it.isSystem }, { it.name }))
                apps = appList
            } catch (e: Exception) {
                DebugLog.e("AppPicker", "Failed to load apps: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps
        else apps.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("选择目标应用", fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索应用名称或包名", color = SmartColors.textTertiary()) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF8B5CF6),
                        unfocusedBorderColor = SmartColors.borderSubtle()
                    ),
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = null, tint = SmartColors.textTertiary())
                    },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = SmartColors.accent())
                    }
                } else {
                    // App list
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(400.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // User apps section
                        val userApps = filteredApps.filter { !it.isSystem }
                        val systemApps = filteredApps.filter { it.isSystem }

                        if (userApps.isNotEmpty()) {
                            item {
                                Text(
                                    "用户应用 (${userApps.size})",
                                    fontSize = 12.sp,
                                    color = SmartColors.textTertiary(),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(userApps.size) { index ->
                                AppItemRow(userApps[index], onAppSelected)
                            }
                        }

                        if (systemApps.isNotEmpty() && searchQuery.isNotBlank()) {
                            item {
                                Text(
                                    "系统应用 (${systemApps.size})",
                                    fontSize = 12.sp,
                                    color = SmartColors.textTertiary(),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(systemApps.size) { index ->
                                AppItemRow(systemApps[index], onAppSelected)
                            }
                        }

                        if (filteredApps.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                    Text("未找到匹配应用", fontSize = 14.sp, color = SmartColors.textTertiary())
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        shape = RoundedCornerShape(20)
    )
}

@Composable
fun AppItemRow(app: AppInfo, onAppSelected: (String) -> Unit) {
    Surface(
        onClick = { onAppSelected(app.name) },
        shape = RoundedCornerShape(10),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // App icon placeholder
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (app.isSystem) SmartColors.textTertiary().copy(alpha = 0.1f)
                        else Color(0xFF8B5CF6).copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (app.isSystem) Icons.Outlined.Settings else Icons.Outlined.Apps,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (app.isSystem) SmartColors.textTertiary() else Color(0xFF8B5CF6)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    app.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    app.packageName,
                    fontSize = 11.sp,
                    color = SmartColors.textTertiary(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
