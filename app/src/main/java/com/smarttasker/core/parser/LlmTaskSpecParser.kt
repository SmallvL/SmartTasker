package com.smarttasker.core.parser

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * LLM-powered TaskSpec parser - calls OpenAI-compatible endpoint.
 * Falls back to rule-based parser on failure.
 */
class LlmTaskSpecParser(
    private val apiUrl: String,
    private val apiKey: String,
    private val model: String = "gpt-4o-mini"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        private val SYSTEM_PROMPT = """
你是一个安卓自动化任务解析器。用户会用自然语言描述一个手机操作任务，你需要将其解析为结构化的 JSON 格式。

输出必须是合法的 JSON，包含以下字段：
{
    "name": "任务简短名称",
    "description": "任务描述",
    "target_app": {
        "name": "应用名称",
        "package": "Android包名",
        "confidence": 0.95
    },
    "trigger": {
        "type": "manual|schedule|notification",
        "time": "HH:MM (如果是定时任务)",
        "repeat": "once|daily|weekly"
    },
    "risk": {
        "level": "low|medium|high|critical",
        "requires_confirmation": false,
        "reason": "风险原因"
    },
    "playbook": "用户原始输入，用于AI首次学习"
}

风险等级规则：
- low: 普通查看、打开应用、签到等无副作用操作
- medium: 涉及确认、同意等可能有后果的操作
- high: 发送消息、删除内容、提交订单等不可逆操作
- critical: 转账、支付、贷款等金融操作（禁止自动执行）

只输出 JSON，不要输出其他内容。
""".trimIndent()
    }

    fun parse(input: String, installedApps: List<Pair<String, String>> = emptyList()): TaskSpecParser.ParseResult {
        try {
            val appListStr = if (installedApps.isNotEmpty()) {
                "\n\n本机已安装的应用列表（包名格式）：\n" +
                    installedApps.joinToString("\n") { "${it.first} -> ${it.second}" }
            } else ""

            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT + appListStr)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", input)
                })
            }

            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("temperature", 0.1)
                put("response_format", JSONObject().apply { put("type", "json_object") })
            }

            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return TaskSpecParser.ParseResult.Error("空响应")

            if (!response.isSuccessful) {
                return TaskSpecParser.ParseResult.Error("API 错误: ${response.code}")
            }

            val json = JSONObject(responseBody)
            val content = json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")

            return parseJsonResponse(content, input)
        } catch (e: Exception) {
            // Fall back to rule-based parser
            return TaskSpecParser.parse(input)
        }
    }

    private fun parseJsonResponse(jsonStr: String, originalInput: String): TaskSpecParser.ParseResult {
        try {
            val json = JSONObject(jsonStr)

            // Check forbidden
            val riskLevel = json.optJSONObject("risk")?.optString("level", "low") ?: "low"
            if (riskLevel == "critical") {
                return TaskSpecParser.ParseResult.Forbidden(
                    json.optJSONObject("risk")?.optString("reason", "包含禁止操作") ?: "包含禁止操作"
                )
            }

            val appJson = json.optJSONObject("target_app")
            val app = if (appJson != null && appJson.optString("name").isNotEmpty()) {
                TaskSpec.AppInfo(
                    name = appJson.optString("name"),
                    packageName = appJson.optString("package"),
                    confidence = appJson.optDouble("confidence", 0.8).toFloat()
                )
            } else null

            val triggerJson = json.optJSONObject("trigger") ?: JSONObject()
            val trigger = TaskSpec.TriggerConfig(
                type = triggerJson.optString("type", "manual"),
                time = triggerJson.optString("time", ""),
                repeat = triggerJson.optString("repeat", "once")
            )

            val riskJson = json.optJSONObject("risk") ?: JSONObject()
            val risk = TaskSpec.RiskConfig(
                level = riskLevel,
                requiresConfirmation = riskJson.optBoolean("requires_confirmation", riskLevel != "low"),
                reason = riskJson.optString("reason", "")
            )

            val spec = TaskSpec(
                taskId = UUID.randomUUID().toString().take(8),
                name = json.optString("name", originalInput.take(20)),
                description = json.optString("description", originalInput),
                targetApp = app,
                trigger = trigger,
                execution = TaskSpec.ExecutionConfig(),
                risk = risk,
                playbook = json.optString("playbook", originalInput)
            )

            return TaskSpecParser.ParseResult.Success(spec)
        } catch (e: Exception) {
            return TaskSpecParser.ParseResult.Error("JSON 解析失败: ${e.message}")
        }
    }
}
