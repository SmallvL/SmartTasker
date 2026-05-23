package com.smarttasker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smarttasker.model.ExecutionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskTestScreen(
    taskId: String,
    onNavigateBack: () -> Unit,
    viewModel: TaskTestViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val logListState = rememberLazyListState()
    
    // 自动滚动到底部
    LaunchedEffect(uiState.logs.size) {
        if (uiState.logs.isNotEmpty()) {
            logListState.animateScrollToItem(uiState.logs.size - 1)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "任务测试",
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
        ) {
            // 任务信息卡片
            TaskInfoCard(
                taskName = uiState.taskName,
                status = uiState.status,
                progress = uiState.progress,
                currentStep = uiState.currentStep,
                totalSteps = uiState.totalSteps
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 控制按钮
            ControlButtons(
                isRunning = uiState.status == ExecutionStatus.RUNNING,
                onRun = { viewModel.runTask() },
                onStop = { viewModel.stopTask() },
                onReset = { viewModel.resetTest() }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 执行日志
            Text(
                text = "执行日志",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 日志列表
            LazyColumn(
                state = logListState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(uiState.logs) { log ->
                    LogEntry(log = log)
                }
                
                if (uiState.logs.isEmpty()) {
                    item {
                        Text(
                            text = "等待执行...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 执行结果
            AnimatedVisibility(
                visible = uiState.status == ExecutionStatus.SUCCESS || 
                         uiState.status == ExecutionStatus.FAILED,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                ResultCard(
                    status = uiState.status,
                    result = uiState.result,
                    error = uiState.error,
                    executionTime = uiState.executionTime
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun TaskInfoCard(
    taskName: String,
    status: ExecutionStatus,
    progress: Float,
    currentStep: Int,
    totalSteps: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // 任务名称
            Text(
                text = taskName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 状态行
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 状态指示器
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            when (status) {
                                ExecutionStatus.RUNNING -> Color(0xFF2196F3)
                                ExecutionStatus.SUCCESS -> Color(0xFF4CAF50)
                                ExecutionStatus.FAILED -> Color(0xFFF44336)
                                ExecutionStatus.CANCELLED -> Color(0xFFFF9800)
                                ExecutionStatus.TIMEOUT -> Color(0xFFFF5722)
                            }
                        )
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = when (status) {
                        ExecutionStatus.RUNNING -> "执行中"
                        ExecutionStatus.SUCCESS -> "执行成功"
                        ExecutionStatus.FAILED -> "执行失败"
                        ExecutionStatus.CANCELLED -> "已取消"
                        ExecutionStatus.TIMEOUT -> "执行超时"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                if (totalSteps > 0) {
                    Text(
                        text = "步骤 $currentStep/$totalSteps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 进度条
            if (status == ExecutionStatus.RUNNING) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
fun ControlButtons(
    isRunning: Boolean,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isRunning) {
            // 停止按钮
            Button(
                onClick = onStop,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("停止执行")
            }
        } else {
            // 运行按钮
            Button(
                onClick = onRun,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("开始测试")
            }
            
            // 重置按钮
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("重置")
            }
        }
    }
}

@Composable
fun LogEntry(log: LogEntry) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // 时间戳
        Text(
            text = log.timestamp,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // 日志图标
        Icon(
            imageVector = when (log.level) {
                LogLevel.INFO -> Icons.Default.CheckCircle
                LogLevel.WARNING -> Icons.Default.Warning
                LogLevel.ERROR -> Icons.Default.Close
            },
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = when (log.level) {
                LogLevel.INFO -> Color(0xFF4CAF50)
                LogLevel.WARNING -> Color(0xFFFF9800)
                LogLevel.ERROR -> Color(0xFFF44336)
            }
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // 日志内容
        Text(
            text = log.message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = when (log.level) {
                LogLevel.ERROR -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
fun ResultCard(
    status: ExecutionStatus,
    result: String?,
    error: String?,
    executionTime: Long
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (status == ExecutionStatus.SUCCESS) {
                Color(0xFF4CAF50).copy(alpha = 0.1f)
            } else {
                Color(0xFFF44336).copy(alpha = 0.1f)
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 结果标题
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (status == ExecutionStatus.SUCCESS) {
                        Icons.Default.CheckCircle
                    } else {
                        Icons.Default.Close
                    },
                    contentDescription = null,
                    tint = if (status == ExecutionStatus.SUCCESS) {
                        Color(0xFF4CAF50)
                    } else {
                        Color(0xFFF44336)
                    }
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = if (status == ExecutionStatus.SUCCESS) "执行成功" else "执行失败",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                Text(
                    text = "${executionTime}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 结果内容
            if (result != null && status == ExecutionStatus.SUCCESS) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            // 错误信息
            if (error != null && status == ExecutionStatus.FAILED) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

data class LogEntry(
    val timestamp: String,
    val level: LogLevel,
    val message: String
)

enum class LogLevel {
    INFO,
    WARNING,
    ERROR
}