package com.smarttasker.core.parser

import org.json.JSONArray
import org.json.JSONObject

/**
 * TaskSpec data class - the structured representation of a task.
 */
data class TaskSpec(
    val taskId: String,
    val name: String,
    val description: String,
    val targetApp: AppInfo?,
    val trigger: TriggerConfig,
    val execution: ExecutionConfig,
    val risk: RiskConfig,
    val playbook: String,
    val status: String = "draft"
) {
    data class AppInfo(
        val name: String,
        val packageName: String,
        val confidence: Float
    )

    data class TriggerConfig(
        val type: String,    // manual / schedule / notification
        val time: String = "",
        val repeat: String = "once",
        val notificationPattern: String = ""
    )

    data class ExecutionConfig(
        val mode: String = "learn_first_then_replay",
        val routeEnabled: Boolean = true,
        val fallbackToVision: Boolean = true
    )

    data class RiskConfig(
        val level: String,   // low / medium / high / critical
        val requiresConfirmation: Boolean = false,
        val reason: String = ""
    )

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("task_id", taskId)
            put("name", name)
            put("description", description)
            put("target_app", JSONObject().apply {
                put("name", targetApp?.name ?: "")
                put("package", targetApp?.packageName ?: "")
                put("confidence", targetApp?.confidence ?: 0f)
            })
            put("trigger", JSONObject().apply {
                put("type", trigger.type)
                put("time", trigger.time)
                put("repeat", trigger.repeat)
            })
            put("execution", JSONObject().apply {
                put("mode", execution.mode)
                put("route_enabled", execution.routeEnabled)
            })
            put("risk", JSONObject().apply {
                put("level", risk.level)
                put("requires_confirmation", risk.requiresConfirmation)
                put("reason", risk.reason)
            })
            put("playbook", playbook)
            put("status", status)
        }
    }
}
