package com.smarttasker.ui.routeeditor

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.data.entity.RouteStepEntity
import com.smarttasker.ui.theme.*

/**
 * 步骤编辑对话框（使用 AlertDialog）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StepEditDialog(
    step: RouteStepEntity,
    stepIndex: Int,
    onSave: (RouteStepEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedType by remember { mutableStateOf(step.type) }
    var summary by remember { mutableStateOf(step.summary) }
    var locatorStrategy by remember { mutableStateOf(step.locatorStrategy) }
    var locatorValue by remember { mutableStateOf(step.locatorValue) }
    var waitTimeMs by remember { mutableStateOf(step.waitTimeMs.toString()) }
    var maxRetries by remember { mutableStateOf(step.maxRetries.toString()) }
    var requiresConfirmation by remember { mutableStateOf(step.requiresConfirmation) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "编辑步骤 ${stepIndex + 1}",
                color = LinearTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 步骤类型选择
                SectionHeader(title = "步骤类型", icon = Icons.Default.Category)
                
                StepTypeSelector(
                    selectedType = selectedType,
                    onTypeSelected = { selectedType = it }
                )
                
                // 步骤摘要
                SectionHeader(title = "步骤描述", icon = Icons.Default.Description)
                
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    placeholder = { Text("输入步骤描述", color = LinearTextTertiary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LinearTextPrimary,
                        unfocusedTextColor = LinearTextPrimary,
                        focusedBorderColor = LinearBrandIndigo,
                        unfocusedBorderColor = LinearBorderDefault,
                        cursorColor = LinearBrandIndigo
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                
                // 定位策略
                SectionHeader(title = "定位策略", icon = Icons.Default.MyLocation)
                
                LocatorStrategySelector(
                    selectedStrategy = locatorStrategy,
                    onStrategySelected = { locatorStrategy = it }
                )
                
                // 定位值
                OutlinedTextField(
                    value = locatorValue,
                    onValueChange = { locatorValue = it },
                    placeholder = { Text("输入定位值", color = LinearTextTertiary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LinearTextPrimary,
                        unfocusedTextColor = LinearTextPrimary,
                        focusedBorderColor = LinearBrandIndigo,
                        unfocusedBorderColor = LinearBorderDefault,
                        cursorColor = LinearBrandIndigo
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                
                // 高级设置
                SectionHeader(title = "高级设置", icon = Icons.Default.Tune)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 等待时间
                    OutlinedTextField(
                        value = waitTimeMs,
                        onValueChange = { waitTimeMs = it },
                        label = { Text("等待时间", fontSize = 12.sp) },
                        suffix = { Text("ms", fontSize = 12.sp, color = LinearTextTertiary) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = LinearTextPrimary,
                            unfocusedTextColor = LinearTextPrimary,
                            focusedBorderColor = LinearBrandIndigo,
                            unfocusedBorderColor = LinearBorderDefault,
                            cursorColor = LinearBrandIndigo
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    // 重试次数
                    OutlinedTextField(
                        value = maxRetries,
                        onValueChange = { maxRetries = it },
                        label = { Text("重试次数", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = LinearTextPrimary,
                            unfocusedTextColor = LinearTextPrimary,
                            focusedBorderColor = LinearBrandIndigo,
                            unfocusedBorderColor = LinearBorderDefault,
                            cursorColor = LinearBrandIndigo
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                
                // 需要确认开关
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(LinearBgPanel)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "执行前确认",
                            color = LinearTextPrimary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "执行此步骤前需要用户确认",
                            color = LinearTextTertiary,
                            fontSize = 12.sp
                        )
                    }
                    
                    Switch(
                        checked = requiresConfirmation,
                        onCheckedChange = { requiresConfirmation = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = LinearBrandIndigo,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = LinearTextTertiary.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updatedStep = step.copy(
                        type = selectedType,
                        summary = summary,
                        locatorStrategy = locatorStrategy,
                        locatorValue = locatorValue,
                        waitTimeMs = waitTimeMs.toLongOrNull() ?: 1000L,
                        maxRetries = maxRetries.toIntOrNull() ?: 2,
                        requiresConfirmation = requiresConfirmation
                    )
                    onSave(updatedStep)
                }
            ) {
                Text(
                    text = "保存",
                    color = LinearBrandIndigo,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "取消",
                    color = LinearTextTertiary
                )
            }
        },
        containerColor = LinearBgSurface,
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * 章节标题
 */
@Composable
private fun SectionHeader(
    title: String,
    icon: ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = LinearBrandIndigo,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            color = LinearTextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )
    }
}

/**
 * 步骤类型选择器
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun StepTypeSelector(
    selectedType: String,
    onTypeSelected: (String) -> Unit
) {
    val types = listOf(
        "tap" to "点击",
        "input" to "输入",
        "swipe" to "滑动",
        "back" to "返回",
        "wait" to "等待",
        "open_app" to "打开应用",
        "assert" to "断言",
        "confirm" to "确认",
        "finish" to "完成"
    )
    
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        types.forEach { (type, name) ->
            val isSelected = selectedType == type
            val animatedColor by animateColorAsState(
                targetValue = if (isSelected) LinearBrandIndigo else LinearBgPanel,
                animationSpec = tween(durationMillis = 200),
                label = "chipColor"
            )
            
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(animatedColor)
                    .then(
                        if (isSelected) Modifier.border(
                            width = 1.dp,
                            color = LinearBrandIndigo.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp)
                        ) else Modifier
                    )
                    .clickable { onTypeSelected(type) }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = name,
                    color = if (isSelected) Color.White else LinearTextPrimary,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * 定位策略选择器
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LocatorStrategySelector(
    selectedStrategy: String,
    onStrategySelected: (String) -> Unit
) {
    val strategies = listOf(
        "text" to "文本",
        "content_desc" to "内容描述",
        "resource_id" to "资源ID",
        "coordinate" to "坐标",
        "visual_description" to "视觉描述"
    )
    
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        strategies.forEach { (strategy, name) ->
            val isSelected = selectedStrategy == strategy
            val animatedColor by animateColorAsState(
                targetValue = if (isSelected) LinearBrandIndigo else LinearBgPanel,
                animationSpec = tween(durationMillis = 200),
                label = "chipColor"
            )
            
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(animatedColor)
                    .then(
                        if (isSelected) Modifier.border(
                            width = 1.dp,
                            color = LinearBrandIndigo.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp)
                        ) else Modifier
                    )
                    .clickable { onStrategySelected(strategy) }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = name,
                    color = if (isSelected) Color.White else LinearTextPrimary,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    fontSize = 14.sp
                )
            }
        }
    }
}
