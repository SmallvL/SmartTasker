package com.smarttasker.core.adapter

import com.smarttasker.data.entity.RunRecordEntity
import com.smarttasker.data.entity.TraceEventEntity
import java.util.UUID

/**
 * Converts AutoLXB trace JSONL to SmartTask product layer entities.
 */
object TraceAdapter {

    // -- JSON helpers (no org.json) --

    private fun jsonStr(json: String, key: String): String {
        val pattern = "\"${Regex.escape(key)}\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\""
        val regex = Regex(pattern)
        return regex.find(json)?.groupValues?.get(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\n", "\n")
            ?.replace("\\\\", "\\") ?: ""
    }

    private fun jsonLong(json: String, key: String): Long {
        val pattern = "\"${Regex.escape(key)}\"\\s*:\\s*(-?\\d+)"
        val regex = Regex(pattern)
        return regex.find(json)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    }

    private fun jsonInt(json: String, key: String): Int {
        val pattern = "\"${Regex.escape(key)}\"\\s*:\\s*(-?\\d+)"
        val regex = Regex(pattern)
        return regex.find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    // -- Public API --

    /**
     * Parse trace JSONL lines into RunRecord + TraceEvents.
     */
    fun parseTrace(traceLines: List<String>, taskId: String, runId: String): ParsedTrace {
        val events = mutableListOf<TraceEventEntity>()
        var taskStart: Long? = null
        var taskEnd: Long? = null
        var finalStatus = "unknown"
        var failedStepId: String? = null
        var failureType: String? = null
        var diagnosisSummary = ""
        var diagnosisSuggestion = ""

        for (line in traceLines) {
            try {
                val ts = jsonLong(line, "ts")
                val event = jsonStr(line, "event")

                events.add(TraceEventEntity(
                    runId = runId,
                    stepId = jsonStr(line, "step_id"),
                    timestamp = ts,
                    level = if (event.contains("error") || event.contains("fail")) "error" else "info",
                    eventType = event,
                    message = generateMessage(line),
                    details = line
                ))

                when (event) {
                    "task_start" -> taskStart = ts
                    "task_end" -> {
                        taskEnd = ts
                        finalStatus = jsonStr(line, "status").ifEmpty { "unknown" }
                    }
                    "step_fail", "action_failed" -> {
                        failedStepId = jsonStr(line, "step_id").ifEmpty { jsonStr(line, "step") }
                        failureType = jsonStr(line, "reason").ifEmpty { jsonStr(line, "failure_type") }
                            .ifEmpty { "unknown" }
                    }
                    "locator_not_found" -> {
                        failedStepId = jsonStr(line, "step_id")
                        failureType = "locator_not_found"
                    }
                    "model_error" -> {
                        failureType = "model_error"
                    }
                }
            } catch (e: Exception) {
                // Skip malformed lines
            }
        }

        // Generate diagnosis
        if (finalStatus == "failed" || failureType != null) {
            val (summary, suggestion) = generateDiagnosis(failureType, failedStepId)
            diagnosisSummary = summary
            diagnosisSuggestion = suggestion
        }

        val durationMs = if (taskStart != null && taskEnd != null) taskEnd - taskStart else 0
        val modelCalls = events.count { it.eventType == "model_call" || it.eventType == "vision_call" }

        val runRecord = RunRecordEntity(
            runId = runId,
            taskId = taskId,
            status = if (finalStatus == "success") "success" else "failed",
            durationMs = durationMs,
            modelCalls = modelCalls,
            failedStepId = failedStepId,
            failureType = failureType,
            diagnosisSummary = diagnosisSummary,
            diagnosisSuggestion = diagnosisSuggestion
        )

        return ParsedTrace(runRecord, events)
    }

    private fun generateMessage(line: String): String {
        val event = jsonStr(line, "event")
        return when (event) {
            "task_start" -> "任务开始执行"
            "task_end" -> "任务执行结束: ${jsonStr(line, "status")}"
            "step_start" -> "步骤 ${jsonInt(line, "step")} 开始: ${jsonStr(line, "type")}"
            "step_ok" -> "步骤 ${jsonInt(line, "step")} 成功"
            "step_fail" -> "步骤 ${jsonInt(line, "step")} 失败: ${jsonStr(line, "reason")}"
            "locator_not_found" -> "未找到控件: ${jsonStr(line, "locator")}"
            "model_call" -> "调用 AI 模型"
            "vision_call" -> "调用视觉模型"
            else -> event
        }
    }

    private fun generateDiagnosis(failureType: String?, stepId: String?): Pair<String, String> {
        return when (failureType) {
            "locator_not_found" -> Pair(
                "第 ${stepId ?: "?"} 步未找到目标控件",
                "可能是 App 页面布局变化，建议将坐标定位改为文本定位"
            )
            "timeout" -> Pair(
                "操作超时",
                "页面加载时间过长或网络不稳定，建议增加等待时间"
            )
            "model_error" -> Pair(
                "AI 模型调用失败",
                "请检查模型 API 地址和密钥是否正确"
            )
            "safety_blocked" -> Pair(
                "操作被安全策略拦截",
                "该动作被标记为高风险，需要用户确认"
            )
            "permission_error" -> Pair(
                "权限不足",
                "请检查无障碍服务和 ADB 权限"
            )
            else -> Pair(
                "任务执行失败",
                "请查看技术日志了解详情"
            )
        }
    }

    data class ParsedTrace(
        val runRecord: RunRecordEntity,
        val events: List<TraceEventEntity>
    )
}
