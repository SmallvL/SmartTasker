package com.smarttasker.ui.templates

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.data.entity.TemplateStepEntity
import com.smarttasker.data.entity.TemplateVersionEntity
import com.smarttasker.data.repository.RouteRepository
import com.smarttasker.data.repository.TemplateRepository
import com.smarttasker.ui.common.SmartCard
import com.smarttasker.ui.theme.SmartColors

// ── Template detail data ──

data class TemplateStepInfo(
    val index: Int,
    val type: String,
    val summary: String,
    val icon: ImageVector = Icons.Outlined.TouchApp,
    val color: Color = Color.Unspecified
)

data class TemplateVersionInfo(
    val version: String,
    val date: String,
    val changeSummary: String,
    val isCurrent: Boolean = false
)

// ── Step type visual mapping (same as RouteStudioScreen) ──

private val StepTypeColors = mapOf(
    "tap" to Color(0xFF3B82F6),
    "input" to Color(0xFF8B5CF6),
    "swipe" to Color(0xFFF97316),
    "wait" to Color(0xFF9CA3AF),
    "back" to Color(0xFFEF4444),
    "home" to Color(0xFF22C55E),
    "open_app" to Color(0xFF06B6D4),
    "key" to Color(0xFFEAB308)
)

private val StepTypeIcons = mapOf(
    "tap" to Icons.Outlined.TouchApp,
    "input" to Icons.Outlined.Keyboard,
    "swipe" to Icons.Outlined.Swipe,
    "wait" to Icons.Outlined.HourglassTop,
    "back" to Icons.Outlined.ArrowBack,
    "home" to Icons.Outlined.Home,
    "open_app" to Icons.Outlined.Launch,
    "key" to Icons.Outlined.VpnKey
)

private val StepTypeLabels = mapOf(
    "tap" to "点击",
    "input" to "输入",
    "swipe" to "滑动",
    "wait" to "等待",
    "back" to "返回",
    "home" to "主页",
    "open_app" to "打开应用",
    "key" to "按键"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateDetailScreen(
    templateId: String,
    templateRepo: TemplateRepository,
    routeRepo: RouteRepository,
    onBack: () -> Unit,
    onCreateTask: (String) -> Unit,
    onEditSteps: (String) -> Unit = {},
    onExport: (String) -> Unit = {}
) {
    // ── Load template from database ──
    val templateEntity by templateRepo.getTemplateByIdFlow(templateId)
        .collectAsState(initial = null)

    val template = templateEntity?.let { entity ->
        TemplateInfo(
            id = entity.templateId,
            name = entity.name,
            description = entity.description,
            icon = entity.icon,
            category = entity.category,
            stepCount = entity.stepCount,
            usageCount = entity.usageCount,
            successRate = entity.successRate,
            version = entity.version
        )
    } ?: TemplateInfo(
        id = templateId,
        name = "未知模板",
        description = "",
        icon = "📋",
        category = "通用",
        stepCount = 0,
        usageCount = 0
    )

    val currentVersionCode = templateEntity?.versionCode ?: 1

    val categoryColor = TemplateCategory.values().find { it.label == template.category }?.color
        ?.let { if (it == Color.Unspecified) SmartColors.accent() else it }
        ?: SmartColors.accent()

    // ── Load steps from database ──
    val stepsEntities by templateRepo.getStepsForTemplate(templateId, currentVersionCode)
        .collectAsState(initial = emptyList())

    val steps = stepsEntities.map { stepEntity ->
        TemplateStepInfo(
            index = stepEntity.stepIndex,
            type = stepEntity.type,
            summary = stepEntity.summary,
            icon = StepTypeIcons[stepEntity.type] ?: Icons.Outlined.TouchApp,
            color = StepTypeColors[stepEntity.type] ?: Color(0xFF10A37F)
        )
    }

    // ── Load version history from database ──
    val versionEntities by templateRepo.getVersionsForTemplate(templateId)
        .collectAsState(initial = emptyList())

    val versions = versionEntities.map { vEntity ->
        TemplateVersionInfo(
            version = vEntity.version,
            date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(java.util.Date(vEntity.createdAt)),
            changeSummary = vEntity.changeSummary,
            isCurrent = vEntity.versionCode == currentVersionCode
        )
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var expandedVersion by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模板详情", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { onExport(templateId) }) {
                        Icon(Icons.Outlined.FileUpload, contentDescription = "导出")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // ══════════════════════════════════════
            // Hero Header
            // ══════════════════════════════════════
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = categoryColor.copy(alpha = 0.04f),
                        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Icon in large colored circle
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(categoryColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            template.icon,
                            fontSize = 32.sp
                        )
                    }

                    Spacer(Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            template.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            template.description,
                            fontSize = 13.sp,
                            color = SmartColors.textSecondary(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Category badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = categoryColor.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Label,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = categoryColor
                        )
                        Text(
                            template.category,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = categoryColor
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ══════════════════════════════════════
            // Stats Row
            // ══════════════════════════════════════
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatChip(
                    icon = Icons.Outlined.List,
                    label = "步骤",
                    value = "${template.stepCount}",
                    color = Color(0xFF3B82F6),
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    icon = Icons.Outlined.Group,
                    label = "使用",
                    value = "${template.usageCount}",
                    color = Color(0xFF8B5CF6),
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    icon = Icons.Outlined.CheckCircle,
                    label = "成功率",
                    value = "${(template.successRate * 100).toInt()}%",
                    color = SmartColors.success(),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(20.dp))

            // ══════════════════════════════════════
            // Step List with Timeline Connector
            // ══════════════════════════════════════
            SectionHeaderWithIcon(
                icon = Icons.Outlined.Route,
                title = "步骤流程",
                color = SmartColors.accent()
            )

            Spacer(Modifier.height(8.dp))

            if (steps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "暂无步骤信息",
                        fontSize = 14.sp,
                        color = SmartColors.textTertiary()
                    )
                }
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp)
                ) {
                    steps.forEachIndexed { index, step ->
                        TimelineStepCard(
                            step = step,
                            isLast = index == steps.lastIndex
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ══════════════════════════════════════
            // Version History
            // ══════════════════════════════════════
            SectionHeaderWithIcon(
                icon = Icons.Outlined.History,
                title = "版本历史",
                color = Color(0xFF8B5CF6)
            )

            Spacer(Modifier.height(8.dp))

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                // Version selector dropdown
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, SmartColors.borderSubtle()),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedVersion = !expandedVersion }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Tag,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = SmartColors.accent()
                                )
                                Text(
                                    "v${template.version}",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                if (versions.firstOrNull { it.isCurrent } != null) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = SmartColors.success().copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            "最新",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = SmartColors.success()
                                        )
                                    }
                                }
                            }
                            Icon(
                                if (expandedVersion) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = SmartColors.textTertiary()
                            )
                        }

                        // Version list
                        if (expandedVersion) {
                            versions.forEach { version ->
                                Divider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = SmartColors.borderSubtle().copy(alpha = 0.5f)
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                "v${version.version}",
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                            if (version.isCurrent) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = SmartColors.accent().copy(alpha = 0.12f)
                                                ) {
                                                    Text(
                                                        "当前",
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                                        fontSize = 10.sp,
                                                        color = SmartColors.accent()
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            version.changeSummary,
                                            fontSize = 12.sp,
                                            color = SmartColors.textTertiary()
                                        )
                                    }
                                    Text(
                                        version.date,
                                        fontSize = 11.sp,
                                        color = SmartColors.textTertiary()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ══════════════════════════════════════
            // Action Buttons
            // ══════════════════════════════════════
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                // Primary: Create task from template
                Button(
                    onClick = { onCreateTask(templateId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SmartColors.accent(),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Outlined.Add, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("基于此模板创建任务", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                }

                Spacer(Modifier.height(10.dp))

                // Secondary: Edit steps
                OutlinedButton(
                    onClick = { onEditSteps(templateId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("编辑步骤", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                }

                Spacer(Modifier.height(10.dp))

                // Tertiary row: Export + Delete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Export
                    OutlinedButton(
                        onClick = { onExport(templateId) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SmartColors.borderSubtle()),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = SmartColors.textSecondary()
                        )
                    ) {
                        Icon(Icons.Outlined.FileUpload, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("导出", fontSize = 13.sp)
                    }

                    // Delete
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SmartColors.danger().copy(alpha = 0.3f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = SmartColors.danger()
                        )
                    ) {
                        Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("删除", fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // ── Delete confirmation dialog ──
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            shape = RoundedCornerShape(20.dp),
            title = { Text("删除模板", fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text("确定要删除模板「${template.name}」吗？此操作不可撤销。")
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SmartColors.danger().copy(alpha = 0.06f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SmartColors.danger().copy(alpha = 0.12f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(template.icon, fontSize = 18.sp)
                                }
                            }
                            Column {
                                Text(
                                    template.name,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                                Text(
                                    "${template.stepCount} 步 · ${template.usageCount} 次使用",
                                    fontSize = 12.sp,
                                    color = SmartColors.textTertiary()
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = SmartColors.danger())
                ) { Text("删除", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

// ══════════════════════════════════════════════════════════════════
// Sub-components
// ══════════════════════════════════════════════════════════════════

@Composable
private fun StatChip(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = color
                )
                Text(
                    label,
                    fontSize = 12.sp,
                    color = SmartColors.textTertiary()
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = color
            )
        }
    }
}

@Composable
private fun SectionHeaderWithIcon(
    icon: ImageVector,
    title: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = color.copy(alpha = 0.12f),
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = color
                )
            }
        }
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun TimelineStepCard(
    step: TemplateStepInfo,
    isLast: Boolean
) {
    val connectorColor = SmartColors.borderSubtle()

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
            Surface(
                shape = CircleShape,
                color = step.color.copy(alpha = 0.15f),
                border = BorderStroke(2.dp, step.color),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        step.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = step.color
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

        // ── Right: Step content ──
        Column(modifier = Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 4.dp)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, SmartColors.borderSubtle())
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Step index badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = step.color.copy(alpha = 0.1f)
                        ) {
                            Text(
                                "#${step.index}",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = step.color
                            )
                        }

                        // Summary
                        Text(
                            step.summary,
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Type pill
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = step.color.copy(alpha = 0.1f)
                        ) {
                            Text(
                                StepTypeLabels[step.type] ?: step.type,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = step.color
                            )
                        }
                    }
                }
            }
        }
    }
}
