package com.smarttasker.ui.create

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.core.bridge.CoreBridgeManager
import com.smarttasker.core.parser.TaskSpec
import com.smarttasker.core.parser.TaskSpecParser
import com.smarttasker.data.entity.TemplateEntity
import com.smarttasker.data.entity.TaskEntity
import com.smarttasker.data.entity.TemplateStepEntity
import com.smarttasker.data.repository.RouteRepository
import com.smarttasker.data.repository.SettingsRepository
import com.smarttasker.data.repository.TaskRepository
import com.smarttasker.data.repository.TemplateRepository
import com.smarttasker.ui.common.*
import com.smarttasker.ui.theme.SmartColors
import com.smarttasker.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

// ── Creation mode enum ──
enum class CreationMode {
    SELECTOR, AI_CHAT, TEMPLATE, MANUAL
}

// ── 对话消息 ──
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

// ── Template info for grid display ──
data class TemplateDisplayInfo(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val category: String,
    val stepCount: Int,
    val usageCount: Int,
    val successRate: Float = 0f
)

// ── Convert TemplateEntity to TemplateDisplayInfo ──

private fun TemplateEntity.toTemplateDisplayInfo(): TemplateDisplayInfo = TemplateDisplayInfo(
    id = templateId,
    name = name,
    description = description,
    icon = icon,
    category = category,
    stepCount = stepCount,
    usageCount = usageCount,
    successRate = successRate
)

private val TemplateCategoryColors = mapOf(
    "社交" to Color(0xFF3B82F6),
    "购物" to Color(0xFFF97316),
    "工具" to Color(0xFF8B5CF6),
    "办公" to Color(0xFF06B6D4),
    "通用" to Color(0xFF6B7280)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskScreen(
    taskRepo: TaskRepository,
    coreBridgeManager: CoreBridgeManager,
    settingsRepo: SettingsRepository,
    templateRepo: TemplateRepository? = null,
    routeRepo: RouteRepository? = null,
    initialInput: String = "",
    templateId: String = "",
    onTaskCreated: (TaskEntity, Boolean) -> Unit,
    onCancel: () -> Unit
) {
    // ── Mode state ──
    var currentMode by remember { mutableStateOf(CreationMode.SELECTOR) }

    // If templateId provided, auto-enter template mode
    LaunchedEffect(templateId) {
        if (templateId.isNotBlank()) {
            currentMode = CreationMode.TEMPLATE
        }
    }

    // If initialInput provided, auto-enter AI chat mode
    LaunchedEffect(initialInput) {
        if (initialInput.isNotBlank()) {
            currentMode = CreationMode.AI_CHAT
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Top bar with adaptive navigation ──
        TopAppBar(
            title = {
                Text(
                    when (currentMode) {
                        CreationMode.SELECTOR -> "创建任务"
                        CreationMode.AI_CHAT -> "AI 对话创建"
                        CreationMode.TEMPLATE -> "从模板创建"
                        CreationMode.MANUAL -> "手动创建"
                    },
                    fontWeight = FontWeight.SemiBold
                )
            },
            navigationIcon = {
                if (currentMode == CreationMode.SELECTOR) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Outlined.Close, contentDescription = "取消")
                    }
                } else {
                    IconButton(onClick = { currentMode = CreationMode.SELECTOR }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        // ── Mode content ──
        AnimatedContent(
            targetState = currentMode,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInVertically { it } + fadeIn() togetherWith slideOutVertically { -it } + fadeOut()
                } else {
                    slideInVertically { -it } + fadeIn() togetherWith slideOutVertically { it } + fadeOut()
                }
            },
            label = "modeTransition"
        ) { mode ->
            when (mode) {
                CreationMode.SELECTOR -> ModeSelectorScreen(
                    onAiChat = { currentMode = CreationMode.AI_CHAT },
                    onTemplate = { currentMode = CreationMode.TEMPLATE },
                    onManual = { currentMode = CreationMode.MANUAL }
                )
                CreationMode.AI_CHAT -> AiChatModeScreen(
                    coreBridgeManager = coreBridgeManager,
                    settingsRepo = settingsRepo,
                    initialInput = initialInput,
                    onTaskCreated = { task -> onTaskCreated(task, false) }
                )
                CreationMode.TEMPLATE -> TemplateModeScreen(
                    templateRepo = templateRepo,
                    routeRepo = routeRepo,
                    preselectedTemplateId = templateId,
                    onTaskCreated = { task, fromTemplate -> onTaskCreated(task, fromTemplate) }
                )
                CreationMode.MANUAL -> ManualModeScreen(
                    onTaskCreated = { task -> onTaskCreated(task, false) }
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════
// MODE SELECTOR — 3 creation path cards
// ════════════════════════════════════════════════════════════════

@Composable
private fun ModeSelectorScreen(
    onAiChat: () -> Unit,
    onTemplate: () -> Unit,
    onManual: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Header
        item {
            Column {
                Text(
                    "选择创建方式",
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "根据你的需求选择最合适的方式",
                    fontSize = 14.sp,
                    color = SmartColors.textSecondary()
                )
            }
        }

        // ── Primary: AI 对话创建 ──
        item {
            ModeCard(
                title = "AI 对话创建",
                subtitle = "描述你想自动完成的事",
                description = "用自然语言描述，AI 会帮你解析成可执行的任务",
                icon = Icons.Outlined.AutoAwesome,
                gradientColors = listOf(SmartColors.accent(), Color(0xFF8B5CF6)),
                iconBgAlpha = 0.2f,
                isPrimary = true,
                onClick = onAiChat
            )
        }

        // ── Secondary: 从模板创建 ──
        item {
            ModeCard(
                title = "从模板创建",
                subtitle = "基于已有模板快速创建",
                description = "选择预设模板，一步创建常用自动化任务",
                icon = Icons.Outlined.Description,
                gradientColors = listOf(Color(0xFF8B5CF6), Color(0xFF6366F1)),
                iconBgAlpha = 0.15f,
                isPrimary = false,
                onClick = onTemplate
            )
        }

        // ── Tertiary: 手动创建 ──
        item {
            ModeCard(
                title = "手动创建",
                subtitle = "手动填写任务信息",
                description = "逐步填写任务配置，适合高级用户精确控制",
                icon = Icons.Outlined.Edit,
                gradientColors = listOf(Color(0xFF3B82F6), Color(0xFF2563EB)),
                iconBgAlpha = 0.12f,
                isPrimary = false,
                onClick = onManual
            )
        }

        // Bottom spacing
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun ModeCard(
    title: String,
    subtitle: String,
    description: String,
    icon: ImageVector,
    gradientColors: List<Color>,
    iconBgAlpha: Float,
    isPrimary: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Gradient header area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            colors = gradientColors.map { it.copy(alpha = if (isPrimary) 0.15f else 0.10f) }
                        ),
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Icon with gradient background
                    Box(
                        modifier = Modifier
                            .size(if (isPrimary) 56.dp else 48.dp)
                            .clip(RoundedCornerShape(if (isPrimary) 16.dp else 14.dp))
                            .background(
                                brush = Brush.linearGradient(colors = gradientColors)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier.size(if (isPrimary) 28.dp else 24.dp),
                            tint = Color.White
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = if (isPrimary) 20.sp else 18.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            subtitle,
                            fontSize = 14.sp,
                            color = SmartColors.textSecondary()
                        )
                    }
                    // Arrow indicator
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = SmartColors.textTertiary(),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Description
            Text(
                description,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                fontSize = 13.sp,
                color = SmartColors.textTertiary(),
                lineHeight = 18.sp
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════
// AI CHAT MODE — Improved chat interface
// ════════════════════════════════════════════════════════════════

@Composable
private fun AiChatModeScreen(
    coreBridgeManager: CoreBridgeManager,
    settingsRepo: SettingsRepository,
    initialInput: String,
    onTaskCreated: (TaskEntity) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var userInput by remember { mutableStateOf(initialInput) }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var taskSpec by remember { mutableStateOf<TaskSpec?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Editable fields in confirm card
    /**
     * Try to resolve a package name from an app name using PackageManager.
     */
    fun tryResolvePackage(appName: String): String {
        if (appName.isBlank()) return ""
        try {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(0)
            // First try exact match on application label
            for (app in apps) {
                val label = pm.getApplicationLabel(app).toString()
                if (label.equals(appName, ignoreCase = true)) {
                    return app.packageName
                }
            }
            // Then try partial match
            for (app in apps) {
                val label = pm.getApplicationLabel(app).toString()
                if (label.contains(appName, ignoreCase = true)) {
                    return app.packageName
                }
            }
        } catch (e: Exception) {
            DebugLog.w("CreateTask", "Failed to resolve package for: $appName")
        }
        return ""
    }

    var editName by remember { mutableStateOf("") }
    var editDescription by remember { mutableStateOf("") }
    var editTargetApp by remember { mutableStateOf("") }
    var editTriggerType by remember { mutableStateOf("manual") }
    var editRiskLevel by remember { mutableStateOf("low") }

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

    /**
     * Parse user input using LLM (async) or rule-based parser.
     * LLM call runs on IO dispatcher to avoid blocking the main thread.
     */
    suspend fun parseInput(input: String): TaskSpecParser.ParseResult = withContext(Dispatchers.IO) {
        if (useLlm && llmParser != null) {
            llmParser.parse(input)
        } else {
            ruleParser.parse(input)
        }
    }

    /**
     * Handle parse result — update UI state accordingly.
     */
    fun handleParseResult(result: TaskSpecParser.ParseResult) {
        when (result) {
            is TaskSpecParser.ParseResult.Success -> {
                val spec = result.spec
                taskSpec = spec
                editName = spec.name
                editDescription = spec.description
                editTargetApp = spec.targetApp?.name ?: ""
                editTriggerType = spec.trigger.type
                editRiskLevel = spec.risk.level
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
                errorMessage = "禁止执行：${result.reason}"
                val errorMsg = ChatMessage(
                    isUser = false,
                    content = "该任务包含禁止操作，无法执行：${result.reason}"
                )
                messages = messages + errorMsg
            }
            is TaskSpecParser.ParseResult.Error -> {
                isAnalyzing = false
                errorMessage = result.message
                val errorMsg = ChatMessage(
                    isUser = false,
                    content = "解析出错：${result.message}。请尝试更具体地描述，或检查AI模型配置。"
                )
                messages = messages + errorMsg
            }
        }
    }

    // Auto-send initial input
    LaunchedEffect(initialInput) {
        if (initialInput.isNotBlank() && messages.isEmpty()) {
            val cleanInput = initialInput.replace(Regex("[\\r\\n]+"), "").trim()
            if (cleanInput.isNotBlank()) {
                val userMsg = ChatMessage(isUser = true, content = cleanInput)
                messages = messages + userMsg
                userInput = ""
                isAnalyzing = true
                errorMessage = null

                try {
                    val result = parseInput(cleanInput)
                    handleParseResult(result)
                } catch (e: Exception) {
                    isAnalyzing = false
                    errorMessage = "解析失败：${e.message}"
                    val errorMsg = ChatMessage(
                        isUser = false,
                        content = "解析失败：${e.message}。请检查网络连接和AI模型配置。"
                    )
                    messages = messages + errorMsg
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
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
                    AiWelcomeCard(onExampleClick = { userInput = it })
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

            // Draft confirmation card with editable fields
            if (showConfirm && taskSpec != null) {
                item {
                    EditableTaskSpecConfirmCard(
                        spec = taskSpec!!,
                        editName = editName,
                        onEditNameChange = { editName = it },
                        editDescription = editDescription,
                        onEditDescriptionChange = { editDescription = it },
                        editTargetApp = editTargetApp,
                        onEditTargetAppChange = { editTargetApp = it },
                        editTriggerType = editTriggerType,
                        onEditTriggerTypeChange = { editTriggerType = it },
                        editRiskLevel = editRiskLevel,
                        onEditRiskLevelChange = { editRiskLevel = it },
                        onConfirm = {
                            val spec = taskSpec!!
                            // Try to resolve package name from app name if LLM didn't return it
                            val resolvedPackage = spec.targetApp?.packageName?.ifEmpty {
                                // Try to find the package from the app name via PackageManager
                                tryResolvePackage(editTargetApp.ifEmpty { spec.targetApp?.name ?: "" })
                            } ?: ""
                            val task = TaskEntity(
                                taskId = spec.taskId,
                                name = editName.ifEmpty { spec.name },
                                description = spec.playbook?.ifEmpty { spec.description } ?: spec.description,
                                targetAppName = editTargetApp.ifEmpty { spec.targetApp?.name ?: "" },
                                targetPackage = resolvedPackage,
                                triggerType = editTriggerType,
                                triggerTime = spec.trigger.time,
                                riskLevel = editRiskLevel,
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
                        maxLines = 3,
                        trailingIcon = {
                            if (userInput.isNotEmpty()) {
                                IconButton(onClick = { userInput = "" }) {
                                    Icon(
                                        Icons.Outlined.Clear,
                                        contentDescription = "清除",
                                        tint = SmartColors.textTertiary()
                                    )
                                }
                            }
                        }
                    )
                    FilledIconButton(
                        onClick = {
                            val cleanInput = userInput.replace(Regex("[\\r\\n]+"), "").trim()
                            if (cleanInput.isNotBlank()) {
                                val userMsg = ChatMessage(isUser = true, content = cleanInput)
                                messages = messages + userMsg
                                val input = cleanInput
                                userInput = ""
                                isAnalyzing = true
                                errorMessage = null

                                scope.launch {
                                    try {
                                        val result = parseInput(input)
                                        handleParseResult(result)
                                    } catch (e: Exception) {
                                        isAnalyzing = false
                                        errorMessage = "解析失败：${e.message}"
                                        val errorMsg = ChatMessage(
                                            isUser = false,
                                            content = "解析失败：${e.message}。请检查网络连接和AI模型配置。"
                                        )
                                        messages = messages + errorMsg
                                    }
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

// ════════════════════════════════════════════════════════════════
// TEMPLATE MODE — Template selection and creation
// ════════════════════════════════════════════════════════════════

@Composable
private fun TemplateModeScreen(
    templateRepo: TemplateRepository?,
    routeRepo: RouteRepository?,
    preselectedTemplateId: String,
    onTaskCreated: (TaskEntity, Boolean) -> Unit
) {
    var selectedTemplate by remember { mutableStateOf<TemplateEntity?>(null) }
    var selectedSteps by remember { mutableStateOf<List<TemplateStepEntity>>(emptyList()) }
    var showDetail by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 从数据库加载模板列表
    var dbTemplates by remember { mutableStateOf<List<TemplateEntity>>(emptyList()) }
    LaunchedEffect(Unit) {
        if (templateRepo != null) {
            templateRepo.getAllTemplates().collect { list ->
                dbTemplates = list
            }
        }
    }

    // Auto-select if templateId provided
    LaunchedEffect(preselectedTemplateId) {
        if (preselectedTemplateId.isNotBlank() && templateRepo != null) {
            val template = templateRepo.getTemplateById(preselectedTemplateId)
            if (template != null) {
                selectedTemplate = template
                val steps = templateRepo.getStepsForTemplateSync(preselectedTemplateId, template.versionCode)
                selectedSteps = steps
                showDetail = true
            }
        }
    }

    if (showDetail && selectedTemplate != null) {
        // ── Template detail view ──
        TemplateDetailView(
            template = selectedTemplate!!,
            templateSteps = selectedSteps,
            onUse = {
                val template = selectedTemplate!!
                // 从模板步骤中提取目标应用信息
                val openAppStep = selectedSteps.find { it.type == "open_app" }
                val targetPackage = openAppStep?.locatorValue ?: ""
                // 从包名推断应用名，或使用模板名称中的信息
                val targetAppName = template.name.let { name ->
                    // 常见包名映射
                    when {
                        targetPackage.contains("wechat") || targetPackage.contains("mm.") -> "微信"
                        targetPackage.contains("taobao") -> "淘宝"
                        targetPackage.contains("alipay") -> "支付宝"
                        targetPackage.contains("dingtalk") -> "钉钉"
                        targetPackage.contains("weibo") -> "微博"
                        targetPackage.contains("jd.") -> "京东"
                        targetPackage.contains("pinduoduo") -> "拼多多"
                        targetPackage.contains("douyin") -> "抖音"
                        else -> ""
                    }.ifEmpty { openAppStep?.summary?.removePrefix("打开")?.trim() ?: "" }
                }

                val task = TaskEntity(
                    taskId = UUID.randomUUID().toString(),
                    name = template.name,
                    description = template.description,
                    targetAppName = targetAppName,
                    targetPackage = targetPackage,
                    triggerType = "manual",
                    riskLevel = "low",
                    executionMode = "route_only",
                    status = "active"  // 模板已有步骤，直接激活
                )

                // 从模板步骤创建路线
                if (routeRepo != null && selectedSteps.isNotEmpty()) {
                    scope.launch {
                        routeRepo.saveFromTemplateSteps(task.taskId, selectedSteps)
                        onTaskCreated(task, true)
                    }
                } else {
                    onTaskCreated(task, true)
                }
            },
            onBack = {
                showDetail = false
                if (preselectedTemplateId.isNotBlank()) {
                    selectedTemplate = null
                    selectedSteps = emptyList()
                }
            }
        )
    } else {
        // ── Template grid view ──
        Column(modifier = Modifier.fillMaxSize()) {
            // Search hint
            Text(
                "选择一个模板快速开始",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                fontSize = 14.sp,
                color = SmartColors.textSecondary()
            )

            if (dbTemplates.isEmpty()) {
                // 数据库无模板时显示空状态
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Description,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = SmartColors.textTertiary()
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("暂无模板", fontSize = 16.sp, color = SmartColors.textSecondary())
                        Spacer(Modifier.height(4.dp))
                        Text("先创建任务并录制路线，然后保存为模板", fontSize = 13.sp, color = SmartColors.textTertiary())
                    }
                }
            } else {
                // 显示真实数据库模板
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(dbTemplates, key = { it.templateId }) { template ->
                        TemplateGridCard(
                            template = TemplateDisplayInfo(
                                id = template.templateId,
                                name = template.name,
                                description = template.description,
                                icon = template.icon,
                                category = template.category,
                                stepCount = template.stepCount,
                                usageCount = template.usageCount,
                                successRate = template.successRate
                            ),
                            onClick = {
                                selectedTemplate = template
                                // 加载模板步骤
                                scope.launch {
                                    if (templateRepo != null) {
                                        val steps = templateRepo.getStepsForTemplateSync(template.templateId, template.versionCode)
                                        selectedSteps = steps
                                    }
                                }
                                showDetail = true
                            }
                        )
                    }
                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }
}

@Composable
private fun TemplateGridCard(
    template: TemplateDisplayInfo,
    onClick: () -> Unit
) {
    val categoryColor = TemplateCategoryColors[template.category] ?: SmartColors.accent()

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Icon + category
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(categoryColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(template.icon, fontSize = 22.sp)
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = categoryColor.copy(alpha = 0.08f)
                ) {
                    Text(
                        template.category,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = categoryColor
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                template.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(4.dp))

            Text(
                template.description,
                fontSize = 12.sp,
                color = SmartColors.textSecondary(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )

            Spacer(Modifier.height(12.dp))

            // Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SmartColors.textTertiary().copy(alpha = 0.08f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            Icons.Outlined.List,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = SmartColors.textTertiary()
                        )
                        Text("${template.stepCount}步", fontSize = 10.sp, color = SmartColors.textTertiary())
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SmartColors.textTertiary().copy(alpha = 0.08f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Group,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = SmartColors.textTertiary()
                        )
                        Text("${template.usageCount}", fontSize = 10.sp, color = SmartColors.textTertiary())
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateDetailView(
    template: TemplateEntity,
    templateSteps: List<TemplateStepEntity>,
    onUse: () -> Unit,
    onBack: () -> Unit
) {
    val categoryColor = TemplateCategoryColors[template.category] ?: SmartColors.accent()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Template header card
        item {
            Surface(
                shape = RoundedCornerShape(20),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Gradient header
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        categoryColor.copy(alpha = 0.15f),
                                        categoryColor.copy(alpha = 0.05f)
                                    )
                                ),
                                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                            )
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(categoryColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(template.icon, fontSize = 30.sp)
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(template.name, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                template.description,
                                fontSize = 14.sp,
                                color = SmartColors.textSecondary(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }

                    // Stats row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TemplateStatItem("步骤", "${template.stepCount}", Icons.Outlined.List, categoryColor)
                        TemplateStatItem("使用次数", "${template.usageCount}", Icons.Outlined.Group, categoryColor)
                        TemplateStatItem(
                            "成功率",
                            "${(template.successRate * 100).toInt()}%",
                            Icons.Outlined.Speed,
                            categoryColor
                        )
                    }
                }
            }
        }

        // Template steps preview
        item {
            Surface(
                shape = RoundedCornerShape(20),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8),
                            color = categoryColor.copy(alpha = 0.12f),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.Route,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = categoryColor
                                )
                            }
                        }
                        Text("执行步骤", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = categoryColor)
                    }

                    Spacer(Modifier.height(12.dp))

                    if (templateSteps.isNotEmpty()) {
                        // 显示真实模板步骤
                        templateSteps.forEachIndexed { index, step ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = categoryColor.copy(alpha = 0.1f),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            "${index + 1}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = categoryColor
                                        )
                                    }
                                }
                                Text(step.summary.ifEmpty { step.type }, fontSize = 14.sp, color = SmartColors.textSecondary())
                            }
                            if (index < templateSteps.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .padding(start = 13.dp)
                                        .width(2.dp)
                                        .height(12.dp)
                                        .background(SmartColors.borderSubtle())
                                )
                            }
                        }
                    } else {
                        // 无步骤时显示提示
                        Text(
                            "暂无步骤信息",
                            fontSize = 14.sp,
                            color = SmartColors.textTertiary(),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // Use button
        item {
            SmartButton(
                text = "使用此模板",
                onClick = onUse,
                icon = Icons.Outlined.PlayArrow
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun TemplateStatItem(label: String, value: String, icon: ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = color)
        Spacer(Modifier.height(4.dp))
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text(label, fontSize = 11.sp, color = SmartColors.textTertiary())
    }
}

// ════════════════════════════════════════════════════════════════
// MANUAL MODE — Clean form with sections
// ════════════════════════════════════════════════════════════════

@Composable
private fun ManualModeScreen(
    onTaskCreated: (TaskEntity) -> Unit
) {
    // ── Basic info ──
    var taskName by remember { mutableStateOf("") }
    var taskDescription by remember { mutableStateOf("") }
    var targetApp by remember { mutableStateOf("") }

    // ── Trigger ──
    var triggerType by remember { mutableStateOf("manual") }
    var triggerTime by remember { mutableStateOf("") }
    var triggerRepeat by remember { mutableStateOf("once") }

    // ── Advanced ──
    var riskLevel by remember { mutableStateOf("low") }
    var executionMode by remember { mutableStateOf("learn_first_then_replay") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // ── Section: 基本信息 ──
        item {
            SectionHeaderWithIcon(
                icon = Icons.Outlined.Info,
                title = "基本信息",
                color = SmartColors.accent()
            )
        }

        item {
            SmartCard {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Task name
                    Column {
                        Text("任务名称", fontSize = 13.sp, color = SmartColors.textTertiary(), fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        SmartInputField(
                            value = taskName,
                            onValueChange = { taskName = it },
                            placeholder = "例如：每日签到"
                        )
                    }
                    // Description
                    Column {
                        Text("任务描述", fontSize = 13.sp, color = SmartColors.textTertiary(), fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        SmartInputField(
                            value = taskDescription,
                            onValueChange = { taskDescription = it },
                            placeholder = "描述任务要完成的目标",
                            singleLine = false
                        )
                    }
                    // Target app
                    Column {
                        Text("目标应用", fontSize = 13.sp, color = SmartColors.textTertiary(), fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        SmartInputField(
                            value = targetApp,
                            onValueChange = { targetApp = it },
                            placeholder = "例如：淘宝、微信"
                        )
                        Spacer(Modifier.height(4.dp))
                        AppPickerChip(onAppSelected = { targetApp = it })
                    }
                }
            }
        }

        // ── Section: 触发方式 ──
        item {
            SectionHeaderWithIcon(
                icon = Icons.Outlined.Schedule,
                title = "触发方式",
                color = Color(0xFF3B82F6)
            )
        }

        item {
            SmartCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Trigger type visual selector
                    TriggerTypeSelector(
                        selected = triggerType,
                        onSelect = { triggerType = it }
                    )

                    // Schedule options
                    if (triggerType == "schedule") {
                        Divider(color = SmartColors.borderSubtle())
                        Spacer(Modifier.height(4.dp))

                        // Time picker
                        Column {
                            Text("触发时间", fontSize = 13.sp, color = SmartColors.textTertiary(), fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(6.dp))
                            SmartInputField(
                                value = triggerTime,
                                onValueChange = { triggerTime = it },
                                placeholder = "例如：09:00"
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        // Repeat selector
                        Column {
                            Text("重复频率", fontSize = 13.sp, color = SmartColors.textTertiary(), fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("once" to "单次", "daily" to "每天", "weekly" to "每周").forEach { (value, label) ->
                                    Surface(
                                        onClick = { triggerRepeat = value },
                                        shape = RoundedCornerShape(12),
                                        color = if (triggerRepeat == value) Color(0xFF3B82F6) else SmartColors.borderSubtle().copy(alpha = 0.3f)
                                    ) {
                                        Text(
                                            label,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (triggerRepeat == value) Color.White else SmartColors.textSecondary()
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Notification hint
                    if (triggerType == "notification") {
                        Divider(color = SmartColors.borderSubtle())
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(12),
                            color = Color(0xFF3B82F6).copy(alpha = 0.08f)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = Color(0xFF3B82F6)
                                )
                                Text(
                                    "通知触发模式将在收到指定应用通知时自动执行",
                                    fontSize = 12.sp,
                                    color = Color(0xFF3B82F6)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Section: 高级设置 ──
        item {
            SectionHeaderWithIcon(
                icon = Icons.Outlined.Tune,
                title = "高级设置",
                color = Color(0xFFF59E0B)
            )
        }

        item {
            SmartCard {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Risk level
                    Column {
                        Text("风险等级", fontSize = 13.sp, color = SmartColors.textTertiary(), fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("low" to "低", "medium" to "中", "high" to "高").forEach { (value, label) ->
                                val isSelected = riskLevel == value
                                val chipColor = when (value) {
                                    "low" -> SmartColors.success()
                                    "medium" -> SmartColors.warning()
                                    "high" -> SmartColors.danger()
                                    else -> SmartColors.textTertiary()
                                }
                                Surface(
                                    onClick = { riskLevel = value },
                                    shape = RoundedCornerShape(12),
                                    color = if (isSelected) chipColor else chipColor.copy(alpha = 0.08f)
                                ) {
                                    Text(
                                        label,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isSelected) Color.White else chipColor
                                    )
                                }
                            }
                        }
                    }

                    // Execution mode
                    Column {
                        Text("执行模式", fontSize = 13.sp, color = SmartColors.textTertiary(), fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "learn_first_then_replay" to "学习后复用",
                                "vision_only" to "纯视觉",
                                "route_only" to "纯路线"
                            ).forEach { (value, label) ->
                                val isSelected = executionMode == value
                                Surface(
                                    onClick = { executionMode = value },
                                    shape = RoundedCornerShape(12),
                                    color = if (isSelected) Color(0xFFF59E0B) else Color(0xFFF59E0B).copy(alpha = 0.08f)
                                ) {
                                    Text(
                                        label,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isSelected) Color.White else Color(0xFFF59E0B)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Create button ──
        item {
            Spacer(Modifier.height(8.dp))
            SmartButton(
                text = "创建",
                onClick = {
                    val task = TaskEntity(
                        taskId = UUID.randomUUID().toString(),
                        name = taskName.ifEmpty { "未命名任务" },
                        description = taskDescription,
                        targetAppName = targetApp,
                        triggerType = triggerType,
                        triggerTime = triggerTime,
                        triggerRepeat = triggerRepeat,
                        riskLevel = riskLevel,
                        executionMode = executionMode,
                        status = "draft"
                    )
                    onTaskCreated(task)
                },
                enabled = taskName.isNotBlank(),
                icon = Icons.Outlined.Check
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Trigger type visual selector ──
@Composable
private fun TriggerTypeSelector(
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            Triple("manual", Icons.Outlined.TouchApp, "手动"),
            Triple("schedule", Icons.Outlined.Schedule, "定时"),
            Triple("notification", Icons.Outlined.Notifications, "通知")
        ).forEach { (type, icon, label) ->
            val isSelected = selected == type
            val activeColor = when (type) {
                "manual" -> SmartColors.accent()
                "schedule" -> Color(0xFF3B82F6)
                "notification" -> Color(0xFFF59E0B)
                else -> SmartColors.textTertiary()
            }
            Surface(
                onClick = { onSelect(type) },
                shape = RoundedCornerShape(14),
                color = if (isSelected) activeColor.copy(alpha = 0.12f) else Color.Transparent,
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (isSelected) Modifier.background(
                            activeColor.copy(alpha = 0.12f),
                            RoundedCornerShape(14.dp)
                        ) else Modifier
                    )
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10),
                        color = if (isSelected) activeColor else SmartColors.textTertiary().copy(alpha = 0.1f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (isSelected) Color.White else SmartColors.textTertiary()
                            )
                        }
                    }
                    Text(
                        label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) activeColor else SmartColors.textTertiary()
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════
// SHARED COMPONENTS — Welcome card, Chat bubble, etc.
// ════════════════════════════════════════════════════════════════

// ── AI Welcome card with gradient header and example chips ──
@Composable
private fun AiWelcomeCard(onExampleClick: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(20),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Gradient header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                SmartColors.accent().copy(alpha = 0.15f),
                                Color(0xFF8B5CF6).copy(alpha = 0.12f)
                            )
                        ),
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    )
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // AI avatar with gradient
                    Surface(
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp),
                        color = Color.Transparent
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            SmartColors.accent(),
                                            Color(0xFF8B5CF6)
                                        )
                                    ),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = Color.White
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "你想让手机自动完成什么？",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "用自然语言描述，AI 会帮你解析成可执行的任务",
                        fontSize = 14.sp,
                        color = SmartColors.textSecondary()
                    )
                }
            }

            // Example chips
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple("⏰", "每天早上9点打开淘宝收金币", Icons.Outlined.Schedule),
                    Triple("💬", "收到微信消息后打开查看", Icons.Outlined.Notifications),
                    Triple("⚙️", "打开设置检查系统更新", Icons.Outlined.Settings)
                ).forEach { (emoji, example, icon) ->
                    Surface(
                        onClick = { onExampleClick(example.replace(Regex("[\\r\\n]+"), "")) },
                        shape = RoundedCornerShape(14),
                        color = SmartColors.accent().copy(alpha = 0.06f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10),
                                color = SmartColors.accent().copy(alpha = 0.1f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = SmartColors.accent()
                                    )
                                }
                            }
                            Text(
                                example,
                                fontSize = 14.sp,
                                color = SmartColors.accent(),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Chat bubble ──
@Composable
private fun ChatBubble(msg: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!msg.isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(SmartColors.accent(), Color(0xFF8B5CF6))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
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
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        }
    }
}

// ── Analyzing indicator with typing animation ──
@Composable
private fun AnalyzingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(SmartColors.accent(), Color(0xFF8B5CF6))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
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
                    color = SmartColors.accent()
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
            Surface(
                shape = RoundedCornerShape(10),
                color = SmartColors.danger().copy(alpha = 0.12f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Error,
                        contentDescription = null,
                        tint = SmartColors.danger(),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Text(message, fontSize = 14.sp, color = SmartColors.danger())
        }
    }
}

// ── Editable TaskSpecConfirmCard ──
@Composable
private fun EditableTaskSpecConfirmCard(
    spec: TaskSpec,
    editName: String,
    onEditNameChange: (String) -> Unit,
    editDescription: String,
    onEditDescriptionChange: (String) -> Unit,
    editTargetApp: String,
    onEditTargetAppChange: (String) -> Unit,
    editTriggerType: String,
    onEditTriggerTypeChange: (String) -> Unit,
    editRiskLevel: String,
    onEditRiskLevelChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Gradient header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                SmartColors.accent().copy(alpha = 0.12f),
                                Color(0xFF8B5CF6).copy(alpha = 0.08f)
                            )
                        ),
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10),
                        color = SmartColors.accent().copy(alpha = 0.15f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.TaskAlt,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = SmartColors.accent()
                            )
                        }
                    }
                    Column {
                        Text("任务解析结果", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Text("AI 已理解你的需求，可编辑调整", fontSize = 13.sp, color = SmartColors.textSecondary())
                    }
                }
            }

            // Editable fields
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Task name - editable
                EditableSpecRow(
                    icon = Icons.Outlined.Label,
                    label = "任务名称",
                    value = editName,
                    onValueChange = onEditNameChange,
                    color = SmartColors.accent()
                )

                // Description - editable
                EditableSpecRow(
                    icon = Icons.Outlined.Notes,
                    label = "描述",
                    value = editDescription,
                    onValueChange = onEditDescriptionChange,
                    color = SmartColors.accent()
                )

                // Target app - editable with app picker
                Column {
                    EditableSpecRow(
                        icon = Icons.Outlined.Apps,
                        label = "目标应用",
                        value = editTargetApp,
                        onValueChange = onEditTargetAppChange,
                        color = Color(0xFF8B5CF6)
                    )
                    Spacer(Modifier.height(4.dp))
                    AppPickerChip(onAppSelected = onEditTargetAppChange)
                }

                // Trigger type - display
                SpecRow(
                    icon = Icons.Outlined.Schedule,
                    label = "触发方式",
                    value = formatTrigger(spec.trigger),
                    color = Color(0xFF3B82F6)
                )

                // Risk level - display
                SpecRow(
                    icon = Icons.Outlined.Shield,
                    label = "风险等级",
                    value = formatRisk(spec.risk),
                    color = when (spec.risk.level) {
                        "high", "forbidden" -> SmartColors.danger()
                        "medium" -> SmartColors.warning()
                        else -> SmartColors.success()
                    }
                )

                // Execution mode - display
                SpecRow(
                    icon = Icons.Outlined.PlayLesson,
                    label = "执行方式",
                    value = "首次学习，成功后复用路线",
                    color = Color(0xFFF59E0B)
                )

                Spacer(Modifier.height(4.dp))

                // Confidence bar
                val confidence = spec.targetApp?.confidence ?: 0.8f
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10),
                        color = SmartColors.accent().copy(alpha = 0.1f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.Speed,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = SmartColors.accent()
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
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

                Spacer(Modifier.height(12.dp))

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
    }
}

// ── Editable spec row with inline text field ──
@Composable
private fun EditableSpecRow(
    icon: ImageVector,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10),
            color = color.copy(alpha = 0.1f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = color
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, color = SmartColors.textTertiary())
            Spacer(Modifier.height(2.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(8),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = color.copy(alpha = 0.5f),
                    unfocusedBorderColor = SmartColors.borderSubtle().copy(alpha = 0.3f),
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                ),
                singleLine = true
            )
        }
    }
}

// ── Icon-based spec row (read-only) ──
@Composable
private fun SpecRow(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10),
            color = color.copy(alpha = 0.1f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = color
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, color = SmartColors.textTertiary())
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ── Format helpers ──

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


