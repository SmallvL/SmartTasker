package com.smarttasker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.data.repository.SettingsRepository
import com.smarttasker.ui.common.IconBox
import com.smarttasker.ui.common.SectionHeaderWithIcon
import com.smarttasker.ui.common.SmartCard
import com.smarttasker.ui.common.SmartButton
import com.smarttasker.ui.common.SmartSecondaryButton
import com.smarttasker.ui.theme.SmartColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptSettingsScreen(settingsRepo: SettingsRepository, onBack: () -> Unit) {
    val savedPrompt by settingsRepo.customPrompt.collectAsState(initial = "")
    val scope = rememberCoroutineScope()

    val defaultPrompt = "你是一个安卓自动化任务解析器。用户会用自然语言描述一个手机操作任务，你需要将其解析为结构化的 JSON 格式。输出必须包含 name, description, target_app, trigger, risk, playbook 字段。风险等级: low=普通查看, medium=确认操作, high=不可逆操作, critical=金融操作(禁止)。只输出JSON。"

    var promptText by remember(savedPrompt) { mutableStateOf(savedPrompt.ifEmpty { defaultPrompt }) }

    val variablePairs = listOf(
        "{input}" to "用户输入的自然语言描述",
        "{app_list}" to "本机已安装的应用列表",
        "{time}" to "当前时间"
    )

    val sectionColor = Color(0xFF8B5CF6)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prompt 设置", fontWeight = FontWeight.SemiBold) },
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
            // Header card with icon box
            item {
                SmartCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IconBox(
                            icon = Icons.Outlined.Psychology,
                            color = sectionColor
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "自定义 AI 解析提示词",
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "修改提示词可以影响 AI 如何理解你的任务描述",
                                fontSize = 13.sp,
                                color = SmartColors.textTertiary()
                            )
                        }
                    }
                }
            }

            // Section: System Prompt
            item {
                SectionHeaderWithIcon(
                    icon = Icons.Outlined.EditNote,
                    title = "系统提示词",
                    color = sectionColor
                )
            }

            item {
                SmartCard {
                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp),
                        label = { Text("System Prompt") },
                        shape = RoundedCornerShape(16),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SmartColors.accent(),
                            unfocusedBorderColor = SmartColors.borderSubtle()
                        )
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        SmartSecondaryButton(
                            text = "恢复默认",
                            onClick = { promptText = defaultPrompt },
                            icon = Icons.Outlined.Restore
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        SmartButton(
                            text = "保存",
                            onClick = {
                                scope.launch {
                                    settingsRepo.saveCustomPrompt(promptText)
                                    onBack()
                                }
                            },
                            icon = Icons.Outlined.Save
                        )
                    }
                }
            }

            // Section: Variables
            item {
                SectionHeaderWithIcon(
                    icon = Icons.Outlined.DataObject,
                    title = "变量说明",
                    color = Color(0xFF06B6D4)
                )
            }

            item {
                SmartCard {
                    Text(
                        text = "在提示词中可以使用以下变量：",
                        fontSize = 14.sp,
                        color = SmartColors.textSecondary()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    variablePairs.forEach { (name, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8))
                                .border(
                                    width = 1.dp,
                                    color = SmartColors.accent().copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8)
                                )
                                .background(
                                    SmartColors.accent().copy(alpha = 0.04f),
                                    RoundedCornerShape(8)
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Accent-colored left border indicator via a small box
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(2))
                                    .background(SmartColors.accent())
                            )
                            Surface(
                                shape = RoundedCornerShape(6),
                                color = SmartColors.accent().copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = name,
                                    fontSize = 12.sp,
                                    color = SmartColors.accent(),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                text = desc,
                                fontSize = 13.sp,
                                color = SmartColors.textSecondary()
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // Bottom spacer
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
