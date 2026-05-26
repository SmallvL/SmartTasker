package com.smarttasker.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.ui.common.SmartCard
import com.smarttasker.ui.theme.SmartColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("关于") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowBack,
                        contentDescription = "返回"
                    )
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // App Info
            item {
                SmartCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = SmartColors.accent().copy(alpha = 0.12f),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.SmartToy,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = SmartColors.accent()
                                )
                            }
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
            }

            // Tech Stack Section
            item {
                Text(
                    text = "技术栈",
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = SmartColors.textSecondary(),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                SmartCard {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("架构", color = SmartColors.textSecondary(), modifier = Modifier.weight(1f))
                            Text("CoreBridge + Route-Then-Act")
                        }
                        Divider(color = SmartColors.borderSubtle(), modifier = Modifier.padding(vertical = 12.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("UI", color = SmartColors.textSecondary(), modifier = Modifier.weight(1f))
                            Text("Jetpack Compose + Material 3")
                        }
                        Divider(color = SmartColors.borderSubtle(), modifier = Modifier.padding(vertical = 12.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("数据库", color = SmartColors.textSecondary(), modifier = Modifier.weight(1f))
                            Text("Room (SQLite)")
                        }
                        Divider(color = SmartColors.borderSubtle(), modifier = Modifier.padding(vertical = 12.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("引擎", color = SmartColors.textSecondary(), modifier = Modifier.weight(1f))
                            Text("AutoLXB Core")
                        }
                        Divider(color = SmartColors.borderSubtle(), modifier = Modifier.padding(vertical = 12.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("AI", color = SmartColors.textSecondary(), modifier = Modifier.weight(1f))
                            Text("OpenAI 兼容接口")
                        }
                    }
                }
            }

            // Open Source Licenses Section
            item {
                Text(
                    text = "开源许可",
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = SmartColors.textSecondary(),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                SmartCard {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            "Jetpack Compose" to "Apache 2.0",
                            "Material 3" to "Apache 2.0",
                            "OkHttp" to "Apache 2.0",
                            "Room" to "Apache 2.0",
                            "Hilt" to "Apache 2.0",
                            "Kotlin" to "Apache 2.0"
                        ).forEachIndexed { index, (name, license) ->
                            if (index > 0) {
                                Divider(
                                    color = SmartColors.borderSubtle(),
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = name,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = license,
                                    fontSize = 13.sp,
                                    color = SmartColors.textTertiary()
                                )
                            }
                        }
                    }
                }
            }

            // Footer
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Made with ❤️",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    color = SmartColors.textTertiary()
                )
            }
        }
    }
}
