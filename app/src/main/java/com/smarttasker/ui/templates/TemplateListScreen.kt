package com.smarttasker.ui.templates

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.data.entity.TemplateEntity
import com.smarttasker.data.repository.RouteRepository
import com.smarttasker.data.repository.TemplateRepository
import com.smarttasker.ui.common.EmptyState
import com.smarttasker.ui.common.SmartCard
import com.smarttasker.ui.theme.SmartColors

// ── Template data models ──

data class TemplateInfo(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val category: String,
    val stepCount: Int,
    val usageCount: Int,
    val successRate: Float = 0f,
    val version: String = "1.0.0"
)

enum class TemplateCategory(val label: String, val color: Color) {
    ALL("全部", Color.Unspecified),
    SOCIAL("社交", Color(0xFF3B82F6)),
    SHOPPING("购物", Color(0xFFF97316)),
    TOOLS("工具", Color(0xFF8B5CF6)),
    OFFICE("办公", Color(0xFF06B6D4)),
    GENERAL("通用", Color.Unspecified)
}

// ── Convert TemplateEntity to TemplateInfo ──

private fun TemplateEntity.toTemplateInfo(): TemplateInfo = TemplateInfo(
    id = templateId,
    name = name,
    description = description,
    icon = icon,
    category = category,
    stepCount = stepCount,
    usageCount = usageCount,
    successRate = successRate,
    version = version
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateListScreen(
    templateRepo: TemplateRepository,
    routeRepo: RouteRepository,
    onTemplateClick: (String) -> Unit,
    onCreateFromTemplate: (String) -> Unit,
    onImportTemplates: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(TemplateCategory.ALL) }
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedTemplates = remember { mutableStateListOf<String>() }

    // Load templates from database
    val allTemplates by templateRepo.getAllTemplates()
        .collectAsState(initial = emptyList())

    // Filter templates
    val filteredTemplates = remember(allTemplates, searchQuery, selectedCategory) {
        allTemplates.map { it.toTemplateInfo() }.filter { template ->
            (selectedCategory == TemplateCategory.ALL || template.category == selectedCategory.label) &&
            (searchQuery.isEmpty() || template.name.contains(searchQuery, true) || template.description.contains(searchQuery, true))
        }
    }

    Scaffold(
        floatingActionButton = {
            if (!isMultiSelectMode) {
                FloatingActionButton(
                    onClick = onImportTemplates,
                    shape = RoundedCornerShape(16.dp),
                    containerColor = SmartColors.accent(),
                    contentColor = Color.White
                ) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = "导入模板")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── Top App Bar ──
            TopAppBar(
                title = {
                    if (isMultiSelectMode) {
                        Text(
                            "已选择 ${selectedTemplates.size} 个",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )
                    } else {
                        Text(
                            "模板库",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 22.sp
                        )
                    }
                },
                navigationIcon = {
                    if (isMultiSelectMode) {
                        TextButton(onClick = {
                            isMultiSelectMode = false
                            selectedTemplates.clear()
                        }) {
                            Text("取消", color = SmartColors.accent())
                        }
                    }
                },
                actions = {
                    if (isMultiSelectMode) {
                        TextButton(
                            onClick = { /* batch export */ },
                            enabled = selectedTemplates.isNotEmpty()
                        ) {
                            Icon(Icons.Outlined.FileUpload, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("导出", color = SmartColors.accent())
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )

            // ── Search Bar ──
            if (!isMultiSelectMode) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    placeholder = {
                        Text("搜索模板...", color = SmartColors.textTertiary())
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            tint = SmartColors.textTertiary()
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = "清除",
                                    tint = SmartColors.textTertiary(),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SmartColors.accent(),
                        unfocusedBorderColor = SmartColors.borderSubtle(),
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )

                Spacer(Modifier.height(12.dp))

                // ── Category Filter Pills ──
                ScrollableTabRow(
                    selectedTabIndex = TemplateCategory.values().indexOf(selectedCategory),
                    containerColor = Color.Transparent,
                    edgePadding = 20.dp,
                    divider = {},
                    indicator = {}
                ) {
                    TemplateCategory.values().forEach { category ->
                        val isSelected = selectedCategory == category
                        val resolvedColor = if (category.color == Color.Unspecified) SmartColors.accent() else category.color
                        val bgColor by animateColorAsState(
                            targetValue = if (isSelected) resolvedColor else resolvedColor.copy(alpha = 0.08f),
                            animationSpec = tween(200), label = "catBg"
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) Color.White else SmartColors.textSecondary(),
                            animationSpec = tween(200), label = "catText"
                        )

                        Tab(
                            selected = isSelected,
                            onClick = { selectedCategory = category },
                            text = {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = bgColor
                                ) {
                                    Text(
                                        text = category.label,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                        color = textColor,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                }
                            }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
            }

            // ── Template Grid ──
            if (filteredTemplates.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Inventory2,
                    title = "没有找到模板",
                    subtitle = if (searchQuery.isNotEmpty()) "试试其他关键词" else "该分类下暂无模板"
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredTemplates, key = { it.id }) { template ->
                        TemplateCard(
                            template = template,
                            isMultiSelectMode = isMultiSelectMode,
                            isSelected = template.id in selectedTemplates,
                            onClick = {
                                if (isMultiSelectMode) {
                                    if (template.id in selectedTemplates) {
                                        selectedTemplates.remove(template.id)
                                    } else {
                                        selectedTemplates.add(template.id)
                                    }
                                } else {
                                    onTemplateClick(template.id)
                                }
                            },
                            onLongClick = {
                                if (!isMultiSelectMode) {
                                    isMultiSelectMode = true
                                    selectedTemplates.add(template.id)
                                }
                            },
                            onUseClick = { onCreateFromTemplate(template.id) }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

// ── Template Card ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateCard(
    template: TemplateInfo,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onUseClick: () -> Unit
) {
    val categoryColor = TemplateCategory.values().find { it.label == template.category }?.color
        ?.let { if (it == Color.Unspecified) SmartColors.accent() else it }
        ?: SmartColors.accent()

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) SmartColors.accent() else SmartColors.borderSubtle(),
        animationSpec = tween(200), label = "cardBorder"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isMultiSelectMode) {
                    Modifier.border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(20.dp)
                    )
                } else Modifier
            ),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // ── Top: Icon + Select indicator ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Icon in colored circle
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(categoryColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        template.icon,
                        fontSize = 22.sp
                    )
                }

                // Multi-select checkbox or category tag
                if (isMultiSelectMode) {
                    if (isSelected) {
                        Surface(
                            shape = CircleShape,
                            color = SmartColors.accent(),
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = Color.Transparent,
                            modifier = Modifier.size(24.dp),
                            border = BorderStroke(2.dp, SmartColors.borderSubtle())
                        ) {}
                    }
                } else {
                    // Category tag
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
            }

            Spacer(Modifier.height(12.dp))

            // ── Name ──
            Text(
                template.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(4.dp))

            // ── Description ──
            Text(
                template.description,
                fontSize = 12.sp,
                color = SmartColors.textSecondary(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )

            Spacer(Modifier.height(12.dp))

            // ── Stats row ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Step count badge
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
                        Text(
                            "${template.stepCount}步",
                            fontSize = 10.sp,
                            color = SmartColors.textTertiary()
                        )
                    }
                }

                // Usage count
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
                        Text(
                            "${template.usageCount}",
                            fontSize = 10.sp,
                            color = SmartColors.textTertiary()
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Use button ──
            if (!isMultiSelectMode) {
                Button(
                    onClick = onUseClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SmartColors.accent(),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text(
                        "使用",
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
