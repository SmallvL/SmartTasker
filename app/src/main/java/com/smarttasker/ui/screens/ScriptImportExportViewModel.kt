package com.smarttasker.ui.screens

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarttasker.model.ScriptEntity
import com.smarttasker.model.ScriptStepEntity
import com.smarttasker.model.StepOperation
import com.smarttasker.util.ScriptImportExport
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ScriptImportExportUiState(
    val message: String? = null,
    val isSuccess: Boolean = true,
    val isLoading: Boolean = false
)

@HiltViewModel
class ScriptImportExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ScriptImportExportUiState())
    val uiState: StateFlow<ScriptImportExportUiState> = _uiState.asStateFlow()
    
    // 当前脚本（模拟数据）
    private val currentScript = ScriptEntity(
        id = "current_script",
        name = "当前脚本",
        description = "这是当前正在编辑的脚本",
        category = "工具",
        isAiGenerated = false
    )
    
    private val currentSteps = listOf(
        ScriptStepEntity(
            id = "step_1",
            scriptId = "current_script",
            stepIndex = 0,
            operation = StepOperation.LAUNCH_APP,
            params = """{"packageName": "com.example.app"}""",
            description = "启动应用"
        ),
        ScriptStepEntity(
            id = "step_2",
            scriptId = "current_script",
            stepIndex = 1,
            operation = StepOperation.TAP,
            params = """{"x": 540, "y": 1200}""",
            description = "点击按钮"
        )
    )
    
    /**
     * 导入脚本
     */
    fun importScript(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                val importData = ScriptImportExport.importFromUri(context, uri)
                
                if (importData == null) {
                    _uiState.update { 
                        it.copy(
                            message = "导入失败：无法解析文件",
                            isSuccess = false,
                            isLoading = false
                        )
                    }
                    return@launch
                }
                
                if (!ScriptImportExport.validateImportData(importData)) {
                    _uiState.update { 
                        it.copy(
                            message = "导入失败：文件格式无效",
                            isSuccess = false,
                            isLoading = false
                        )
                    }
                    return@launch
                }
                
                // TODO: 保存到数据库
                // 1. 创建 ScriptEntity
                // 2. 创建 ScriptStepEntity 列表
                // 3. 保存到 Room 数据库
                
                _uiState.update { 
                    it.copy(
                        message = "导入成功：${importData.script.name}",
                        isSuccess = true,
                        isLoading = false
                    )
                }
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        message = "导入失败：${e.message}",
                        isSuccess = false,
                        isLoading = false
                    )
                }
            }
        }
    }
    
    /**
     * 导出脚本
     */
    fun exportScript() {
        viewModelScope.launch {
            try {
                val json = ScriptImportExport.exportToJson(currentScript, currentSteps)
                val intent = ScriptImportExport.createShareIntent(json, currentScript.name)
                
                // TODO: 启动分享 Intent
                
                _uiState.update { 
                    it.copy(
                        message = "导出成功，请选择分享方式",
                        isSuccess = true
                    )
                }
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        message = "导出失败：${e.message}",
                        isSuccess = false
                    )
                }
            }
        }
    }
    
    /**
     * 导出到文件
     */
    fun exportToFile() {
        viewModelScope.launch {
            try {
                val json = ScriptImportExport.exportToJson(currentScript, currentSteps)
                
                // 创建文件名
                val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                val fileName = "smarttasker_script_${dateFormat.format(Date())}.json"
                
                // 保存到 Downloads 目录
                val downloadsDir = File(context.getExternalFilesDir(null), "scripts")
                downloadsDir.mkdirs()
                
                val file = File(downloadsDir, fileName)
                file.writeText(json)
                
                _uiState.update { 
                    it.copy(
                        message = "导出成功：${file.absolutePath}",
                        isSuccess = true
                    )
                }
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        message = "导出失败：${e.message}",
                        isSuccess = false
                    )
                }
            }
        }
    }
    
    /**
     * 下载示例脚本
     */
    fun downloadSample() {
        viewModelScope.launch {
            try {
                val json = ScriptImportExport.generateSampleJson()
                
                // 创建文件名
                val fileName = "sample_script.json"
                
                // 保存到 Downloads 目录
                val downloadsDir = File(context.getExternalFilesDir(null), "scripts")
                downloadsDir.mkdirs()
                
                val file = File(downloadsDir, fileName)
                file.writeText(json)
                
                _uiState.update { 
                    it.copy(
                        message = "示例脚本已保存：${file.absolutePath}",
                        isSuccess = true
                    )
                }
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        message = "下载失败：${e.message}",
                        isSuccess = false
                    )
                }
            }
        }
    }
    
    /**
     * 清除消息
     */
    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}