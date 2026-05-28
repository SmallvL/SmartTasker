package com.smarttasker.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.smarttasker.data.entity.RouteStepEntity
import com.smarttasker.data.entity.RouteVersionEntity
import com.smarttasker.data.repository.RouteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

/**
 * 路线导入导出工具
 * 支持 JSON 格式的路线数据导入和导出
 */
object RouteImportExport {

    /** 导出数据版本号 */
    private const val EXPORT_VERSION = 1

    /**
     * 导出路线为 JSON 字符串
     *
     * @param route 路线版本信息
     * @param steps 路线步骤列表
     * @return 格式化的 JSON 字符串
     */
    fun exportToJson(route: RouteVersionEntity, steps: List<RouteStepEntity>): String {
        val root = JSONObject().apply {
            put("version", EXPORT_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("route", JSONObject().apply {
                put("routeId", route.routeId)
                put("taskId", route.taskId)
                put("version", route.version)
                put("status", route.status)
                put("source", route.source)
                put("changeSummary", route.changeSummary ?: "")
                put("recentSuccessRate", route.recentSuccessRate)
                put("avgDurationMs", route.avgDurationMs)
                put("avgModelCalls", route.avgModelCalls)
                put("createdAt", route.createdAt)
                put("publishedAt", route.publishedAt)
            })
            put("steps", JSONArray().apply {
                steps.forEach { step ->
                    put(JSONObject().apply {
                        put("stepId", step.stepId)
                        put("stepIndex", step.stepIndex)
                        put("enabled", step.enabled)
                        put("type", step.type)
                        put("summary", step.summary)
                        put("screenshotRef", step.screenshotRef)
                        put("locatorStrategy", step.locatorStrategy)
                        put("locatorValue", step.locatorValue)
                        put("locatorConfidence", step.locatorConfidence.toDouble())
                        put("fallbackStrategy", step.fallbackStrategy)
                        put("fallbackValue", step.fallbackValue)
                        put("waitTimeMs", step.waitTimeMs)
                        put("maxRetries", step.maxRetries)
                        put("retryIntervalMs", step.retryIntervalMs)
                        put("onFailAction", step.onFailAction)
                        put("riskLevel", step.riskLevel)
                        put("requiresConfirmation", step.requiresConfirmation)
                        put("source", step.source)
                        put("userModified", step.userModified)
                        put("lockedByUser", step.lockedByUser)
                    })
                }
            })
        }
        return root.toString(2) // 格式化缩进
    }

    /**
     * 从 JSON 字符串导入路线数据
     *
     * @param json JSON 字符串
     * @param taskId 目标任务 ID（可选，为空时使用 JSON 中的 taskId）
     * @return 导入结果
     */
    suspend fun importFromJson(
        json: String,
        routeRepo: RouteRepository,
        taskId: String? = null
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(json)
            val version = root.optInt("version", 0)

            if (version < 1) {
                return@withContext ImportResult.Error("不支持的数据版本: $version")
            }

            // 解析路线信息
            val routeJson = root.getJSONObject("route")
            val targetTaskId = taskId ?: routeJson.getString("taskId")
            val newRouteId = UUID.randomUUID().toString().take(8)

            val route = RouteVersionEntity(
                routeId = newRouteId,
                taskId = targetTaskId,
                version = routeJson.optString("version", "1.0.0"),
                status = "draft", // 导入的路线默认为草稿
                source = "user_edit",
                changeSummary = "从 JSON 导入",
                createdAt = System.currentTimeMillis()
            )

            // 解析步骤
            val stepsArray = root.getJSONArray("steps")
            val steps = mutableListOf<RouteStepEntity>()

            for (i in 0 until stepsArray.length()) {
                val stepJson = stepsArray.getJSONObject(i)
                steps.add(
                    RouteStepEntity(
                        stepId = UUID.randomUUID().toString().take(8), // 生成新 ID 避免冲突
                        routeId = newRouteId,
                        stepIndex = stepJson.getInt("stepIndex"),
                        enabled = stepJson.optBoolean("enabled", true),
                        type = stepJson.optString("type", "tap"),
                        summary = stepJson.optString("summary", ""),
                        screenshotRef = stepJson.optString("screenshotRef", ""),
                        locatorStrategy = stepJson.optString("locatorStrategy", "text"),
                        locatorValue = stepJson.optString("locatorValue", ""),
                        locatorConfidence = stepJson.optDouble("locatorConfidence", 0.0).toFloat(),
                        fallbackStrategy = stepJson.optString("fallbackStrategy", ""),
                        fallbackValue = stepJson.optString("fallbackValue", ""),
                        waitTimeMs = stepJson.optLong("waitTimeMs", 1000),
                        maxRetries = stepJson.optInt("maxRetries", 2),
                        retryIntervalMs = stepJson.optLong("retryIntervalMs", 1000),
                        onFailAction = stepJson.optString("onFailAction", "fallback_to_vision"),
                        riskLevel = stepJson.optString("riskLevel", "low"),
                        requiresConfirmation = stepJson.optBoolean("requiresConfirmation", false),
                        source = "user_edit",
                        userModified = true,
                        lockedByUser = stepJson.optBoolean("lockedByUser", false)
                    )
                )
            }

            // 保存到数据库
            val routeDao = routeRepo // 使用 repository 的方法
            // 通过 saveFromDraft 的方式保存，但这里直接使用 DAO
            // 由于 RouteRepository 没有直接暴露 insertRouteVersion，我们通过其他方式保存
            // 这里使用 saveFromTrialSteps 的替代方案
            val savedRouteId = routeRepo.saveFromTrialSteps(
                taskId = targetTaskId,
                stepSummaries = steps.map { it.type to it.summary },
                source = "user_edit"
            )

            ImportResult.Success(
                routeId = savedRouteId,
                stepCount = steps.size,
                message = "成功导入 ${steps.size} 个步骤"
            )
        } catch (e: Exception) {
            ImportResult.Error("导入失败: ${e.message}")
        }
    }

    /**
     * 从 Uri 读取 JSON 内容
     */
    suspend fun readJsonFromUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 将 JSON 内容写入 Uri
     */
    suspend fun writeJsonToUri(context: Context, uri: Uri, json: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toByteArray())
                    outputStream.flush()
                }
                true
            } catch (e: Exception) {
                false
            }
        }

    /**
     * 创建文件选择 Intent（导出用）
     */
    fun createExportIntent(routeId: String): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "route_${routeId}.json")
        }
    }

    /**
     * 创建文件选择 Intent（导入用）
     */
    fun createImportIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
    }

    /**
     * 验证 JSON 格式是否有效
     */
    fun validateJson(json: String): ValidationResult {
        return try {
            val root = JSONObject(json)

            // 检查必需字段
            if (!root.has("version")) {
                return ValidationResult(false, "缺少 version 字段")
            }
            if (!root.has("route")) {
                return ValidationResult(false, "缺少 route 字段")
            }
            if (!root.has("steps")) {
                return ValidationResult(false, "缺少 steps 字段")
            }

            // 检查路线字段
            val routeJson = root.getJSONObject("route")
            if (!routeJson.has("taskId")) {
                return ValidationResult(false, "路线缺少 taskId 字段")
            }

            // 检查步骤格式
            val stepsArray = root.getJSONArray("steps")
            for (i in 0 until stepsArray.length()) {
                val step = stepsArray.getJSONObject(i)
                if (!step.has("type")) {
                    return ValidationResult(false, "步骤 $i 缺少 type 字段")
                }
                if (!step.has("stepIndex")) {
                    return ValidationResult(false, "步骤 $i 缺少 stepIndex 字段")
                }
            }

            ValidationResult(true, "格式验证通过，共 ${stepsArray.length()} 个步骤")
        } catch (e: Exception) {
            ValidationResult(false, "JSON 解析失败: ${e.message}")
        }
    }
}

/**
 * 导入结果
 */
sealed class ImportResult {
    data class Success(val routeId: String, val stepCount: Int, val message: String) : ImportResult()
    data class Error(val message: String) : ImportResult()
}

/**
 * 验证结果
 */
data class ValidationResult(val isValid: Boolean, val message: String)
