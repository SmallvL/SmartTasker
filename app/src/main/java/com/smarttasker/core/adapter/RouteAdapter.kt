package com.smarttasker.core.adapter

import com.smarttasker.data.entity.RouteStepEntity
import com.smarttasker.data.entity.RouteVersionEntity
import java.util.UUID

/**
 * Converts AutoLXB raw route format to SmartTask product layer entities.
 */
object RouteAdapter {

    // -- JSON helpers (no org.json) --

    private fun jsonStr(json: String, key: String): String {
        val regex = Regex(""""${Regex.escape(key)}"\s*:\s*"((?:[^"\\]|\\.)*)"""")
        return regex.find(json)?.groupValues?.get(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\n", "\n")
            ?.replace("\\\\", "\\") ?: ""
    }

    private fun extractJsonObject(json: String, key: String): String? {
        val pattern = Regex(""""${Regex.escape(key)}"\s*:\s*\{""")
        val match = pattern.find(json) ?: return null
        return extractBalanced(json, match.range.last)
    }

    private fun extractJsonArray(json: String, key: String): String? {
        val pattern = Regex(""""${Regex.escape(key)}"\s*:\s*\[""")
        val match = pattern.find(json) ?: return null
        return extractBalanced(json, match.range.last)
    }

    private fun extractBalanced(json: String, pos: Int): String? {
        if (pos >= json.length) return null
        val open = json[pos]
        val close = if (open == '{') '}' else ']'
        var depth = 0
        var inStr = false
        var escaped = false
        for (i in pos until json.length) {
            val c = json[i]
            if (escaped) { escaped = false; continue }
            if (c == '\\') { escaped = true; continue }
            if (c == '"') { inStr = !inStr; continue }
            if (inStr) continue
            if (c == open) depth++
            else if (c == close) { depth--; if (depth == 0) return json.substring(pos, i + 1) }
        }
        return null
    }

    private fun arrayItems(arrayStr: String): List<String> {
        val inner = arrayStr.trim().removeSurrounding("[", "]").trim()
        if (inner.isEmpty()) return emptyList()
        val items = mutableListOf<String>()
        var depth = 0
        var inStr = false
        var escaped = false
        var start = 0
        for (i in inner.indices) {
            val c = inner[i]
            if (escaped) { escaped = false; continue }
            if (c == '\\') { escaped = true; continue }
            if (c == '"') { inStr = !inStr; continue }
            if (inStr) continue
            when (c) {
                '{', '[' -> depth++
                '}', ']' -> depth--
                ',' -> if (depth == 0) {
                    items.add(inner.substring(start, i).trim())
                    start = i + 1
                }
            }
        }
        items.add(inner.substring(start).trim())
        return items
    }

    private fun jsonIntArray(arrayStr: String?): List<Int> {
        if (arrayStr == null) return emptyList()
        val inner = arrayStr.trim().removeSurrounding("[", "]").trim()
        if (inner.isEmpty()) return emptyList()
        return inner.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    /**
     * Extract the first string argument from a JSON array like ["some text"].
     */
    private fun extractStringArg(arrayStr: String?): String {
        if (arrayStr == null) return ""
        val inner = arrayStr.trim().removeSurrounding("[", "]").trim()
        // Remove surrounding quotes and unescape
        return inner.removeSurrounding("\"")
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
    }

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
    }

    // -- Public API --

    /**
     * Parse AutoLXB route JSON into RouteVersion + RouteSteps.
     */
    fun parseRoute(rawJson: String, taskId: String): ParsedRoute? {
        return try {
            val segmentsStr = extractJsonArray(rawJson, "segments") ?: return null
            val segments = arrayItems(segmentsStr)

            val steps = mutableListOf<RouteStepEntity>()
            val routeId = UUID.randomUUID().toString().take(8)

            for (segmentStr in segments) {
                val segmentStepsStr = extractJsonArray(segmentStr, "steps") ?: continue
                val segmentSteps = arrayItems(segmentStepsStr)
                for ((j, stepStr) in segmentSteps.withIndex()) {
                    steps.add(parseStep(stepStr, routeId, j + 1))
                }
            }

            val routeVersion = RouteVersionEntity(
                routeId = routeId,
                taskId = taskId,
                version = "1.0.0",
                status = "draft",
                source = "ai_learned"
            )

            ParsedRoute(routeVersion, steps)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseStep(stepJson: String, routeId: String, index: Int): RouteStepEntity {
        val op = jsonStr(stepJson, "op").ifEmpty { "TAP" }.uppercase()
        val argsStr = extractJsonArray(stepJson, "args")
        val locatorStr = extractJsonObject(stepJson, "locator")
        val semanticStr = extractJsonObject(stepJson, "semantic_descriptor")

        // Determine step type
        val type = when (op) {
            "TAP" -> {
                // If args contain a string (text to find), use tap_by_text
                val args = extractJsonArray(stepJson, "args")
                val hasStringArg = args != null && args.contains("\"")
                if (hasStringArg && locatorStr == null) "tap_by_text" else "tap"
            }
            "INPUT" -> "input"
            "SWIPE" -> "swipe"
            "BACK" -> "back"
            "WAIT" -> "wait"
            "LAUNCH" -> "open_app"
            else -> op.lowercase()
        }

        // Extract locator info (3-level: locator → containerProbe → fallback_point)
        val containerProbeStr = extractJsonObject(stepJson, "container_probe")
        // semanticNote and expectedNote available for future use (step description enrichment)
        @Suppress("UNUSED_VARIABLE") val semanticNote = jsonStr(stepJson, "semantic_note")
        @Suppress("UNUSED_VARIABLE") val expectedNote = jsonStr(stepJson, "expected")

        val locatorStrategy: String
        val locatorValue: String
        val fallbackStrategy: String
        val fallbackValue: String

        if (locatorStr != null) {
            val text = jsonStr(locatorStr, "text")
            val resourceId = jsonStr(locatorStr, "resource_id")
            val contentDesc = jsonStr(locatorStr, "content_desc")

            when {
                text.isNotEmpty() -> {
                    locatorStrategy = "text"
                    locatorValue = text
                }
                resourceId.isNotEmpty() -> {
                    locatorStrategy = "resource_id"
                    locatorValue = resourceId
                }
                contentDesc.isNotEmpty() -> {
                    locatorStrategy = "content_desc"
                    locatorValue = contentDesc
                }
                else -> {
                    locatorStrategy = "coordinate"
                    val args = jsonIntArray(argsStr)
                    locatorValue = if (args.size >= 2) "${args[0]},${args[1]}" else ""
                }
            }

            // Fallback: containerProbe → fallback_point → args
            when {
                containerProbeStr != null -> {
                    // Level 2 fallback: containerProbe
                    val cpText = jsonStr(containerProbeStr, "text")
                    val cpResId = jsonStr(containerProbeStr, "resource_id")
                    val cpDesc = jsonStr(containerProbeStr, "content_desc")
                    fallbackStrategy = when {
                        cpText.isNotEmpty() -> "text"
                        cpResId.isNotEmpty() -> "resource_id"
                        cpDesc.isNotEmpty() -> "content_desc"
                        else -> "coordinate"
                    }
                    fallbackValue = when {
                        cpText.isNotEmpty() -> cpText
                        cpResId.isNotEmpty() -> cpResId
                        cpDesc.isNotEmpty() -> cpDesc
                        else -> {
                            val fp = jsonIntArray(extractJsonArray(stepJson, "fallback_point") ?: "")
                            if (fp.size >= 2) "${fp[0]},${fp[1]}" else ""
                        }
                    }
                }
                else -> {
                    // Level 3 fallback: fallback_point coordinates
                    val stepFallbackStr = extractJsonArray(stepJson, "fallback_point")
                    if (stepFallbackStr != null) {
                        val fp = jsonIntArray(stepFallbackStr)
                        if (fp.size >= 2) {
                            fallbackStrategy = "coordinate"
                            fallbackValue = "${fp[0]},${fp[1]}"
                        } else {
                            fallbackStrategy = ""
                            fallbackValue = ""
                        }
                    } else {
                        val args = jsonIntArray(argsStr)
                        if (args.size >= 2) {
                            fallbackStrategy = "coordinate"
                            fallbackValue = "${args[0]},${args[1]}"
                        } else {
                            fallbackStrategy = ""
                            fallbackValue = ""
                        }
                    }
                }
            }
        } else {
            // No locator - use coordinates or text based on type
            if (type == "tap_by_text" || type == "input") {
                // Extract string argument
                val stringArg = extractStringArg(argsStr)
                locatorStrategy = if (type == "tap_by_text") "text" else "coordinate"
                locatorValue = stringArg
                fallbackStrategy = ""
                fallbackValue = ""
            } else if (type == "open_app") {
                // Package name from "package" field
                locatorStrategy = "package"
                locatorValue = jsonStr(stepJson, "package")
                fallbackStrategy = ""
                fallbackValue = ""
            } else if (type == "wait") {
                locatorStrategy = "duration"
                val args = jsonIntArray(argsStr)
                locatorValue = if (args.isNotEmpty()) "${args[0]}" else "1000"
                fallbackStrategy = ""
                fallbackValue = ""
            } else {
                locatorStrategy = "coordinate"
                val args = jsonIntArray(argsStr)
                locatorValue = if (args.size >= 2) "${args[0]},${args[1]}" else ""
                fallbackStrategy = ""
                fallbackValue = ""
            }
        }

        // Summary: prefer explicit summary from JSON, then semantic descriptor, then generate
        val jsonSummary = jsonStr(stepJson, "summary")
        val summary = when {
            jsonSummary.isNotEmpty() -> jsonSummary
            semanticStr != null -> jsonStr(semanticStr, "instruction").ifEmpty { generateSummary(type, locatorValue) }
            else -> generateSummary(type, locatorValue)
        }

        // Risk detection
        val riskLevel = detectRisk(summary, type)

        return RouteStepEntity(
            stepId = UUID.randomUUID().toString().take(8),
            routeId = routeId,
            stepIndex = index,
            type = type,
            summary = summary,
            locatorStrategy = locatorStrategy,
            locatorValue = locatorValue,
            locatorConfidence = if (locatorStrategy == "coordinate") 0.5f else 0.85f,
            fallbackStrategy = fallbackStrategy,
            fallbackValue = fallbackValue,
            riskLevel = riskLevel,
            source = "ai_learned"
        )
    }

    private fun generateSummary(type: String, value: String): String {
        return when (type) {
            "tap" -> if (value.isNotEmpty()) "点击 $value" else "点击"
            "tap_by_text" -> if (value.isNotEmpty()) "点击「$value」" else "点击"
            "input" -> "输入 $value"
            "swipe" -> "滑动"
            "back" -> "返回"
            "wait" -> "等待页面加载"
            "open_app" -> if (value.isNotEmpty()) "打开应用 $value" else "打开应用"
            else -> type
        }
    }

    private fun detectRisk(summary: String, type: String): String {
        val lower = summary.lowercase()
        return when {
            lower.contains("转账") || lower.contains("支付") || lower.contains("贷款") -> "critical"
            lower.contains("发送") || lower.contains("删除") || lower.contains("提交") ||
            lower.contains("下单") || lower.contains("注销") -> "high"
            lower.contains("确认") || lower.contains("同意") -> "medium"
            else -> "low"
        }
    }

    data class ParsedRoute(
        val routeVersion: RouteVersionEntity,
        val steps: List<RouteStepEntity>
    )

    /**
     * Convert RouteSteps back to AutoLXB route JSON for replay.
     */
    fun toRouteJson(steps: List<RouteStepEntity>): String {
        val sb = StringBuilder()
        sb.append("{\"segments\":[{\"steps\":[")
        steps.forEachIndexed { idx, step ->
            if (idx > 0) sb.append(",")
            val op = when (step.type) {
                "open_app" -> "LAUNCH"
                "tap", "tap_by_text" -> "TAP"
                "input" -> "INPUT"
                "swipe", "swipe_down", "swipe_up" -> "SWIPE"
                "back" -> "KEY"
                "home" -> "KEY"
                "wait" -> "WAIT"
                else -> step.type.uppercase()
            }
            sb.append("{\"op\":\"$op\"")

            // Add args based on type
            when (step.type) {
                "open_app" -> sb.append(",\"package\":\"${escapeJson(step.locatorValue)}\"")
                "tap_by_text" -> sb.append(",\"args\":[\"${escapeJson(step.locatorValue)}\"]")
                "tap" -> {
                    val coords = step.locatorValue.split(",")
                    if (coords.size >= 2) {
                        sb.append(",\"args\":[${coords[0].trim()},${coords[1].trim()}]")
                    }
                }
                "input" -> sb.append(",\"args\":[\"${escapeJson(step.locatorValue)}\"]")
                "swipe_down" -> sb.append(",\"args\":[540,1600,540,600,500]")
                "swipe_up" -> sb.append(",\"args\":[540,600,540,1600,500]")
                "back" -> sb.append(",\"args\":[4]")
                "home" -> sb.append(",\"args\":[3]")
                "wait" -> sb.append(",\"args\":[${step.locatorValue.toLongOrNull() ?: 1000}]")
                else -> sb.append(",\"args\":[]")
            }

            // Add locator for text-based taps
            if (step.locatorStrategy in listOf("text", "resource_id", "content_desc")) {
                sb.append(",\"locator\":{\"${step.locatorStrategy}\":\"${escapeJson(step.locatorValue)}\"}")
            }

            sb.append(",\"summary\":\"${escapeJson(step.summary)}\"")
            sb.append("}")
        }
        sb.append("]}]}")
        return sb.toString()
    }
}
