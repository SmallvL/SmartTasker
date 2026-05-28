package com.smarttasker.ui.routeeditor

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.data.entity.RouteStepEntity
import com.smarttasker.ui.theme.*

/**
 * 步骤编辑对话框
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun StepEditDialog(
    step: RouteStepEntity,
    stepIndex: Int,
    onSave: (RouteStepEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var editedStep by remember { mutableStateOf(step) }
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
                fontWeight = FontWeight.Medium
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
                Text(
                    text = "步骤类型",
                    color = LinearTextSecondary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                
                StepTypeSelector(
                    selectedType = selectedType,
                    onTypeSelected = { selectedType = it }
                )
                
                // 步骤摘要
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    label = { Text("步骤描述") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LinearTextPrimary,
                        unfocusedTextColor = LinearTextPrimary,
                        focusedBorderColor = LinearBrandIndigo,
                        unfocusedBorderColor = LinearTextTertiary
                    )
                )
                
                // 定位策略
                Text(
                    text = "定位策略",
                    color = LinearTextSecondary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                
                LocatorStrategySelector(
                    selectedStrategy = locatorStrategy,
                    onStrategySelected = { locatorStrategy = it }
                )
                
                // 定位值
                OutlinedTextField(
                    value = locatorValue,
                    onValueChange = { locatorValue = it },
                    label = { Text("定位值") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LinearTextPrimary,
                        unfocusedTextColor = LinearTextPrimary,
                        focusedBorderColor = LinearBrandIndigo,
                        unfocusedBorderColor = LinearTextTertiary
                    )
                )
                
                // 等待时间
                OutlinedTextField(
                    value = waitTimeMs,
                    onValueChange = { waitTimeMs = it },
                    label = { Text("等待时间 (ms)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LinearTextPrimary,
                        unfocusedTextColor = LinearTextPrimary,
                        focusedBorderColor = LinearBrandIndigo,
                        unfocusedBorderColor = LinearTextTertiary
                    )
                )
                
                // 重试次数
                OutlinedTextField(
                    value = maxRetries,
                    onValueChange = { maxRetries = it },
                    label = { Text("最大重试次数") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LinearTextPrimary,
                        unfocusedTextColor = LinearTextPrimary,
                        focusedBorderColor = LinearBrandIndigo,
                        unfocusedBorderColor = LinearTextTertiary
                    )
                )
                
                // 需要确认
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = requiresConfirmation,
                        onCheckedChange = { requiresConfirmation = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = LinearBrandIndigo
                        )
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = "执行前需要确认",
                        color = LinearTextPrimary,
                        fontSize = 14.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updatedStep = editedStep.copy(
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
            FilterChip(
                selected = selectedType == type,
                onClick = { onTypeSelected(type) },
                label = {
                    Text(
                        text = name,
                        color = if (selectedType == type) Color.White else LinearTextPrimary
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = LinearBrandIndigo
                )
            )
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
            FilterChip(
                selected = selectedStrategy == strategy,
                onClick = { onStrategySelected(strategy) },
                label = {
                    Text(
                        text = name,
                        color = if (selectedStrategy == strategy) Color.White else LinearTextPrimary
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = LinearBrandIndigo
                )
            )
        }
    }
}
