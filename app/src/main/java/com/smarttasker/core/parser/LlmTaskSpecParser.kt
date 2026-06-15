package com.smarttasker.core.parser

import com.smarttasker.util.DebugLog
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
 * Supports MiMo, DeepSeek, Qwen, and any OpenAI-compatible API.
 * Falls back to rule-based parser on failure.
 */
class LlmTaskSpecParser(
    private val apiUrl: String,
    private val apiKey: String,
    private val model: String = "gpt-4o-mini"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
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

只输出 JSON，不要输出其他内容。不要用markdown代码块包裹。
""".trimIndent()

        /**
         * Normalize the API URL to a full chat completions endpoint.
         * Handles various formats users might enter:
         * - "https://api.openai.com/v1" → "https://api.openai.com/v1/chat/completions"
         * - "https://api.openai.com/v1/" → "https://api.openai.com/v1/chat/completions"
         * - "https://api.openai.com/v1/chat/completions" → as-is
         * - "https://api.mlm.com/v1" → "https://api.mlm.com/v1/chat/completions"
         */
        fun normalizeApiUrl(url: String): String {
            val trimmed = url.trimEnd('/')
            return when {
                trimmed.endsWith("/chat/completions") -> trimmed
                trimmed.endsWith("/v1") -> "$trimmed/chat/completions"
                trimmed.endsWith("/v1/chat") -> "$trimmed/completions"
                trimmed.contains("/v1/") && !trimmed.endsWith("/completions") -> {
                    // e.g. https://api.example.com/v1/something → use as-is
                    trimmed
                }
                else -> "$trimmed/v1/chat/completions"
            }
        }
    }

    /**
     * Parse natural language input into a TaskSpec using LLM.
     * This is a blocking network call — must be called from a background thread.
     */
    fun parse(input: String, installedApps: List<Pair<String, String>> = emptyList()): TaskSpecParser.ParseResult {
        try {
            val normalizedUrl = normalizeApiUrl(apiUrl)
            DebugLog.i("LlmParser", "Calling LLM: model=$model url=$normalizedUrl")

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
                // Some LLMs don't support response_format, so we only add it for known providers
                // that support JSON mode. For others, we'll extract JSON from the response manually.
                if (supportsJsonMode()) {
                    put("response_format", JSONObject().apply { put("type", "json_object") })
                }
            }

            val request = Request.Builder()
                .url(normalizedUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            if (responseBody.isNullOrBlank()) {
                DebugLog.e("LlmParser", "Empty response body, code=${response.code}")
                return TaskSpecParser.ParseResult.Error("API 返回空响应 (HTTP ${response.code})")
            }

            if (!response.isSuccessful) {
                DebugLog.e("LlmParser", "API error: HTTP ${response.code} body=${responseBody.take(200)}")
                // Try to extract error message from response
                val errorMsg = try {
                    JSONObject(responseBody).optString("error", "")
                        .let { err ->
                            if (err.isNotEmpty()) err
                            else JSONObject(responseBody).optJSONObject("error")?.optString("message", "")
                                ?.let { m -> "API 错误: $m" } ?: "API 错误: HTTP ${response.code}"
                        }
                } catch (_: Exception) {
                    "API 错误: HTTP ${response.code}"
                }
                return TaskSpecParser.ParseResult.Error(errorMsg)
            }

            // Parse response — handle various response formats
            val json = JSONObject(responseBody)
            val content = extractContent(json)
            if (content.isNullOrBlank()) {
                DebugLog.e("LlmParser", "Could not extract content from response: ${responseBody.take(300)}")
                return TaskSpecParser.ParseResult.Error("无法解析 LLM 响应内容")
            }

            DebugLog.i("LlmParser", "LLM response: ${content.take(200)}")

            // Extract JSON from the content (may be wrapped in markdown code block)
            val jsonStr = extractJson(content)
            return parseJsonResponse(jsonStr, input)
        } catch (e: Exception) {
            DebugLog.e("LlmParser", "LLM call failed: ${e.message}")
            // Fall back to rule-based parser
            return TaskSpecParser.parse(input)
        }
    }

    /**
     * Check if the current model/provider likely supports JSON mode.
     * Conservative: only enable for well-known providers.
     */
    private fun supportsJsonMode(): Boolean {
        val lowerUrl = apiUrl.lowercase()
        return lowerUrl.contains("openai.com") ||
            lowerUrl.contains("api.deepseek.com") ||
            lowerUrl.contains("dashscope.aliyuncs.com")
    }

    /**
     * Extract content from the API response.
     * Handles both OpenAI format and some non-standard formats.
     */
    private fun extractContent(json: JSONObject): String? {
        // Standard OpenAI format: choices[0].message.content
        try {
            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val message = choices.getJSONObject(0).optJSONObject("message")
                if (message != null) {
                    return message.optString("content", null)
                }
            }
        } catch (_: Exception) {}

        // Some providers return "output" or "result" directly
        try {
            return json.optString("output", null) ?: json.optString("result", null)
        } catch (_: Exception) {}

        return null
    }

    /**
     * Extract JSON string from LLM response content.
     * Handles cases where the LLM wraps JSON in markdown code blocks.
     */
    private fun extractJson(content: String): String {
        val trimmed = content.trim()

        // If it starts with {, assume it's raw JSON
        if (trimmed.startsWith("{")) {
            // Find the matching closing brace
            val lastBrace = trimmed.lastIndexOf("}")
            if (lastBrace > 0) {
                return trimmed.substring(0, lastBrace + 1)
            }
            return trimmed
        }

        // Try to extract from markdown code block: ```json ... ``` or ``` ... ```
        val codeBlockRegex = Regex("```(?:json)?\\s*\\n?([\\s\\S]*?)\\n?```")
        val match = codeBlockRegex.find(trimmed)
        if (match != null) {
            return match.groupValues[1].trim()
        }

        // Try to find JSON object in the text
        val jsonStart = trimmed.indexOf("{")
        val jsonEnd = trimmed.lastIndexOf("}")
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return trimmed.substring(jsonStart, jsonEnd + 1)
        }

        return trimmed
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
            DebugLog.e("LlmParser", "JSON parse failed: ${e.message}, input: ${jsonStr.take(100)}")
            return TaskSpecParser.ParseResult.Error("JSON 解析失败: ${e.message}")
        }
    }
}
