package com.smarttasker.ui.routeeditor

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.data.entity.RouteStepEntity
import com.smarttasker.ui.theme.SmartColors

// ── Step type color mapping (Precision Instrument palette) ──
private val StepTypeColorMap = mapOf(
    "tap"       to Color(0xFF3B82F6),  // Blue
    "input"     to Color(0xFF8B5CF6),  // Purple
    "swipe"     to Color(0xFFF97316),  // Orange
    "wait"      to Color(0xFF9CA3AF),  // Gray
    "back"      to Color(0xFFEF4444),  // Red
    "home"      to Color(0xFF22C55E),  // Green
    "open_app"  to Color(0xFF06B6D4),  // Cyan
    "assert"    to Color(0xFF10B981),  // Emerald
    "confirm"   to Color(0xFFEAB308),  // Yellow
    "finish"    to Color(0xFFEF4444)   // Red
)

private val StepTypeIconMap = mapOf(
    "tap"       to Icons.Outlined.TouchApp,
    "input"     to Icons.Outlined.Keyboard,
    "swipe"     to Icons.Outlined.Swipe,
    "wait"      to Icons.Outlined.HourglassTop,
    "back"      to Icons.Outlined.ArrowBack,
    "home"      to Icons.Outlined.Home,
    "open_app"  to Icons.Outlined.Launch,
    "assert"    to Icons.Outlined.CheckCircle,
    "confirm"   to Icons.Outlined.Warning,
    "finish"    to Icons.Outlined.Flag
)

/**
 * 路线编辑器界面 — Precision Instrument aesthetic
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteEditorScreen(
    viewModel: RouteEditorViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.routeName.ifEmpty { "路线编辑器" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = "${uiState.steps.size} 步骤",
                            fontSize = 12.sp,
                            color = SmartColors.textTertiary()
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    // 保存按钮
                    IconButton(
                        onClick = { viewModel.saveRoute() },
                        enabled = !uiState.isSaving
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = SmartColors.accent()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "保存",
                                tint = SmartColors.accent()
                            )
                        }
                    }

                    // 导出按钮
                    IconButton(onClick = { viewModel.exportRoute() }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "导出",
                            tint = SmartColors.textSecondary()
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.addStep() },
                containerColor = SmartColors.accent(),
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "添加步骤"
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedContent(
                targetState = when {
                    uiState.isLoading -> "loading"
                    uiState.error != null -> "error"
                    uiState.steps.isEmpty() -> "empty"
                    else -> "content"
                },
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith
                    fadeOut(animationSpec = tween(300))
                },
                label = "contentTransition"
            ) { state ->
                when (state) {
                    "loading" -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = SmartColors.accent()
                        )
                    }

                    "error" -> {
                        ErrorMessage(
                            message = uiState.error!!,
                            onRetry = { viewModel.clearError() },
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    "empty" -> {
                        EmptyStepsMessage(
                            onAddStep = { viewModel.addStep() },
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    "content" -> {
                        StepsList(
                            steps = uiState.steps,
                            onStepClick = { viewModel.selectStep(it) },
                            onStepToggle = { viewModel.toggleStepEnabled(it) },
                            onStepDelete = { viewModel.deleteStep(it) },
                            onStepMove = { from, to -> viewModel.moveStep(from, to) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // 步骤编辑对话框
            if (uiState.showStepEditDialog && uiState.editingStep != null) {
                StepEditDialog(
                    step = uiState.editingStep!!,
                    stepIndex = uiState.selectedStepIndex,
                    onSave = { step ->
                        viewModel.updateStep(uiState.selectedStepIndex, step)
                        viewModel.dismissStepEditDialog()
                    },
                    onDismiss = { viewModel.dismissStepEditDialog() }
                )
            }
        }
    }
}

/**
 * 步骤列表 — Timeline style with vertical connector lines
 */
@Composable
private fun StepsList(
    steps: List<RouteStepEntity>,
    onStepClick: (Int) -> Unit,
    onStepToggle: (Int) -> Unit,
    onStepDelete: (Int) -> Unit,
    onStepMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        itemsIndexed(
            items = steps,
            key = { _, step -> step.stepId }
        ) { index, step ->
            StepItem(
                step = step,
                stepIndex = index,
                isLast = index == steps.lastIndex,
                onClick = { onStepClick(index) },
                onToggle = { onStepToggle(index) },
                onDelete = { onStepDelete(index) },
                onMoveUp = { if (index > 0) onStepMove(index, index - 1) },
                onMoveDown = { if (index < steps.size - 1) onStepMove(index, index + 1) }
            )
        }
        item { Spacer(Modifier.height(100.dp)) }
    }
}

/**
 * 单个步骤项 — Timeline card with connector line and type-colored circle
 */
@Composable
private fun StepItem(
    step: RouteStepEntity,
    stepIndex: Int,
    isLast: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val stepColor = StepTypeColorMap[step.type] ?: SmartColors.accent()
    val stepIcon = StepTypeIconMap[step.type] ?: Icons.Outlined.Circle
    val connectorColor = SmartColors.borderSubtle()

    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(step.stepId) {
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = tween(durationMillis = 300, easing = EaseOutCubic)
        ) + fadeIn(animationSpec = tween(300))
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // ── Left: Timeline connector ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(44.dp)
            ) {
                // Top connector line
                Canvas(modifier = Modifier.width(2.dp).height(12.dp)) {
                    drawLine(
                        color = connectorColor,
                        start = Offset(size.width / 2f, 0f),
                        end = Offset(size.width / 2f, size.height),
                        strokeWidth = 2f
                    )
                }

                // Step circle with icon
                val circleBgColor by animateColorAsState(
                    targetValue = if (step.enabled) stepColor.copy(alpha = 0.15f) else SmartColors.textTertiary().copy(alpha = 0.08f),
                    label = "circleBg"
                )
                val circleBorderColor by animateColorAsState(
                    targetValue = if (step.enabled) stepColor else SmartColors.textTertiary().copy(alpha = 0.3f),
                    label = "circleBorder"
                )

                Surface(
                    shape = CircleShape,
                    color = circleBgColor,
                    border = BorderStroke(2.dp, circleBorderColor),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            stepIcon,
                            contentDescription = step.type,
                            modifier = Modifier.size(18.dp),
                            tint = if (step.enabled) stepColor else SmartColors.textTertiary()
                        )
                    }
                }

                // Bottom connector line
                if (!isLast) {
                    Canvas(modifier = Modifier.width(2.dp).weight(1f)) {
                        drawLine(
                            color = connectorColor,
                            start = Offset(size.width / 2f, 0f),
                            end = Offset(size.width / 2f, size.height),
                            strokeWidth = 2f
                        )
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }

            Spacer(Modifier.width(12.dp))

            // ── Right: Step content card ──
            Column(modifier = Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 4.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, SmartColors.borderSubtle()),
                    onClick = onClick
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Row 1: Drag handle + Step index + Summary + Actions
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Drag handle
                            Icon(
                                Icons.Outlined.DragIndicator,
                                contentDescription = "拖拽",
                                modifier = Modifier.size(16.dp),
                                tint = SmartColors.textTertiary().copy(alpha = 0.5f)
                            )

                            // Step index badge
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = stepColor.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    "#${stepIndex + 1}",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = stepColor
                                )
                            }

                            // Summary text
                            Text(
                                step.summary.ifEmpty { getStepTypeName(step.type) },
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = if (step.enabled) MaterialTheme.colorScheme.onSurface else SmartColors.textTertiary(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Action buttons
                            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                                // Move up
                                IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
                                    Icon(
                                        Icons.Outlined.KeyboardArrowUp,
                                        contentDescription = "上移",
                                        modifier = Modifier.size(16.dp),
                                        tint = SmartColors.textTertiary()
                                    )
                                }
                                // Move down
                                IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
                                    Icon(
                                        Icons.Outlined.KeyboardArrowDown,
                                        contentDescription = "下移",
                                        modifier = Modifier.size(16.dp),
                                        tint = SmartColors.textTertiary()
                                    )
                                }
                                // Delete
                                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = "删除",
                                        modifier = Modifier.size(16.dp),
                                        tint = SmartColors.danger().copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }

                        // Row 2: Tags + Toggle
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Type pill
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = stepColor.copy(alpha = 0.1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        StepTypeIconMap[step.type] ?: Icons.Outlined.Circle,
                                        contentDescription = null,
                                        modifier = Modifier.size(10.dp),
                                        tint = stepColor
                                    )
                                    Text(
                                        getStepTypeName(step.type),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = stepColor
                                    )
                                }
                            }

                            // Locator strategy pill
                            val locatorColor = when (step.locatorStrategy) {
                                "coordinate" -> SmartColors.warning()
                                "text" -> SmartColors.accent()
                                "resource_id" -> Color(0xFF8B5CF6)
                                "content_desc" -> Color(0xFF06B6D4)
                                else -> SmartColors.textTertiary()
                            }
                            val locatorLabel = when (step.locatorStrategy) {
                                "text" -> "文本定位"
                                "coordinate" -> "坐标定位"
                                "resource_id" -> "ID定位"
                                "content_desc" -> "内容描述"
                                else -> step.locatorStrategy
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = locatorColor.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    locatorLabel,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = locatorColor
                                )
                            }

                            Spacer(Modifier.weight(1f))

                            // Enabled toggle
                            Switch(
                                checked = step.enabled,
                                onCheckedChange = { onToggle() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = SmartColors.accent(),
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = SmartColors.textTertiary().copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 步骤类型图标
 */
@Composable
private fun StepTypeIcon(type: String) {
    val color = StepTypeColorMap[type] ?: SmartColors.textTertiary()
    val icon = StepTypeIconMap[type] ?: Icons.Outlined.Circle

    Icon(
        imageVector = icon,
        contentDescription = type,
        tint = color,
        modifier = Modifier.size(20.dp)
    )
}

/**
 * 获取步骤类型名称
 */
private fun getStepTypeName(type: String): String {
    return when (type) {
        "tap" -> "点击"
        "input" -> "输入"
        "swipe" -> "滑动"
        "back" -> "返回"
        "wait" -> "等待"
        "home" -> "主页"
        "open_app" -> "打开应用"
        "assert" -> "断言"
        "confirm" -> "确认"
        "finish" -> "完成"
        else -> type
    }
}

/**
 * 错误消息
 */
@Composable
private fun ErrorMessage(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = SmartColors.danger().copy(alpha = 0.12f),
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "错误",
                    tint = SmartColors.danger(),
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = message,
            color = SmartColors.textSecondary(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = SmartColors.accent()
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("重试")
        }
    }
}

/**
 * 空步骤消息
 */
@Composable
private fun EmptyStepsMessage(
    onAddStep: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = SmartColors.accent().copy(alpha = 0.08f),
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Route,
                    contentDescription = "空路线",
                    tint = SmartColors.accent().copy(alpha = 0.6f),
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "暂无步骤",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "点击下方按钮添加第一个步骤",
            color = SmartColors.textTertiary(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onAddStep,
            colors = ButtonDefaults.buttonColors(
                containerColor = SmartColors.accent()
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.height(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "添加",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("添加步骤", fontWeight = FontWeight.Medium)
        }
    }
}
