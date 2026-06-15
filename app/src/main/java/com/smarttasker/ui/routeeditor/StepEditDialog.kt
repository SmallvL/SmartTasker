package com.smarttasker.ui.routeeditor

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.data.entity.RouteStepEntity
import com.smarttasker.ui.theme.SmartColors

// ── Step type color mapping for dialog ──
private val DialogStepTypeColors = mapOf(
    "tap"       to Color(0xFF3B82F6),
    "input"     to Color(0xFF8B5CF6),
    "swipe"     to Color(0xFFF97316),
    "wait"      to Color(0xFF9CA3AF),
    "back"      to Color(0xFFEF4444),
    "home"      to Color(0xFF22C55E),
    "open_app"  to Color(0xFF06B6D4),
    "assert"    to Color(0xFF10B981),
    "confirm"   to Color(0xFFEAB308),
    "finish"    to Color(0xFFEF4444)
)

/**
 * 步骤编辑对话框 — Precision Instrument aesthetic
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

    val currentStepColor = DialogStepTypeColors[selectedType] ?: SmartColors.accent()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* 阻止点击穿透 */ }
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Header ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = currentStepColor.copy(alpha = 0.12f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = null,
                            tint = currentStepColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "编辑步骤",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "#${stepIndex + 1}",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SmartColors.textTertiary()
                    )
                }
            }

            Divider(color = SmartColors.borderSubtle())

            // ── 步骤类型 ──
            DialogSectionHeader(title = "步骤类型", icon = Icons.Outlined.Category)

            StepTypeSelector(
                selectedType = selectedType,
                onTypeSelected = { selectedType = it }
            )

            // ── 步骤描述 ──
            DialogSectionHeader(title = "步骤描述", icon = Icons.Outlined.Description)

            OutlinedTextField(
                value = summary,
                onValueChange = { summary = it },
                placeholder = { Text("输入步骤描述", color = SmartColors.textTertiary()) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = SmartColors.accent(),
                    unfocusedBorderColor = SmartColors.borderSubtle(),
                    cursorColor = SmartColors.accent()
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // ── 定位策略 ──
            DialogSectionHeader(title = "定位策略", icon = Icons.Outlined.MyLocation)

            LocatorStrategySelector(
                selectedStrategy = locatorStrategy,
                onStrategySelected = { locatorStrategy = it }
            )

            // 定位值
            OutlinedTextField(
                value = locatorValue,
                onValueChange = { locatorValue = it },
                placeholder = { Text("输入定位值", color = SmartColors.textTertiary()) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = SmartColors.accent(),
                    unfocusedBorderColor = SmartColors.borderSubtle(),
                    cursorColor = SmartColors.accent()
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // ── 高级设置 ──
            DialogSectionHeader(title = "高级设置", icon = Icons.Outlined.Tune)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = waitTimeMs,
                    onValueChange = { waitTimeMs = it },
                    label = { Text("等待时间", fontSize = 12.sp) },
                    suffix = {
                        Text(
                            "ms",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = SmartColors.textTertiary()
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = SmartColors.accent(),
                        unfocusedBorderColor = SmartColors.borderSubtle(),
                        cursorColor = SmartColors.accent()
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = maxRetries,
                    onValueChange = { maxRetries = it },
                    label = { Text("重试次数", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = SmartColors.accent(),
                        unfocusedBorderColor = SmartColors.borderSubtle(),
                        cursorColor = SmartColors.accent()
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // 需要确认开关
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(1.dp, SmartColors.borderSubtle())
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "执行前确认",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "执行此步骤前需要用户确认",
                            color = SmartColors.textTertiary(),
                            fontSize = 12.sp
                        )
                    }

                    Switch(
                        checked = requiresConfirmation,
                        onCheckedChange = { requiresConfirmation = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = SmartColors.accent(),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = SmartColors.textTertiary().copy(alpha = 0.3f)
                        )
                    )
                }
            }

            // ── 按钮行 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("取消")
                }
                Button(
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
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SmartColors.accent()
                    )
                ) {
                    Text("保存", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/**
 * 章节标题
 */
@Composable
private fun DialogSectionHeader(
    title: String,
    icon: ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = SmartColors.accent().copy(alpha = 0.1f),
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = SmartColors.accent(),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Text(
            text = title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = SmartColors.textSecondary()
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
            val typeColor = DialogStepTypeColors[type] ?: SmartColors.accent()
            val animatedColor by animateColorAsState(
                targetValue = if (isSelected) typeColor else MaterialTheme.colorScheme.surfaceVariant,
                animationSpec = tween(durationMillis = 200),
                label = "chipColor"
            )

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onTypeSelected(type) },
                shape = RoundedCornerShape(10.dp),
                color = animatedColor.copy(alpha = if (isSelected) 0.15f else 1f),
                border = androidx.compose.foundation.BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) typeColor else SmartColors.borderSubtle()
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = typeColor
                        )
                    }
                    Text(
                        text = name,
                        color = if (isSelected) typeColor else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                }
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
                targetValue = if (isSelected) SmartColors.accent() else MaterialTheme.colorScheme.surfaceVariant,
                animationSpec = tween(durationMillis = 200),
                label = "chipColor"
            )

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onStrategySelected(strategy) },
                shape = RoundedCornerShape(10.dp),
                color = animatedColor.copy(alpha = if (isSelected) 0.15f else 1f),
                border = androidx.compose.foundation.BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) SmartColors.accent() else SmartColors.borderSubtle()
                )
            ) {
                Text(
                    text = name,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    color = if (isSelected) SmartColors.accent() else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    fontSize = 13.sp
                )
            }
        }
    }
}
