package com.smarttasker.ui.routeeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.data.entity.RouteStepEntity
import com.smarttasker.ui.theme.*

/**
 * 路线编辑器界面
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
                    Text(
                        text = uiState.routeName.ifEmpty { "路线编辑器" },
                        color = LinearTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = LinearTextPrimary
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
                                color = LinearBrandIndigo
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "保存",
                                tint = LinearBrandIndigo
                            )
                        }
                    }
                    
                    // 导出按钮
                    IconButton(onClick = { viewModel.exportRoute() }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "导出",
                            tint = LinearTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LinearBgPanel
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.addStep() },
                containerColor = LinearBrandIndigo,
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "添加步骤"
                )
            }
        },
        containerColor = LinearBgMarketing
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = LinearBrandIndigo
                    )
                }
                
                uiState.error != null -> {
                    ErrorMessage(
                        message = uiState.error!!,
                        onRetry = { viewModel.clearError() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                uiState.steps.isEmpty() -> {
                    EmptyStepsMessage(
                        onAddStep = { viewModel.addStep() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                else -> {
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
    }

    // 步骤编辑对话框
    if (uiState.showStepEditDialog && uiState.editingStep != null) {
        StepEditDialog(
            step = uiState.editingStep!!,
            stepIndex = uiState.selectedStepIndex,
            onSave = { step -> viewModel.updateStep(uiState.selectedStepIndex, step) },
            onDismiss = { viewModel.dismissStepEditDialog() }
        )
    }
}

/**
 * 步骤列表
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        itemsIndexed(steps) { index, step ->
            StepItem(
                step = step,
                stepIndex = index,
                onClick = { onStepClick(index) },
                onToggle = { onStepToggle(index) },
                onDelete = { onStepDelete(index) },
                onMoveUp = { if (index > 0) onStepMove(index, index - 1) },
                onMoveDown = { if (index < steps.size - 1) onStepMove(index, index + 1) }
            )
        }
    }
}

/**
 * 单个步骤项
 */
@Composable
private fun StepItem(
    step: RouteStepEntity,
    stepIndex: Int,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (step.enabled) LinearBgSurface else LinearBgSurface.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 步骤序号
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(LinearBrandIndigo.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${stepIndex + 1}",
                    color = LinearBrandIndigo,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 步骤信息
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 步骤类型图标
                    StepTypeIcon(type = step.type)
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // 步骤类型
                    Text(
                        text = getStepTypeName(step.type),
                        color = LinearTextPrimary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 步骤摘要
                Text(
                    text = step.summary.ifEmpty { "无描述" },
                    color = LinearTextTertiary,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
            
            // 操作按钮
            Row {
                // 启用/禁用开关
                Switch(
                    checked = step.enabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = LinearBrandIndigo,
                        checkedTrackColor = LinearBrandIndigo.copy(alpha = 0.3f)
                    )
                )
                
                // 移动按钮
                IconButton(
                    onClick = onMoveUp,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "上移",
                        tint = LinearTextTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                IconButton(
                    onClick = onMoveDown,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "下移",
                        tint = LinearTextTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                // 删除按钮
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = LinearRed,
                        modifier = Modifier.size(20.dp)
                    )
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
    val (icon, color) = when (type) {
        "tap" -> Icons.Default.TouchApp to LinearBrandIndigo
        "input" -> Icons.Default.Keyboard to LinearGreen
        "swipe" -> Icons.Default.Swipe to LinearAccentViolet
        "back" -> Icons.Default.ArrowBack to LinearTextTertiary
        "wait" -> Icons.Default.Timer to LinearYellow
        "open_app" -> Icons.Default.Apps to LinearBlue
        "assert" -> Icons.Default.CheckCircle to LinearGreen
        "confirm" -> Icons.Default.Warning to LinearYellow
        "finish" -> Icons.Default.Flag to LinearRed
        else -> Icons.Default.Circle to LinearTextTertiary
    }
    
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
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = "错误",
            tint = LinearRed,
            modifier = Modifier.size(48.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = message,
            color = LinearTextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = LinearBrandIndigo
            )
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
        Icon(
            imageVector = Icons.Default.Route,
            contentDescription = "空路线",
            tint = LinearTextTertiary,
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "暂无步骤",
            color = LinearTextPrimary,
            fontWeight = FontWeight.Medium,
            fontSize = 18.sp
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "点击下方按钮添加第一个步骤",
            color = LinearTextTertiary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onAddStep,
            colors = ButtonDefaults.buttonColors(
                containerColor = LinearBrandIndigo
            )
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "添加"
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("添加步骤")
        }
    }
}
