package com.smarttasker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smarttasker.model.StepOperation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepEditorScreen(
    scriptId: String,
    stepIndex: Int,
    onNavigateBack: () -> Unit,
    viewModel: StepEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    var selectedOperation by remember { mutableStateOf(uiState.operation) }
    var description by remember { mutableStateOf(uiState.description) }
    var params by remember { mutableStateOf(uiState.params) }
    var semanticNote by remember { mutableStateOf(uiState.semanticNote) }
    var expected by remember { mutableStateOf(uiState.expected) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "编辑步骤 ${stepIndex + 1}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
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
                    IconButton(
                        onClick = {
                            viewModel.saveStep(
                                operation = selectedOperation,
                                description = description,
                                params = params,
                                semanticNote = semanticNote,
                                expected = expected
                            )
                            onNavigateBack()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "保存"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 操作类型选择
            OperationTypeSelector(
                selectedOperation = selectedOperation,
                onOperationSelected = { selectedOperation = it }
            )
            
            // 步骤描述
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("步骤描述") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                placeholder = { Text("描述这个步骤要做什么") }
            )
            
            // 参数配置
            Text(
                text = "参数配置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            // 根据操作类型显示不同的参数输入
            when (selectedOperation) {
                StepOperation.TAP, StepOperation.LONG_PRESS -> {
                    OutlinedTextField(
                        value = params,
                        onValueChange = { params = it },
                        label = { Text("坐标 (x, y)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("例如: 540, 1200") }
                    )
                }
                StepOperation.INPUT -> {
                    OutlinedTextField(
                        value = params,
                        onValueChange = { params = it },
                        label = { Text("输入内容") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 5,
                        placeholder = { Text("要输入的文本内容") }
                    )
                }
                StepOperation.SWIPE -> {
                    OutlinedTextField(
                        value = params,
                        onValueChange = { params = it },
                        label = { Text("滑动坐标 (x1, y1, x2, y2)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("例如: 540, 1800, 540, 600") }
                    )
                }
                StepOperation.WAIT -> {
                    OutlinedTextField(
                        value = params,
                        onValueChange = { params = it },
                        label = { Text("等待时间 (毫秒)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("例如: 1000") }
                    )
                }
                StepOperation.LAUNCH_APP -> {
                    OutlinedTextField(
                        value = params,
                        onValueChange = { params = it },
                        label = { Text("应用包名") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("例如: com.tencent.mm") }
                    )
                }
                StepOperation.WAIT_FOR_ELEMENT -> {
                    OutlinedTextField(
                        value = params,
                        onValueChange = { params = it },
                        label = { Text("元素标识") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("例如: text=登录") }
                    )
                }
                StepOperation.VERIFY -> {
                    OutlinedTextField(
                        value = params,
                        onValueChange = { params = it },
                        label = { Text("验证条件") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        placeholder = { Text("例如: text=登录成功") }
                    )
                }
                StepOperation.SCROLL -> {
                    OutlinedTextField(
                        value = params,
                        onValueChange = { params = it },
                        label = { Text("滚动方向和距离") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("例如: down, 500") }
                    )
                }
                StepOperation.COMMAND -> {
                    OutlinedTextField(
                        value = params,
                        onValueChange = { params = it },
                        label = { Text("自定义命令") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        placeholder = { Text("输入自定义命令") }
                    )
                }
                else -> {
                    OutlinedTextField(
                        value = params,
                        onValueChange = { params = it },
                        label = { Text("参数") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        placeholder = { Text("输入参数") }
                    )
                }
            }
            
            // 语义说明
            OutlinedTextField(
                value = semanticNote,
                onValueChange = { semanticNote = it },
                label = { Text("语义说明 (可选)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                placeholder = { Text("AI 可理解的语义描述") }
            )
            
            // 预期结果
            OutlinedTextField(
                value = expected,
                onValueChange = { expected = it },
                label = { Text("预期结果 (可选)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                placeholder = { Text("执行此步骤后期望看到的结果") }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 保存按钮
            Button(
                onClick = {
                    viewModel.saveStep(
                        operation = selectedOperation,
                        description = description,
                        params = params,
                        semanticNote = semanticNote,
                        expected = expected
                    )
                    onNavigateBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存步骤")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationTypeSelector(
    selectedOperation: StepOperation,
    onOperationSelected: (StepOperation) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column {
        Text(
            text = "操作类型",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selectedOperation.title,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                StepOperation.values().forEach { operation ->
                    DropdownMenuItem(
                        text = { Text(operation.title) },
                        onClick = {
                            onOperationSelected(operation)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}