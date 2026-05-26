package com.smarttasker.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.data.repository.SettingsRepository
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

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Prompt 设置") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                SmartCard {
                    Icon(
                        Icons.Outlined.Psychology,
                        contentDescription = null,
                        tint = SmartColors.accent(),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "自定义 AI 解析提示词",
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "修改提示词可以影响 AI 如何理解你的任务描述",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Text(
                    text = "系统提示词",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 4.dp)
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
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
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

            item {
                Text(
                    text = "变量说明",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            item {
                SmartCard {
                    Text(
                        text = "在提示词中可以使用以下变量：",
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    variablePairs.forEach { (name, desc) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6),
                                color = SmartColors.accent().copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = name,
                                    fontSize = 12.sp,
                                    color = SmartColors.accent(),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = desc,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}
