package com.smarttasker.core.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.smarttasker.core.direct.InputEngine
import com.smarttasker.core.direct.SenseEngine
import com.smarttasker.core.direct.ShellExecutor
import com.smarttasker.core.direct.ShellResult
import com.smarttasker.data.database.AppDatabase
import com.smarttasker.data.entity.RouteStepEntity
import com.smarttasker.data.entity.RunRecordEntity
import com.smarttasker.util.DebugLog
import kotlinx.coroutines.*

/**
 * Route playback service - executes a route's steps via ADB shell commands.
 * Can be triggered via ADB: am broadcast -a com.smarttasker.action.PLAY_ROUTE --es routeId <routeId>
 */
class RoutePlaybackService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var playbackJob: Job? = null

    companion object {
        private const val CHANNEL_ID = "route_playback"
        private const val NOTIFICATION_ID = 2001
        const val ACTION_PLAY_ROUTE = "com.smarttasker.action.PLAY_ROUTE"
        const val EXTRA_ROUTE_ID = "route_id"

        @Volatile
        var isPlaying = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PLAY_ROUTE) {
            val routeId = intent.getStringExtra(EXTRA_ROUTE_ID)
            if (routeId.isNullOrEmpty()) {
                DebugLog.e("Playback", "No routeId provided")
                stopSelf()
                return START_NOT_STICKY
            }
            startForeground(NOTIFICATION_ID, buildNotification("执行路线 $routeId..."))
            playbackJob?.cancel()
            playbackJob = scope.launch {
                executeRoute(routeId)
                stopSelf()
            }
        } else {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        playbackJob?.cancel()
        scope.cancel()
        isPlaying = false
        super.onDestroy()
    }

    private suspend fun executeRoute(routeId: String) {
        isPlaying = true
        val startTime = System.currentTimeMillis()
        DebugLog.i("Playback", "Starting route playback: $routeId")

        // Initialize ShellExecutor
        ShellExecutor.init(applicationContext)

        val db = AppDatabase.getInstance(applicationContext)
        val steps = db.routeDao().getStepsForRouteSync(routeId)
        if (steps.isEmpty()) {
            DebugLog.e("Playback", "No steps found for route: $routeId")
            isPlaying = false
            return
        }

        // Get task ID from route
        val route = db.routeDao().getRouteById(routeId)
        val taskId = route?.taskId ?: ""

        DebugLog.i("Playback", "Found ${steps.size} steps for route $routeId")
        val activeSteps = steps.filter { it.enabled }
        val sense = SenseEngine(applicationContext)
        val input = InputEngine()

        var successCount = 0
        var failedStepIndex: Int? = null
        var failedStepReason = ""

        for ((index, step) in activeSteps.withIndex()) {
            DebugLog.i("Playback", "Step ${index + 1}/${activeSteps.size}: ${step.type} - ${step.summary}")
            try {
                val ok = executeStepAction(step, sense, input)
                if (ok) {
                    successCount++
                    DebugLog.i("Playback", "  ✓ ${step.summary}")
                } else {
                    if (failedStepIndex == null) {
                        failedStepIndex = index + 1
                        failedStepReason = "步骤 ${index + 1} 执行失败: ${step.summary}"
                    }
                    DebugLog.e("Playback", "  ✗ ${step.summary}")
                }
                // Wait between steps
                if (step.waitTimeMs > 0) {
                    DebugLog.i("Playback", "  Waiting ${step.waitTimeMs}ms...")
                    delay(step.waitTimeMs)
                }
            } catch (e: Exception) {
                if (failedStepIndex == null) {
                    failedStepIndex = index + 1
                    failedStepReason = "步骤 ${index + 1} 异常: ${e.message}"
                }
                DebugLog.e("Playback", "  ✗ ${step.summary}: ${e.message}")
            }
        }

        val durationMs = System.currentTimeMillis() - startTime
        val allSuccess = successCount == activeSteps.size
        val result = if (allSuccess) {
            "✅ 全部 $successCount 步执行成功"
        } else {
            "⚠️ $successCount/${activeSteps.size} 步成功"
        }
        DebugLog.i("Playback", "Route playback complete: $result")

        // Save run record
        if (taskId.isNotEmpty()) {
            val runId = "run_${System.currentTimeMillis()}"
            val runRecord = RunRecordEntity(
                runId = runId,
                taskId = taskId,
                routeVersion = routeId,
                triggerType = "manual",
                status = if (allSuccess) "success" else "failed",
                durationMs = durationMs,
                failedStepId = failedStepIndex?.toString(),
                failureType = if (allSuccess) null else "route_step_failed",
                diagnosisSummary = if (allSuccess) "路线回放成功" else failedStepReason,
                diagnosisSuggestion = if (allSuccess) "路线执行完成" else "请检查步骤 ${failedStepIndex} 的配置",
                retryCount = 0
            )
            try {
                db.runRecordDao().insertRun(runRecord)
                DebugLog.i("Playback", "Run record saved: $runId status=${runRecord.status}")
            } catch (e: Exception) {
                DebugLog.e("Playback", "Failed to save run record: ${e.message}")
            }
        }

        isPlaying = false
    }

    private suspend fun executeStepAction(step: RouteStepEntity, sense: SenseEngine, input: InputEngine): Boolean {
        return try {
            when (step.type) {
                "tap", "tap_by_text" -> {
                    val coords = resolveTapCoordinates(step, sense)
                    if (coords != null) {
                        DebugLog.i("Playback", "  Tapping (${coords.first}, ${coords.second})")
                        input.tap(coords.first, coords.second)
                        delay(500)
                        true
                    } else {
                        DebugLog.e("Playback", "  Cannot resolve tap coordinates for: ${step.locatorValue}")
                        false
                    }
                }
                "swipe" -> {
                    val coords = parseSwipeCoordinates(step)
                    if (coords != null) {
                        input.swipe(coords[0], coords[1], coords[2], coords[3], 300)
                        delay(500)
                        true
                    } else false
                }
                "input" -> {
                    DebugLog.i("Playback", "  Inputting text: ${step.locatorValue}")
                    input.inputText(step.locatorValue)
                    delay(300)
                    true
                }
                "wait" -> {
                    val ms = step.locatorValue.toLongOrNull() ?: step.waitTimeMs
                    DebugLog.i("Playback", "  Waiting ${ms}ms")
                    delay(ms)
                    true
                }
                "back" -> {
                    input.pressBack()
                    delay(500)
                    true
                }
                "home" -> {
                    input.pressHome()
                    delay(500)
                    true
                }
                "open_app" -> {
                    DebugLog.i("Playback", "  Launching app: ${step.locatorValue}")
                    sense.launchApp(step.locatorValue)
                    delay(2000)
                    true
                }
                "key" -> {
                    val keyCode = step.locatorValue.toIntOrNull() ?: mapKeyNameToCode(step.locatorValue)
                    if (keyCode > 0) {
                        DebugLog.i("Playback", "  Pressing key: $keyCode (${step.locatorValue})")
                        input.pressKey(keyCode)
                        delay(300)
                        true
                    } else false
                }
                else -> {
                    val coords = parseCoordinate(step.locatorValue)
                    if (coords != null) {
                        input.tap(coords.first, coords.second)
                        delay(500)
                        true
                    } else false
                }
            }
        } catch (e: Exception) {
            DebugLog.e("Playback", "  Step execution error: ${e.message}")
            false
        }
    }

    private suspend fun resolveTapCoordinates(step: RouteStepEntity, sense: SenseEngine): Pair<Int, Int>? {
        // Level 1: Try primary locator strategy
        val primaryResult = when (step.locatorStrategy) {
            "coordinate" -> parseCoordinate(step.locatorValue)
            "text", "resource_id", "content_desc" -> {
                val result = sense.dumpHierarchy()
                when (result) {
                    is com.smarttasker.core.bridge.HierarchyResult.Success -> {
                        findElementBounds(result.xml, step.locatorStrategy, step.locatorValue)
                    }
                    else -> null
                }
            }
            else -> parseCoordinate(step.locatorValue)
        }
        if (primaryResult != null) return primaryResult

        // Level 2: Try fallback strategy (containerProbe pattern)
        if (step.fallbackStrategy.isNotBlank() && step.fallbackValue.isNotBlank()) {
            val fallbackResult = when (step.fallbackStrategy) {
                "coordinate" -> parseCoordinate(step.fallbackValue)
                "text", "resource_id", "content_desc" -> {
                    val result = sense.dumpHierarchy()
                    when (result) {
                        is com.smarttasker.core.bridge.HierarchyResult.Success -> {
                            findElementBounds(result.xml, step.fallbackStrategy, step.fallbackValue)
                        }
                        else -> null
                    }
                }
                else -> null
            }
            if (fallbackResult != null) return fallbackResult
        }

        // Level 3: Last resort — if locatorStrategy is text-based, try coordinate from fallbackValue
        if (step.locatorStrategy != "coordinate" && step.fallbackValue.isNotBlank()) {
            return parseCoordinate(step.fallbackValue)
        }

        return null
    }

    private fun findElementBounds(xml: String, strategy: String, value: String): Pair<Int, Int>? {
        try {
            val attrName = when (strategy) {
                "text" -> "text"
                "resource_id" -> "resource-id"
                "content_desc" -> "content-desc"
                else -> return null
            }
            val nodePattern = Regex("""<node\s[^>]*?>""")
            val boundsPattern = Regex("""bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"""")
            val attrPattern = Regex("""$attrName="([^"]*?)"""")
            for (nodeMatch in nodePattern.findAll(xml)) {
                val nodeStr = nodeMatch.value
                val attrMatch = attrPattern.find(nodeStr) ?: continue
                val attrValue = attrMatch.groupValues[1]
                if (attrValue == value || attrValue.contains(value)) {
                    val boundsMatch = boundsPattern.find(nodeStr) ?: continue
                    val x1 = boundsMatch.groupValues[1].toInt()
                    val y1 = boundsMatch.groupValues[2].toInt()
                    val x2 = boundsMatch.groupValues[3].toInt()
                    val y2 = boundsMatch.groupValues[4].toInt()
                    return Pair((x1 + x2) / 2, (y1 + y2) / 2)
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun parseSwipeCoordinates(step: RouteStepEntity): List<Int>? {
        val value = step.locatorValue.ifEmpty { step.fallbackValue }
        if (value.isBlank()) return null
        val commaParts = value.split(",").map { it.trim().toIntOrNull() }
        if (commaParts.size >= 4 && commaParts.all { it != null }) {
            return commaParts.take(4).map { it!! }
        }
        return null
    }

    private fun parseCoordinate(value: String): Pair<Int, Int>? {
        if (value.isBlank()) return null
        val parts = value.split(",")
        if (parts.size < 2) return null
        val x = parts[0].trim().toIntOrNull() ?: return null
        val y = parts[1].trim().toIntOrNull() ?: return null
        return Pair(x, y)
    }

    private fun mapKeyNameToCode(name: String): Int = when (name.uppercase()) {
        "BACK" -> 4
        "HOME" -> 3
        "MENU" -> 82
        "POWER" -> 26
        "APP_SWITCH", "RECENT" -> 187
        "VOLUME_UP" -> 24
        "VOLUME_DOWN" -> 25
        "ENTER" -> 66
        "DEL", "DELETE" -> 67
        "TAB" -> 61
        "SPACE" -> 62
        else -> 0
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "路线回放",
            NotificationManager.IMPORTANCE_LOW
        )
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("SmartTasker")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }
}
