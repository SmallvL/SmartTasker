package com.smarttasker.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarttasker.model.StepOperation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StepEditorUiState(
    val operation: StepOperation = StepOperation.TAP,
    val description: String = "",
    val params: String = "",
    val semanticNote: String = "",
    val expected: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class StepEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val scriptId: String = savedStateHandle.get<String>("scriptId") ?: ""
    private val stepIndex: Int = savedStateHandle.get<String>("stepIndex")?.toIntOrNull() ?: 0
    
    private val _uiState = MutableStateFlow(StepEditorUiState())
    val uiState: StateFlow<StepEditorUiState> = _uiState.asStateFlow()
    
    init {
        loadStep()
    }
    
    /**
     * 加载步骤数据
     */
    private fun loadStep() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                // TODO: 从数据库加载步骤
                // 模拟数据
                val step = StepEditorUiState(
                    operation = StepOperation.TAP,
                    description = "点击登录按钮",
                    params = "540, 1200",
                    semanticNote = "点击屏幕中央的登录按钮",
                    expected = "进入登录页面"
                )
                
                _uiState.update { step.copy(isLoading = false) }
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
     * 保存步骤
     */
    fun saveStep(
        operation: StepOperation,
        description: String,
        params: String,
        semanticNote: String,
        expected: String
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                // 验证输入
                if (description.isBlank()) {
                    _uiState.update { 
                        it.copy(
                            error = "步骤描述不能为空",
                            isLoading = false
                        )
                    }
                    return@launch
                }
                
                // TODO: 保存到数据库
                // 1. 创建或更新 ScriptStepEntity
                // 2. 保存到 Room 数据库
                
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