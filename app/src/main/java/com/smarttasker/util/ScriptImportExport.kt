package com.smarttasker.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.smarttasker.model.ScriptEntity
import com.smarttasker.model.ScriptStepEntity
import com.smarttasker.model.StepOperation
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 脚本导入导出工具
 */
object ScriptImportExport {
    
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()
    
    /**
     * 导出脚本数据
     */
    data class ScriptExportData(
        val version: Int = 1,
        val script: ScriptEntity,
        val steps: List<ScriptStepEntity>
    )
    
    /**
     * 导出脚本为 JSON 字符串
     */
    fun exportToJson(script: ScriptEntity, steps: List<ScriptStepEntity>): String {
        val exportData = ScriptExportData(
            script = script,
            steps = steps.sortedBy { it.stepIndex }
        )
        return gson.toJson(exportData)
    }
    
    /**
     * 从 JSON 字符串导入脚本
     */
    fun importFromJson(json: String): ScriptExportData? {
        return try {
            val type = object : TypeToken<ScriptExportData>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 从 Uri 导入脚本
     */
    fun importFromUri(context: Context, uri: Uri): ScriptExportData? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val json = reader.readText()
            reader.close()
            importFromJson(json)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 创建分享 Intent
     */
    fun createShareIntent(json: String, scriptName: String): Intent {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "application/json"
        intent.putExtra(Intent.EXTRA_TEXT, json)
        intent.putExtra(Intent.EXTRA_SUBJECT, "SmartTasker 脚本: $scriptName")
        return Intent.createChooser(intent, "导出脚本")
    }
    
    /**
     * 验证导入数据
     */
    fun validateImportData(data: ScriptExportData): Boolean {
        // 检查版本
        if (data.version < 1) return false
        
        // 检查脚本基本信息
        if (data.script.name.isBlank()) return false
        
        // 检查步骤
        if (data.steps.isEmpty()) return false
        
        // 检查步骤操作类型
        data.steps.forEach { step ->
            try {
                StepOperation.valueOf(step.operation.name)
            } catch (e: Exception) {
                return false
            }
        }
        
        return true
    }
    
    /**
     * 生成示例脚本 JSON
     */
    fun generateSampleJson(): String {
        val script = ScriptEntity(
            id = "sample_script",
            name = "示例脚本",
            description = "这是一个示例脚本",
            category = "示例",
            isAiGenerated = false
        )
        
        val steps = listOf(
            ScriptStepEntity(
                id = "step_1",
                scriptId = "sample_script",
                stepIndex = 0,
                operation = StepOperation.LAUNCH_APP,
                params = """{"packageName": "com.example.app"}""",
                description = "启动示例应用"
            ),
            ScriptStepEntity(
                id = "step_2",
                scriptId = "sample_script",
                stepIndex = 1,
                operation = StepOperation.TAP,
                params = """{"x": 540, "y": 1200}""",
                description = "点击按钮"
            )
        )
        
        return exportToJson(script, steps)
    }
}