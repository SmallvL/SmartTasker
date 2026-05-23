package com.smarttasker.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarttasker.core.repository.TaskRepository
import com.smarttasker.model.TaskType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TaskEditorUiState(
    val name: String = "",
    val description: String = "",
    val type: TaskType = TaskType.SINGLE,
    val isEnabled: Boolean = true,
    val cronExpression: String = "",
    val intervalMinutes: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class TaskEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskRepository: TaskRepository
) : ViewModel() {
    
    private val taskId: String? = savedStateHandle.get<String>("taskId")
    
    private val _uiState = MutableStateFlow(TaskEditorUiState())
    val uiState: StateFlow<TaskEditorUiState> = _uiState.asStateFlow()
    
    init {
        if (taskId != null) {
            loadTask(taskId)
        }
    }
    
    /**
     * 加载任务数据
     */
    private fun loadTask(taskId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                val task = taskRepository.getTaskById(taskId)
                
                if (task != null) {
                    _uiState.update { 
                        it.copy(
                            name = task.name,
                            description = task.description,
                            type = task.type,
                            isEnabled = task.isEnabled,
                            cronExpression = task.cronExpression ?: "",
                            intervalMinutes = task.intervalMinutes?.toString() ?: "",
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update { 
                        it.copy(
                            error = "任务不存在",
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        error = e.message,
                        isLoading = false
                    )
                }
            }
        }
    }
    
    /**
     * 保存任务
     */
    fun saveTask(
        name: String,
        description: String,
        type: TaskType,
        isEnabled: Boolean,
        cronExpression: String,
        intervalMinutes: String
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                // 验证输入
                if (name.isBlank()) {
                    _uiState.update { 
                        it.copy(
                            error = "任务名称不能为空",
                            isLoading = false
                        )
                    }
                    return@launch
                }
                
                if (taskId != null) {
                    // 更新现有任务
                    val existingTask = taskRepository.getTaskById(taskId)
                    if (existingTask != null) {
                        val updatedTask = existingTask.copy(
                            name = name,
                            description = description,
                            type = type,
                            isEnabled = isEnabled,
                            cronExpression = cronExpression.ifBlank { null },
                            intervalMinutes = intervalMinutes.toLongOrNull(),
                            updatedAt = System.currentTimeMillis()
                        )
                        taskRepository.updateTask(updatedTask)
                    }
                } else {
                    // 创建新任务
                    taskRepository.createTask(
                        name = name,
                        description = description,
                        type = type,
                        isEnabled = isEnabled,
                        cronExpression = cronExpression.ifBlank { null },
                        intervalMinutes = intervalMinutes.toLongOrNull()
                    )
                }
                
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        error = e.message,
                        isLoading = false
                    )
                }
            }
        }
    }
    
    /**
     * 清除错误
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}