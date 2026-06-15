package com.smarttasker.ui.settings

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.ui.common.IconBox
import com.smarttasker.ui.common.SectionHeaderWithIcon
import com.smarttasker.ui.common.SettingsDivider
import com.smarttasker.ui.common.SmartCard
import com.smarttasker.ui.theme.SmartColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于", fontWeight = FontWeight.SemiBold) },
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
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // App Info with gradient background icon
            item {
                SmartCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Gradient background for app icon
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
            }

            // Tech Stack Section
            item {
                SectionHeaderWithIcon(
                    icon = Icons.Outlined.Code,
                    title = "技术栈",
                    color = Color(0xFF8B5CF6)
                )
            }

            item {
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            IconBox(
                                icon = item.icon,
                                color = item.color
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.label, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                Text(item.value, fontSize = 13.sp, color = SmartColors.textTertiary())
                            }
                        }
                    }
                }
            }

            // Open Source Licenses Section
            item {
                SectionHeaderWithIcon(
                    icon = Icons.Outlined.Description,
                    title = "开源许可",
                    color = Color(0xFF5B6EF5)
                )
            }

            item {
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            IconBox(
                                icon = Icons.Outlined.Verified,
                                color = Color(0xFF5B6EF5)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp
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
                    text = "Made with \u2764\uFE0F",
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
