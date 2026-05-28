package com.smarttasker.ui.routeeditor

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smarttasker.data.entity.RouteStepEntity
import com.smarttasker.data.entity.RouteVersionEntity
import com.smarttasker.data.repository.RouteRepository
import com.smarttasker.util.RouteImportExport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 路线编辑器 UI 状态
 */
data class RouteEditorUiState(
    val routeId: String = "",
    val routeName: String = "",
    val steps: List<RouteStepEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val selectedStepIndex: Int = -1,
    val showStepEditDialog: Boolean = false,
    val editingStep: RouteStepEntity? = null
)

/**
 * 路线编辑器 ViewModel
 */
class RouteEditorViewModel(
    application: Application,
    private val routeRepo: RouteRepository,
    private val routeId: String
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(RouteEditorUiState())
    val uiState: StateFlow<RouteEditorUiState> = _uiState.asStateFlow()

    init {
        loadRoute()
    }

    /**
     * 加载路线数据
     */
    private fun loadRoute() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                val route = routeRepo.getRouteById(routeId)
                val steps = routeRepo.getStepsForRouteSync(routeId)
                
                _uiState.value = _uiState.value.copy(
                    routeId = routeId,
                    routeName = route?.changeSummary ?: "未命名路线",
                    steps = steps,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "加载路线失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 添加新步骤
     */
    fun addStep(type: String = "tap", summary: String = "") {
        val currentSteps = _uiState.value.steps.toMutableList()
        val newStep = RouteStepEntity(
            stepId = java.util.UUID.randomUUID().toString().take(8),
            routeId = routeId,
            stepIndex = currentSteps.size,
            type = type,
            summary = summary.ifEmpty { "新步骤 ${currentSteps.size + 1}" }
        )
        currentSteps.add(newStep)
        _uiState.value = _uiState.value.copy(steps = currentSteps)
    }

    /**
     * 删除步骤
     */
    fun deleteStep(stepIndex: Int) {
        val currentSteps = _uiState.value.steps.toMutableList()
        if (stepIndex in currentSteps.indices) {
            currentSteps.removeAt(stepIndex)
            // 更新步骤索引
            val updatedSteps = currentSteps.mapIndexed { index, step ->
                step.copy(stepIndex = index)
            }
            _uiState.value = _uiState.value.copy(steps = updatedSteps)
        }
    }

    /**
     * 切换步骤启用状态
     */
    fun toggleStepEnabled(stepIndex: Int) {
        val currentSteps = _uiState.value.steps.toMutableList()
        if (stepIndex in currentSteps.indices) {
            currentSteps[stepIndex] = currentSteps[stepIndex].copy(
                enabled = !currentSteps[stepIndex].enabled
            )
            _uiState.value = _uiState.value.copy(steps = currentSteps)
        }
    }

    /**
     * 移动步骤位置
     */
    fun moveStep(fromIndex: Int, toIndex: Int) {
        val currentSteps = _uiState.value.steps.toMutableList()
        if (fromIndex in currentSteps.indices && toIndex in currentSteps.indices) {
            val step = currentSteps.removeAt(fromIndex)
            currentSteps.add(toIndex, step)
            // 更新步骤索引
            val updatedSteps = currentSteps.mapIndexed { index, s ->
                s.copy(stepIndex = index)
            }
            _uiState.value = _uiState.value.copy(steps = updatedSteps)
        }
    }

    /**
     * 选择步骤进行编辑
     */
    fun selectStep(stepIndex: Int) {
        val currentSteps = _uiState.value.steps
        if (stepIndex in currentSteps.indices) {
            _uiState.value = _uiState.value.copy(
                selectedStepIndex = stepIndex,
                editingStep = currentSteps[stepIndex],
                showStepEditDialog = true
            )
        }
    }

    /**
     * 更新步骤
     */
    fun updateStep(stepIndex: Int, updatedStep: RouteStepEntity) {
        val currentSteps = _uiState.value.steps.toMutableList()
        if (stepIndex in currentSteps.indices) {
            currentSteps[stepIndex] = updatedStep
            _uiState.value = _uiState.value.copy(
                steps = currentSteps,
                showStepEditDialog = false,
                editingStep = null,
                selectedStepIndex = -1
            )
        }
    }

    /**
     * 关闭步骤编辑对话框
     */
    fun dismissStepEditDialog() {
        _uiState.value = _uiState.value.copy(
            showStepEditDialog = false,
            editingStep = null,
            selectedStepIndex = -1
        )
    }

    /**
     * 保存路线
     */
    fun saveRoute() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            
            try {
                val steps = _uiState.value.steps
                routeRepo.updateRouteSteps(routeId, steps)
                
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = "保存失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 导出路线为 JSON
     */
    fun exportRoute(): String? {
        val steps = _uiState.value.steps
        if (steps.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "没有步骤可导出")
            return null
        }

        return try {
            val route = RouteVersionEntity(
                routeId = routeId,
                taskId = "",
                version = "1.0.0",
                status = "draft",
                source = "user_edit",
                changeSummary = _uiState.value.routeName,
                createdAt = System.currentTimeMillis()
            )
            RouteImportExport.exportToJson(route, steps)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = "导出失败: ${e.message}")
            null
        }
    }

    /**
     * 从 JSON 导入路线
     */
    fun importRoute(json: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                val result = RouteImportExport.importFromJson(json, routeRepo)
                when (result) {
                    is com.smarttasker.util.ImportResult.Success -> {
                        loadRoute() // 重新加载数据
                    }
                    is com.smarttasker.util.ImportResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = result.message
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "导入失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
