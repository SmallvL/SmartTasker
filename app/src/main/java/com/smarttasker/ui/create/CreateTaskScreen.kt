package com.smarttasker.ui.create

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.core.bridge.CoreBridgeManager
import com.smarttasker.core.parser.TaskSpec
import com.smarttasker.core.parser.TaskSpecParser
import com.smarttasker.data.entity.TaskEntity
import com.smarttasker.data.repository.SettingsRepository
import com.smarttasker.data.repository.TaskRepository
import com.smarttasker.ui.common.*
import com.smarttasker.ui.theme.SmartColors
import java.util.UUID

// 对话消息
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskScreen(
    taskRepo: TaskRepository,
    coreBridgeManager: CoreBridgeManager,
    settingsRepo: SettingsRepository,
    initialInput: String = "",
    onTaskCreated: (TaskEntity) -> Unit,
    onCancel: () -> Unit
) {
    var userInput by remember { mutableStateOf(initialInput) }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var taskSpec by remember { mutableStateOf<TaskSpec?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // LLM config from settings
    val apiKey by settingsRepo.apiKey.collectAsState(initial = "")
    val apiUrl by settingsRepo.apiUrl.collectAsState(initial = "https://api.openai.com/v1/chat/completions")
    val modelName by settingsRepo.modelName.collectAsState(initial = "gpt-4o-mini")
    val useLlm = apiKey.isNotBlank()

    val ruleParser = coreBridgeManager.getTaskSpecParser()
    val llmParser = remember(apiKey, apiUrl, modelName) {
        if (apiKey.isNotBlank()) {
            coreBridgeManager.configureLlm(apiKey, apiUrl, modelName)
            coreBridgeManager.getLlmParser()
        } else null
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        TopAppBar(
            title = { Text("创建任务", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Outlined.Close, contentDescription = "取消")
                }
            },
            actions = {
                StatusPill(
                    if (useLlm) "AI: $modelName" else "规则解析",
                    if (useLlm) SmartColors.accent() else SmartColors.textTertiary()
                )
                Spacer(Modifier.width(16.dp))
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        // Chat messages
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Welcome message
            if (messages.isEmpty() && initialInput.isBlank()) {
                item {
                    WelcomeCard(onExampleClick = { userInput = it })
                }
            }

            items(messages) { msg ->
                ChatBubble(msg)
            }

            // AI analyzing indicator
            if (isAnalyzing) {
                item {
                    AnalyzingIndicator()
                }
            }

            // Error message
            if (errorMessage != null) {
                item {
                    ErrorCard(
                        message = errorMessage!!,
                        onRetry = { errorMessage = null }
                    )
                }
            }

            // Draft confirmation card
            if (showConfirm && taskSpec != null) {
                item {
                    TaskSpecConfirmCard(
                        spec = taskSpec!!,
                        onConfirm = {
                            // Create task entity from TaskSpec
                            val spec = taskSpec!!
                            val task = TaskEntity(
                                taskId = spec.taskId,
                                name = spec.name,
                                description = spec.description,
                                targetAppName = spec.targetApp?.name ?: "",
                                targetPackage = spec.targetApp?.packageName ?: "",
                                triggerType = spec.trigger.type,
                                triggerTime = spec.trigger.time,
                                riskLevel = spec.risk.level,
                                executionMode = spec.execution.mode,
                                status = "draft"
                            )
                            onTaskCreated(task)
                        },
                        onEdit = { showConfirm = false },
                        onCancel = { showConfirm = false; taskSpec = null }
                    )
                }
            }
        }

        // Input area
        if (!showConfirm) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = userInput,
                        onValueChange = { userInput = it },
                        placeholder = { Text("描述你想自动完成的事...", color = SmartColors.textTertiary()) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SmartColors.accent(),
                            unfocusedBorderColor = SmartColors.borderSubtle()
                        ),
                        maxLines = 3
                    )
                    FilledIconButton(
                        onClick = {
                            if (userInput.isNotBlank()) {
                                val userMsg = ChatMessage(isUser = true, content = userInput)
                                messages = messages + userMsg
                                val input = userInput
                                userInput = ""
                                isAnalyzing = true
                                errorMessage = null

                                // Use LLM parser if configured, fallback to rule-based
                                try {
                                    val result = if (useLlm && llmParser != null) {
                                        llmParser.parse(input)
                                    } else {
                                        ruleParser.parse(input)
                                    }
                                    when (result) {
                                        is TaskSpecParser.ParseResult.Success -> {
                                            val spec = result.spec
                                            taskSpec = spec
                                            isAnalyzing = false
                                            
                                            val aiMsg = ChatMessage(
                                                isUser = false,
                                                content = buildString {
                                                    append("我理解了你的需求：\n")
                                                    append("• 任务：${spec.name}\n")
                                                    if (spec.targetApp != null) {
                                                        append("• 应用：${spec.targetApp!!.name}\n")
                                                    }
                                                    append("• 触发：${formatTrigger(spec.trigger)}\n")
                                                    append("• 风险：${formatRisk(spec.risk)}")
                                                }
                                            )
                                            messages = messages + aiMsg
                                            showConfirm = true
                                        }
                                        is TaskSpecParser.ParseResult.Forbidden -> {
                                            isAnalyzing = false
                                            errorMessage = "⛔ 禁止执行：${result.reason}"
                                            val errorMsg = ChatMessage(
                                                isUser = false,
                                                content = "⛔ 该任务包含禁止操作，无法执行：${result.reason}"
                                            )
                                            messages = messages + errorMsg
                                        }
                                        is TaskSpecParser.ParseResult.Error -> {
                                            isAnalyzing = false
                                            errorMessage = result.message
                                            val errorMsg = ChatMessage(
                                                isUser = false,
                                                content = "抱歉，${result.message}。请尝试更具体地描述。"
                                            )
                                            messages = messages + errorMsg
                                        }
                                    }
                                } catch (e: Exception) {
                                    isAnalyzing = false
                                    errorMessage = "解析失败：${e.message}"
                                }
                            }
                        },
                        enabled = userInput.isNotBlank() && !isAnalyzing,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = SmartColors.accent(),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Outlined.Send, contentDescription = "发送")
                    }
                }
            }
        }
    }
}

private fun formatTrigger(trigger: TaskSpec.TriggerConfig): String {
    return when (trigger.type) {
        "manual" -> "手动执行"
        "schedule" -> "定时 ${trigger.time.ifEmpty { "未设置" }}"
        "notification" -> "通知触发"
        else -> trigger.type
    }
}

private fun formatRisk(risk: TaskSpec.RiskConfig): String {
    return when (risk.level) {
        "forbidden" -> "⛔ 禁止执行"
        "high" -> "⚠️ 高风险（需确认）"
        "medium" -> "中风险"
        else -> "✅ 低风险"
    }
}

@Composable
private fun WelcomeCard(onExampleClick: (String) -> Unit) {
    SmartCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = SmartColors.accent()
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "你想让手机自动完成什么？",
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "用自然语言描述，AI 会帮你解析成可执行的任务",
                fontSize = 14.sp,
                color = SmartColors.textSecondary()
            )
            Spacer(Modifier.height(20.dp))
            listOf(
                "每天早上9点打开淘宝收金币",
                "收到微信消息后打开查看",
                "打开设置检查系统更新"
            ).forEach { example ->
                Surface(
                    onClick = { onExampleClick(example) },
                    shape = RoundedCornerShape(12),
                    color = SmartColors.accent().copy(alpha = 0.08f)
                ) {
                    Text(
                        example,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        fontSize = 14.sp,
                        color = SmartColors.accent()
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!msg.isUser) {
            Surface(
                shape = RoundedCornerShape(20),
                color = SmartColors.accent(),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
        }
        Surface(
            shape = RoundedCornerShape(
                topStart = if (msg.isUser) 20.dp else 4.dp,
                topEnd = if (msg.isUser) 4.dp else 20.dp,
                bottomStart = 20.dp,
                bottomEnd = 20.dp
            ),
            color = if (msg.isUser) SmartColors.accent() else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                msg.content,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = if (msg.isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun AnalyzingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(20),
            color = SmartColors.accent(),
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = SmartColors.textTertiary()
                )
                Spacer(Modifier.width(8.dp))
                Text("正在分析...", color = SmartColors.textSecondary(), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    SmartCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Outlined.Error,
                contentDescription = null,
                tint = SmartColors.danger(),
                modifier = Modifier.size(24.dp)
            )
            Text(message, fontSize = 14.sp, color = SmartColors.danger())
        }
    }
}

@Composable
private fun TaskSpecConfirmCard(
    spec: TaskSpec,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit
) {
    SmartCard {
        Text("任务解析结果", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(16.dp))

        // Task details
        DraftRow("任务名称", spec.name)
        DraftRow("目标应用", spec.targetApp?.name ?: "待确定")
        DraftRow("触发方式", formatTrigger(spec.trigger))
        DraftRow("风险等级", formatRisk(spec.risk))
        DraftRow("执行方式", "首次学习，成功后复用路线")

        Spacer(Modifier.height(8.dp))

        // Confidence
        val confidence = spec.targetApp?.confidence ?: 0.8f
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("置信度 ", fontSize = 13.sp, color = SmartColors.textSecondary())
            LinearProgressIndicator(
                progress = confidence,
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp),
                color = SmartColors.accent(),
                trackColor = SmartColors.borderSubtle()
            )
            Text(" ${(confidence * 100).toInt()}%", fontSize = 13.sp, color = SmartColors.textSecondary())
        }

        Spacer(Modifier.height(20.dp))

        // Actions
        SmartButton(
            text = "开始试跑",
            onClick = onConfirm,
            icon = Icons.Outlined.PlayArrow
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16)
            ) {
                Text("调整设置")
            }
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16)
            ) {
                Text("取消")
            }
        }
    }
}

@Composable
private fun DraftRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = SmartColors.textSecondary())
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
