package com.smarttasker.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarttasker.core.repository.ScriptRepository
import com.smarttasker.core.repository.TaskRepository
import com.smarttasker.model.ScriptItem
import com.smarttasker.model.TaskItem
import com.smarttasker.model.TaskType
import com.smarttasker.model.TaskUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val scriptRepository: ScriptRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(TaskUiState())
    val uiState: StateFlow<TaskUiState> = _uiState.asStateFlow()
    
    init {
        loadTasks()
        loadScripts()
    }
    
    /**
     * 加载任务列表
     */
    private fun loadTasks() {
        viewModelScope.launch {
            taskRepository.getAllTasks().collect { tasks ->
                _uiState.update { it.copy(tasks = tasks) }
            }
        }
    }
    
    /**
     * 加载脚本列表
     */
    private fun loadScripts() {
        viewModelScope.launch {
            scriptRepository.getAllScripts().collect { scripts ->
                _uiState.update { it.copy(scripts = scripts) }
            }
        }
    }
    
    /**
     * 运行任务
     */
    fun runTask(taskId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                // TODO: 实现任务运行逻辑
                // 1. 查找任务
                // 2. 加载关联的脚本
                // 3. 执行脚本
                
                _uiState.update { it.copy(isLoading = false) }
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
     * 删除任务
     */
    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                taskRepository.deleteTask(taskId)
                _uiState.update { it.copy(isLoading = false) }
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
     * 运行脚本
     */
    fun runScript(scriptId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                // TODO: 实现脚本运行逻辑
                // 1. 加载脚本
                // 2. 执行脚本步骤
                
                _uiState.update { it.copy(isLoading = false) }
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
     * 删除脚本
     */
    fun deleteScript(scriptId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                scriptRepository.deleteScript(scriptId)
                _uiState.update { it.copy(isLoading = false) }
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
     * 显示添加任务对话框
     */
    fun showAddTaskDialog() {
        viewModelScope.launch {
            // TODO: 显示添加任务对话框
        }
    }
    
    /**
     * 显示添加脚本对话框
     */
    fun showAddScriptDialog() {
        viewModelScope.launch {
            // TODO: 显示添加脚本对话框
        }
    }
    
    /**
     * 清除错误
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}