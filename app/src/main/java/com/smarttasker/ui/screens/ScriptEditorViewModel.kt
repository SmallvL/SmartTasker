package com.smarttasker.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarttasker.core.repository.ScriptRepository
import com.smarttasker.model.StepOperation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScriptEditorUiState(
    val name: String = "",
    val description: String = "",
    val category: String = "其他",
    val steps: List<StepItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ScriptEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val scriptRepository: ScriptRepository
) : ViewModel() {
    
    private val scriptId: String? = savedStateHandle.get<String>("scriptId")
    
    private val _uiState = MutableStateFlow(ScriptEditorUiState())
    val uiState: StateFlow<ScriptEditorUiState> = _uiState.asStateFlow()
    
    init {
        if (scriptId != null && scriptId != "new") {
            loadScript(scriptId)
        }
    }
    
    /**
     * 加载脚本数据
     */
    private fun loadScript(scriptId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                val script = scriptRepository.getScriptById(scriptId)
                
                if (script != null) {
                    // 加载脚本步骤
                    scriptRepository.getScriptSteps(scriptId).collect { steps ->
                        val stepItems = steps.map { step ->
                            StepItem(
                                operation = step.operation,
                                description = step.description,
                                params = step.params
                            )
                        }
                        
                        _uiState.update { 
                            it.copy(
                                name = script.name,
                                description = script.description,
                                category = script.category,
                                steps = stepItems,
                                isLoading = false
                            )
                        }
                    }
                } else {
                    _uiState.update { 
                        it.copy(
                            error = "脚本不存在",
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
     * 保存脚本
     */
    fun saveScript(
        name: String,
        description: String,
        category: String
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                // 验证输入
                if (name.isBlank()) {
                    _uiState.update { 
                        it.copy(
                            error = "脚本名称不能为空",
                            isLoading = false
                        )
                    }
                    return@launch
                }
                
                if (scriptId != null && scriptId != "new") {
                    // 更新现有脚本
                    val existingScript = scriptRepository.getScriptById(scriptId)
                    if (existingScript != null) {
                        val updatedScript = existingScript.copy(
                            name = name,
                            description = description,
                            category = category,
                            updatedAt = System.currentTimeMillis()
                        )
                        scriptRepository.updateScript(updatedScript)
                    }
                } else {
                    // 创建新脚本
                    scriptRepository.createScript(
                        name = name,
                        description = description,
                        category = category
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
     * 添加步骤
     */
    fun addStep() {
        _uiState.update { state ->
            state.copy(
                steps = state.steps + StepItem(
                    operation = StepOperation.TAP,
                    description = ""
                )
            )
        }
    }
    
    /**
     * 删除步骤
     */
    fun deleteStep(index: Int) {
        _uiState.update { state ->
            state.copy(
                steps = state.steps.toMutableList().apply {
                    removeAt(index)
                }
            )
        }
    }
    
    /**
     * 更新步骤
     */
    fun updateStep(index: Int, step: StepItem) {
        _uiState.update { state ->
            state.copy(
                steps = state.steps.toMutableList().apply {
                    set(index, step)
                }
            )
        }
    }
    
    /**
     * 清除错误
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}