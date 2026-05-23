package com.smarttasker.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarttasker.model.ExecutionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class TaskTestUiState(
    val taskName: String = "测试任务",
    val status: ExecutionStatus = ExecutionStatus.SUCCESS,
    val progress: Float = 0f,
    val currentStep: Int = 0,
    val totalSteps: Int = 0,
    val logs: List<LogEntry> = emptyList(),
    val result: String? = null,
    val error: String? = null,
    val executionTime: Long = 0,
    val isLoading: Boolean = false
)

@HiltViewModel
class TaskTestViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val taskId: String = savedStateHandle.get<String>("taskId") ?: ""
    
    private val _uiState = MutableStateFlow(TaskTestUiState())
    val uiState: StateFlow<TaskTestUiState> = _uiState.asStateFlow()
    
    private var executionJob: Job? = null
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    init {
        loadTaskInfo()
    }
    
    /**
     * 加载任务信息
     */
    private fun loadTaskInfo() {
        viewModelScope.launch {
            // TODO: 从数据库加载任务信息
            // 模拟数据
            _uiState.update { 
                it.copy(
                    taskName = "每日签到任务",
                    totalSteps = 5
                )
            }
        }
    }
    
    /**
     * 运行任务
     */
    fun runTask() {
        executionJob?.cancel()
        executionJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            
            _uiState.update { 
                it.copy(
                    status = ExecutionStatus.RUNNING,
                    progress = 0f,
                    currentStep = 0,
                    logs = emptyList(),
                    result = null,
                    error = null,
                    executionTime = 0
                )
            }
            
            addLog(LogLevel.INFO, "开始执行任务...")
            
            try {
                // 模拟执行步骤
                val steps = listOf(
                    "启动目标应用" to 1000L,
                    "等待应用加载" to 1500L,
                    "查找签到按钮" to 800L,
                    "点击签到按钮" to 500L,
                    "验证签到结果" to 1200L
                )
                
                steps.forEachIndexed { index, (stepName, delay) ->
                    _uiState.update { 
                        it.copy(
                            currentStep = index + 1,
                            progress = (index + 1).toFloat() / steps.size
                        )
                    }
                    
                    addLog(LogLevel.INFO, "步骤 ${index + 1}: $stepName")
                    delay(delay)
                    
                    // 模拟随机失败
                    if (index == 2 && Math.random() < 0.3) {
                        addLog(LogLevel.WARNING, "查找元素超时，重试中...")
                        delay(500)
                    }
                    
                    addLog(LogLevel.INFO, "步骤 ${index + 1} 完成")
                }
                
                val endTime = System.currentTimeMillis()
                
                _uiState.update { 
                    it.copy(
                        status = ExecutionStatus.SUCCESS,
                        progress = 1f,
                        result = "签到成功！获得 10 积分",
                        executionTime = endTime - startTime
                    )
                }
                
                addLog(LogLevel.INFO, "任务执行成功！")
                
            } catch (e: Exception) {
                val endTime = System.currentTimeMillis()
                
                _uiState.update { 
                    it.copy(
                        status = ExecutionStatus.FAILED,
                        error = e.message ?: "未知错误",
                        executionTime = endTime - startTime
                    )
                }
                
                addLog(LogLevel.ERROR, "任务执行失败: ${e.message}")
            }
        }
    }
    
    /**
     * 停止任务
     */
    fun stopTask() {
        executionJob?.cancel()
        
        _uiState.update { 
            it.copy(
                status = ExecutionStatus.CANCELLED
            )
        }
        
        addLog(LogLevel.WARNING, "任务已取消")
    }
    
    /**
     * 重置测试
     */
    fun resetTest() {
        executionJob?.cancel()
        
        _uiState.update { 
            TaskTestUiState(
                taskName = it.taskName,
                totalSteps = it.totalSteps
            )
        }
    }
    
    /**
     * 添加日志
     */
    private fun addLog(level: LogLevel, message: String) {
        val timestamp = dateFormat.format(Date())
        
        _uiState.update { state ->
            state.copy(
                logs = state.logs + LogEntry(
                    timestamp = timestamp,
                    level = level,
                    message = message
                )
            )
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        executionJob?.cancel()
    }
}