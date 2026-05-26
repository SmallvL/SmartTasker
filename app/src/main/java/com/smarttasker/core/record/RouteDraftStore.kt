package com.smarttasker.core.record

import android.content.Context
import com.smarttasker.core.record.model.*
import java.io.File

/**
 * Route draft persistence - uses StringBuilder JSON (no org.json dependency).
 * org.json.JSONObject causes native crash on some real devices.
 */
class RouteDraftStore(private val context: Context) {
    private val routesDir: File
        get() = File(context.filesDir, "routes").also { it.mkdirs() }

    fun save(draft: RouteDraft) {
        val dir = File(routesDir, draft.routeId).also { it.mkdirs() }
        val json = serialize(draft)
        File(dir, "route.json").writeText(json)
    }

    fun load(routeId: String): RouteDraft? {
        val file = File(routesDir, "$routeId/route.json")
        if (!file.exists()) return null
        return deserialize(file.readText())
    }

    fun listAll(): List<RouteDraft> {
        return routesDir.listFiles()?.filter { it.isDirectory }?.mapNotNull { dir ->
            try { load(dir.name) } catch (_: Exception) { null }
        }?.sortedByDescending { it.updatedAt } ?: emptyList()
    }

    fun delete(routeId: String) {
        File(routesDir, routeId).deleteRecursively()
    }

    // ===== Serialization (StringBuilder, no org.json) =====

    private fun serialize(draft: RouteDraft): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"routeId\": \"${esc(draft.routeId)}\",\n")
        sb.append("  \"name\": \"${esc(draft.name)}\",\n")
        sb.append("  \"source\": \"${draft.source.name}\",\n")
        sb.append("  \"status\": \"${draft.status.name}\",\n")
        sb.append("  \"createdAt\": ${draft.createdAt},\n")
        sb.append("  \"updatedAt\": ${draft.updatedAt},\n")
        draft.deviceProfile?.let {
            sb.append("  \"deviceProfile\": ${serializeDeviceProfile(it)},\n")
        }
        draft.appScope?.let {
            sb.append("  \"appScope\": ${serializeAppContext(it)},\n")
        }
        sb.append("  \"steps\": [")
        draft.steps.forEachIndexed { i, step ->
            if (i > 0) sb.append(",")
            sb.append("\n    ${serializeStep(step)}")
        }
        if (draft.steps.isNotEmpty()) sb.append("\n  ") else sb.append(" ")
        sb.append("]\n")
        sb.append("}")
        return sb.toString()
    }

    private fun serializeDeviceProfile(dp: DeviceProfile): String {
        return """{"screenWidth":${dp.screenWidth},"screenHeight":${dp.screenHeight},"densityDpi":${dp.densityDpi},"androidVersion":${dp.androidVersion},"manufacturer":"${esc(dp.manufacturer)}","model":"${esc(dp.model)}","rotation":${dp.rotation}}"""
    }

    private fun serializeAppContext(app: AppContextSnapshot): String {
        return """{"packageName":"${esc(app.packageName)}","activityName":"${esc(app.activityName)}"}"""
    }

    private fun serializeStep(step: RecordedStep): String {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"id\":\"${esc(step.id)}\",")
        sb.append("\"order\":${step.order},")
        sb.append("\"type\":\"${step.type.name}\",")
        sb.append("\"recordedAt\":${step.recordedAt},")
        sb.append("\"delayFromPreviousMs\":${step.delayFromPreviousMs},")
        sb.append("\"confidence\":${step.confidence}")
        step.notes?.let { sb.append(",\"notes\":\"${esc(it)}\"") }
        // Action
        sb.append(",\"action\":${serializeAction(step.action)}")
        // Target
        step.target?.let { t ->
            sb.append(",\"target\":${serializeTarget(t)}")
        }
        // Contexts
        step.appContext?.let { sb.append(",\"appContext\":${serializeAppContext(it)}") }
        step.deviceContext?.let { sb.append(",\"deviceContext\":${serializeDeviceProfile(it)}") }
        sb.append("}")
        return sb.toString()
    }

    private fun serializeAction(a: StepAction): String {
        return when (a) {
            is StepAction.Tap -> """{"op":"TAP","x":${a.x},"y":${a.y},"normalizedX":${a.normalizedX},"normalizedY":${a.normalizedY}}"""
            is StepAction.LongPress -> """{"op":"LONG_PRESS","x":${a.x},"y":${a.y},"durationMs":${a.durationMs}}"""
            is StepAction.Swipe -> """{"op":"SWIPE","startX":${a.startX},"startY":${a.startY},"endX":${a.endX},"endY":${a.endY},"durationMs":${a.durationMs}}"""
            is StepAction.Key -> """{"op":"KEY","keyCode":${a.keyCode},"keyName":"${esc(a.keyName)}","longPress":${a.longPress}}"""
            is StepAction.TextInput -> """{"op":"TEXT_INPUT","text":"${esc(a.text)}","sensitive":${a.sensitive}}"""
            is StepAction.Wait -> """{"op":"WAIT","durationMs":${a.durationMs}}"""
            is StepAction.Screenshot -> """{"op":"SCREENSHOT"}"""
            is StepAction.AppStart -> """{"op":"APP_START","packageName":"${esc(a.packageName)}"}"""
        }
    }

    private fun serializeTarget(t: TargetSnapshot): String {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"rawX\":${t.rawX},\"rawY\":${t.rawY},")
        sb.append("\"normalizedX\":${t.normalizedX},\"normalizedY\":${t.normalizedY},")
        sb.append("\"confidence\":${t.confidence},")
        sb.append("\"matchStrategy\":\"${t.matchStrategy}\",")
        sb.append("\"primaryLocator\":${serializeLocator(t.primaryLocator)},")
        sb.append("\"fallbackLocators\":[")
        t.fallbackLocators.forEachIndexed { i, loc ->
            if (i > 0) sb.append(",")
            sb.append(serializeLocator(loc))
        }
        sb.append("]}")
        return sb.toString()
    }

    private fun serializeLocator(loc: TargetLocator): String {
        return when (loc) {
            is TargetLocator.AccessibilityNode -> {
                """{"type":"accessibility","packageName":"${esc(loc.packageName ?: "")}","className":"${esc(loc.className ?: "")}","viewIdResourceName":"${esc(loc.viewIdResourceName ?: "")}","text":"${esc(loc.text ?: "")}","contentDescription":"${esc(loc.contentDescription ?: "")}","boundsLeft":${loc.boundsLeft},"boundsTop":${loc.boundsTop},"boundsRight":${loc.boundsRight},"boundsBottom":${loc.boundsBottom},"clickable":${loc.clickable},"enabled":${loc.enabled},"depthPath":"${esc(loc.depthPath)}"}"""
            }
            is TargetLocator.Text -> {
                """{"type":"text","text":"${esc(loc.text)}","packageName":"${esc(loc.packageName ?: "")}"}"""
            }
            is TargetLocator.Coordinate -> {
                """{"type":"coordinate","x":${loc.x},"y":${loc.y},"normalizedX":${loc.normalizedX},"normalizedY":${loc.normalizedY}}"""
            }
        }
    }

    // ===== Deserialization (regex-based, no org.json) =====

    private fun deserialize(json: String): RouteDraft {
        val steps = mutableListOf<RecordedStep>()
        val stepsArrStr = extractArray(json, "steps") ?: ""
        val stepObjects = extractArrayItems(stepsArrStr)
        for (stepStr in stepObjects) {
            steps.add(deserializeStep(stepStr))
        }
        val deviceProfileStr = extractObject(json, "deviceProfile")
        val deviceProfile = deviceProfileStr?.let { deserializeDeviceProfile(it) }
        val appScopeStr = extractObject(json, "appScope")
        val appScope = appScopeStr?.let { deserializeAppContext(it) }
        return RouteDraft(
            routeId = getString(json, "routeId") ?: "",
            name = getString(json, "name") ?: "",
            source = runCatching { RouteSource.valueOf(getString(json, "source") ?: "MANUAL_RECORDING") }.getOrDefault(RouteSource.MANUAL_RECORDING),
            status = runCatching { RouteStatus.valueOf(getString(json, "status") ?: "DRAFT") }.getOrDefault(RouteStatus.DRAFT),
            deviceProfile = deviceProfile,
            appScope = appScope,
            steps = steps,
            createdAt = getLong(json, "createdAt"),
            updatedAt = getLong(json, "updatedAt")
        )
    }

    private fun deserializeDeviceProfile(json: String): DeviceProfile {
        return DeviceProfile(
            screenWidth = getInt(json, "screenWidth"),
            screenHeight = getInt(json, "screenHeight"),
            densityDpi = getInt(json, "densityDpi"),
            androidVersion = getInt(json, "androidVersion"),
            manufacturer = getString(json, "manufacturer") ?: "",
            model = getString(json, "model") ?: "",
            rotation = getInt(json, "rotation")
        )
    }

    private fun deserializeAppContext(json: String): AppContextSnapshot {
        return AppContextSnapshot(
            packageName = getString(json, "packageName") ?: "",
            activityName = getString(json, "activityName") ?: ""
        )
    }

    private fun deserializeStep(json: String): RecordedStep {
        val actionObj = extractObject(json, "action") ?: ""
        val op = getString(actionObj, "op") ?: "TAP"
        val action: StepAction = when (op) {
            "TAP" -> StepAction.Tap(getInt(actionObj, "x"), getInt(actionObj, "y"))
            "LONG_PRESS" -> StepAction.LongPress(getInt(actionObj, "x"), getInt(actionObj, "y"), getLong(actionObj, "durationMs"))
            "SWIPE" -> StepAction.Swipe(getInt(actionObj, "startX"), getInt(actionObj, "startY"), getInt(actionObj, "endX"), getInt(actionObj, "endY"), getLong(actionObj, "durationMs"))
            "KEY" -> StepAction.Key(getInt(actionObj, "keyCode"), getString(actionObj, "keyName") ?: "")
            "TEXT_INPUT" -> StepAction.TextInput(getString(actionObj, "text") ?: "", getBool(actionObj, "sensitive"))
            "WAIT" -> StepAction.Wait(getLong(actionObj, "durationMs"))
            "SCREENSHOT" -> StepAction.Screenshot()
            "APP_START" -> StepAction.AppStart(getString(actionObj, "packageName") ?: "")
            else -> StepAction.Tap(0, 0)
        }
        val type = runCatching { RecordedStepType.valueOf(getString(json, "type") ?: "TAP") }.getOrDefault(RecordedStepType.TAP)
        val targetObj = extractObject(json, "target")
        val target = targetObj?.let { deserializeTarget(it) }
        val appCtx = extractObject(json, "appContext")?.let { deserializeAppContext(it) }
        val deviceCtx = extractObject(json, "deviceContext")?.let { deserializeDeviceProfile(it) }
        return RecordedStep(
            id = getString(json, "id") ?: java.util.UUID.randomUUID().toString(),
            order = getInt(json, "order"),
            type = type,
            action = action,
            recordedAt = getLong(json, "recordedAt"),
            delayFromPreviousMs = getLong(json, "delayFromPreviousMs"),
            appContext = appCtx,
            deviceContext = deviceCtx,
            target = target,
            confidence = getFloat(json, "confidence"),
            notes = getString(json, "notes")
        )
    }

    private fun deserializeTarget(json: String): TargetSnapshot? {
        val primaryLocStr = extractObject(json, "primaryLocator")
        val primaryLoc = primaryLocStr?.let { deserializeLocator(it) }
            ?: TargetLocator.Coordinate(getInt(json, "rawX"), getInt(json, "rawY"))
        val fallbackList = mutableListOf<TargetLocator>()
        val fallbackArr = extractArray(json, "fallbackLocators")
        if (fallbackArr != null) {
            for (item in extractArrayItems(fallbackArr)) {
                fallbackList.add(deserializeLocator(item))
            }
        }
        return TargetSnapshot(
            rawX = getInt(json, "rawX"),
            rawY = getInt(json, "rawY"),
            normalizedX = getFloat(json, "normalizedX"),
            normalizedY = getFloat(json, "normalizedY"),
            confidence = getFloat(json, "confidence"),
            matchStrategy = getString(json, "matchStrategy") ?: "COORDINATE",
            primaryLocator = primaryLoc,
            fallbackLocators = fallbackList
        )
    }

    private fun deserializeLocator(json: String): TargetLocator {
        val type = getString(json, "type") ?: "coordinate"
        return when (type) {
            "accessibility" -> TargetLocator.AccessibilityNode(
                packageName = getString(json, "packageName"),
                className = getString(json, "className"),
                viewIdResourceName = getString(json, "viewIdResourceName"),
                text = getString(json, "text"),
                contentDescription = getString(json, "contentDescription"),
                boundsLeft = getInt(json, "boundsLeft"),
                boundsTop = getInt(json, "boundsTop"),
                boundsRight = getInt(json, "boundsRight"),
                boundsBottom = getInt(json, "boundsBottom"),
                clickable = getBool(json, "clickable"),
                enabled = getBool(json, "enabled"),
                depthPath = getString(json, "depthPath") ?: ""
            )
            "text" -> TargetLocator.Text(
                text = getString(json, "text") ?: "",
                packageName = getString(json, "packageName")
            )
            else -> TargetLocator.Coordinate(
                x = getInt(json, "x"),
                y = getInt(json, "y"),
                normalizedX = getFloat(json, "normalizedX"),
                normalizedY = getFloat(json, "normalizedY")
            )
        }
    }

    // ===== Parsing helpers =====

    private fun esc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

    private fun getString(json: String, key: String): String? {
        val m = Regex(""""$key"\s*:\s*"([^"]*?)"""").find(json) ?: return null
        return m.groupValues[1].replace("\\\"", "\"").replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\")
    }

    private fun getInt(json: String, key: String): Int {
        return Regex(""""$key"\s*:\s*(-?\d+)""").find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun getLong(json: String, key: String): Long {
        return Regex(""""$key"\s*:\s*(-?\d+)""").find(json)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    }

    private fun getFloat(json: String, key: String): Float {
        return Regex(""""$key"\s*:\s*([0-9.eE+-]+)""").find(json)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
    }

    private fun getBool(json: String, key: String): Boolean {
        return Regex(""""$key"\s*:\s*(true|false)""").find(json)?.groupValues?.get(1) == "true"
    }

    private fun extractObject(json: String, key: String): String? {
        val m = Regex(""""$key"\s*:\s*\{""").find(json) ?: return null
        return extractBalanced(json, m.range.last, '{', '}')
    }

    private fun extractArray(json: String, key: String): String? {
        val m = Regex(""""$key"\s*:\s*\[""").find(json) ?: return null
        return extractBalanced(json, m.range.last, '[', ']')
    }

    private fun extractBalanced(json: String, pos: Int, open: Char = '{', close: Char = '}'): String? {
        if (pos >= json.length || json[pos] != open) return null
        var depth = 0; var inStr = false; var escaped = false
        for (i in pos until json.length) {
            val c = json[i]
            if (escaped) { escaped = false; continue }
            if (c == '\\') { escaped = true; continue }
            if (c == '"') { inStr = !inStr; continue }
            if (inStr) continue
            when (c) {
                open -> depth++
                close -> { depth--; if (depth == 0) return json.substring(pos, i + 1) }
            }
        }
        return null
    }

    private fun extractArrayItems(array: String): List<String> {
        val results = mutableListOf<String>()
        val inner = array.trim().removeSurrounding("[", "]").trim()
        if (inner.isEmpty()) return results
        var depth = 0; var start = -1; var inStr = false; var escaped = false
        for (i in inner.indices) {
            val c = inner[i]
            if (escaped) { escaped = false; continue }
            if (c == '\\') { escaped = true; continue }
            if (c == '"') { inStr = !inStr; continue }
            if (inStr) continue
            when (c) {
                '{' -> { if (depth == 0) start = i; depth++ }
                '}' -> { depth--; if (depth == 0 && start >= 0) { results.add(inner.substring(start, i + 1)); start = -1 } }
            }
        }
        return results
    }
}
