package com.smarttasker.core.direct

import android.content.Context
import android.util.Base64
import com.smarttasker.core.bridge.*
import com.smarttasker.core.parser.LlmTaskSpecParser
import com.smarttasker.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Direct CoreBridge implementation — AI vision loop that operates the phone
 * like a human, records every step with locator info, and produces a
 * replayable script.
 *
 * Architecture (inspired by AutoLXB CortexFsmEngine):
 *
 *   INIT ──► VISION_ACT loop ──► FINISH / FAIL
 *
 * VISION_ACT loop (per turn):
 *   1. Screenshot + dump UI hierarchy
 *   2. Build 12-tag structured prompt (AutoLXB compatible)
 *   3. Call multimodal LLM (prompt + screenshot + compressed hierarchy)
 *   4. Parse 12-tag response → extract <command>
 *   5. Validate command args (normalized 0-1000 coordinates)
 *   6. Map logical coords → device pixels
 *   7. Execute action + record locator info (RuntimeLocatorBuilder pattern)
 *   8. Wait for UI to stabilize
 *   9. Loop until DONE / FAIL / max turns / loop detected
 *
 * Recording:
 *   Every action is recorded as a RecordedStep with full locator info:
 *   - locator: {resource_id, text, content_desc, class, bounds_hint, locator_index, locator_count, fallback_point}
 *   - containerProbe: parent-level locator for fallback
 *   - tapPoint: [x, y] in device pixels
 *   - semanticNote: what the AI observed on this page
 *   - expected: what the AI expects after this action
 *
 * Script optimization (TaskMapAssembler pattern):
 *   - Filter out non-replayable ops (WAIT, HOME, etc.)
 *   - Validate TAP has locator or tapPoint
 *   - Validate SWIPE has valid args
 *   - Remove redundant steps (same locator consecutive TAPs)
 *
 * Replay (3-level fallback):
 *   locator → containerProbe → fallback_point (tapPoint)
 */
class DirectCoreBridge(private val context: Context) : CoreBridge {

    private val inputEngine = InputEngine()
    private val senseEngine = SenseEngine(context)
    private val taskScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + Dispatchers.IO
    )

    // LLM config — loaded from SettingsRepository when needed
    @Volatile private var llmApiUrl: String = ""
    @Volatile private var llmApiKey: String = ""
    @Volatile private var llmModel: String = "gpt-4o-mini"

    // Device info for coordinate mapping
    @Volatile private var deviceWidth: Int = 1080
    @Volatile private var deviceHeight: Int = 1920

    // Current task state reference (for vision mode tracking)
    @Volatile private var currentState: TaskState? = null

    private val visionClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // ===== Constants (AutoLXB aligned) =====
    companion object {
        private const val COORD_SPACE = 1000          // Normalized coordinate space [0, 1000]
        private const val MAX_VISION_TURNS = 30        // Max turns per VISION_ACT loop
        private const val MAX_VISION_ATTEMPTS = 3      // Retry budget per turn (parse + arg + exec)
        private const val UI_SETTLE_TIMEOUT_MS = 3000L
        private const val UI_SETTLE_SAMPLE_MS = 500L
        private const val UI_SETTLE_SIM_THRESHOLD = 0.90
        private const val UI_SETTLE_REQUIRED_HITS = 2
        private const val SWIPE_POST_WAIT_MS = 1500L
        private const val SAME_COMMAND_STREAK_LIMIT = 4
        private const val SAME_ACTIVITY_STREAK_LIMIT = 6
        private const val MAX_HIERARCHY_ELEMENTS = 80
        private const val MAX_CONSECUTIVE_LLM_FAILURES = 5  // Abort after N consecutive LLM failures
        private const val RATE_LIMIT_BASE_DELAY_MS = 10000L // Base delay for 429 rate limit
        private const val RETRY_BASE_DELAY_MS = 2000L       // Base delay between retries
    }

    override suspend fun getCoreStatus(): CoreStatusResult = withContext(Dispatchers.IO) {
        val mode = ShellExecutor.detectMode()
        when (mode) {
            ShellExecutor.ShellMode.ROOT,
            ShellExecutor.ShellMode.ADB,
            ShellExecutor.ShellMode.ADB_LOCAL -> CoreStatusResult.Running(port = 0, pid = android.os.Process.myPid())
            ShellExecutor.ShellMode.SH -> CoreStatusResult.ShellOnly()
            ShellExecutor.ShellMode.NONE -> CoreStatusResult.Stopped("需要 Root 权限或 ADB 调试")
        }
    }

    private val activeTasks = java.util.concurrent.ConcurrentHashMap<String, TaskState>()

    /**
     * Full execution state for a single task.
     * Mirrors AutoLXB CortexFsmEngine.Context.
     */
    data class TaskState(
        val taskId: String,
        var state: String = "running",
        var phase: String = "INIT",
        var detail: String = "",
        var currentStep: Int = 0,
        val executedSteps: MutableList<RecordedStep> = mutableListOf(),
        var visionTurns: Int = 0,
        var lastCommand: String = "",
        var sameCommandStreak: Int = 0,
        var lastActivitySig: String = "",
        var sameActivityStreak: Int = 0,
        // Vision history (AutoLXB pattern)
        val visionHistory: MutableList<VisionHistoryRow> = mutableListOf(),
        var pendingInstruction: String = "",
        var pendingExpected: String = "",
        var pendingCarryContext: String = "",
        val workingMemory: MutableList<String> = mutableListOf(),
        // Agent resilience
        var consecutiveLlmFailures: Int = 0,
        var visionMode: String = "auto",  // "auto" → try multimodal, fallback to text-only; "text_only" → hierarchy only
        // AI thinking for UI display
        var aiObserving: String = "",
        var aiThinking: String = "",
        var aiAction: String = "",
        var aiExpected: String = ""
    )

    data class VisionHistoryRow(
        val instruction: String,
        val expected: String,
        val actual: String,
        val judgePrev: String,
        val judgeGlobal: String,
        val carryContext: String
    )

    /**
     * A recorded step with full locator info for script replay.
     * Mirrors AutoLXB TaskRouteRecord.Action + TaskMap.Step structure.
     */
    data class RecordedStep(
        val op: String,           // TAP, SWIPE, INPUT, BACK, WAIT, LAUNCH
        val args: List<String>,   // operation arguments (in normalized 0-1000 coords for TAP/SWIPE)
        val summary: String,      // human-readable description
        val locator: Map<String, Any> = emptyMap(),   // resource_id, text, content_desc, class, bounds_hint, locator_index, locator_count, fallback_point
        val containerProbe: Map<String, String> = emptyMap(),  // parent-level locator
        val tapPoint: List<Int> = emptyList(),         // [x, y] in device pixels
        val swipe: Map<String, Any> = emptyMap(),      // start, end, duration_ms
        val success: Boolean = true,
        val semanticNote: String = "",   // what the AI observed (Ovserve_result)
        val expected: String = "",       // what the AI expects after this action
        val visionNote: String = ""      // raw observing text
    )

    override suspend fun submitQuickTask(taskPayload: String): TaskSubmitResult {
        val taskId = "task_${System.currentTimeMillis()}"
        val state = TaskState(taskId = taskId)
        activeTasks[taskId] = state
        try {
            val name = SafeJson.getString(taskPayload, "name") ?: "未命名任务"
            state.detail = "准备执行: $name"
            taskScope.launch {
                try { executeTaskAsync(taskId, taskPayload) }
                catch (e: Exception) {
                    DebugLog.e("DirectBridge", "Task $taskId failed: ${e.message}")
                    activeTasks[taskId]?.let { ts ->
                        ts.state = "failed"; ts.phase = "ERROR"; ts.detail = "执行异常: ${e.message}"
                    }
                }
            }
        } catch (e: Exception) {
            state.state = "failed"; state.detail = "提交失败: ${e.message}"
            return TaskSubmitResult.Error(CoreErrorCode.TASK_SUBMIT_FAILED, "提交失败: ${e.message}")
        }
        return TaskSubmitResult.Accepted(taskId = taskId, runId = taskId)
    }

    fun configureLlm(apiKey: String, baseUrl: String, model: String) {
        llmApiKey = apiKey
        llmApiUrl = LlmTaskSpecParser.normalizeApiUrl(baseUrl)
        llmModel = model
    }

    private suspend fun loadLlmConfig() {
        if (llmApiKey.isNotBlank()) return
        try {
            val settingsRepo = com.smarttasker.data.repository.SettingsRepository(context)
            llmApiUrl = LlmTaskSpecParser.normalizeApiUrl(settingsRepo.apiUrl.first())
            llmApiKey = settingsRepo.apiKey.first()
            llmModel = settingsRepo.modelName.first()
            DebugLog.i("DirectBridge", "LLM config loaded: url=$llmApiUrl model=$llmModel")
        } catch (e: Exception) {
            DebugLog.w("DirectBridge", "Failed to load LLM config: ${e.message}")
        }
    }

    /**
     * Detect device screen resolution for coordinate mapping.
     */
    private suspend fun detectDeviceResolution() {
        try {
            val result = ShellExecutor.exec("wm size")
            val output = when (result) {
                is ShellResult.Success -> result.output
                is ShellResult.Error -> return
            }
            val regex = Regex("""(\d+)x(\d+)""")
            val match = regex.find(output) ?: return
            deviceWidth = match.groupValues[1].toIntOrNull() ?: deviceWidth
            deviceHeight = match.groupValues[2].toIntOrNull() ?: deviceHeight
            DebugLog.i("DirectBridge", "Device resolution: ${deviceWidth}x${deviceHeight}")
        } catch (e: Exception) {
            DebugLog.w("DirectBridge", "Failed to detect resolution: ${e.message}")
        }
    }

    // ===== FSM Execution =====

    private suspend fun executeTaskAsync(taskId: String, taskPayload: String) {
        val state = activeTasks[taskId] ?: return
        currentState = state
        ShellExecutor.init(context)

        loadLlmConfig()
        if (llmApiKey.isBlank()) {
            state.state = "failed"; state.phase = "ERROR"
            state.detail = "未配置AI模型，请在设置中配置LLM"
            return
        }

        detectDeviceResolution()

        try {
            var packageName = SafeJson.getNestedString(taskPayload, "target_app", "package") ?: ""
            val appName = SafeJson.getNestedString(taskPayload, "target_app", "name") ?: ""
            val userTask = SafeJson.getString(taskPayload, "playbook")
                ?: SafeJson.getString(taskPayload, "description") ?: ""

            // Resolve package name from app name if not provided
            if (packageName.isEmpty() && appName.isNotEmpty()) {
                packageName = tryResolvePackage(appName)
                if (packageName.isNotEmpty()) {
                    DebugLog.i("DirectBridge", "Resolved package for '$appName': $packageName")
                }
            }

            // ===== INIT: Launch target app =====
            state.phase = "INIT"; state.detail = "启动应用: ${appName.ifEmpty { packageName }}"
            if (packageName.isNotEmpty()) {
                val launched = senseEngine.launchApp(packageName)
                if (!launched) {
                    ShellExecutor.exec("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
                }
                state.executedSteps.add(RecordedStep(
                    op = "LAUNCH",
                    args = listOf(packageName),
                    summary = "打开${appName.ifEmpty { packageName }}",
                    locator = mapOf("package" to packageName),
                    success = true
                ))
                state.currentStep = 1
                DebugLog.i("DirectBridge", "App launched: $packageName")
                waitForUiStable()
            }

            // ===== VISION_ACT loop =====
            state.phase = "VISION_ACT"; state.detail = "AI 正在操作手机..."

            for (turn in 1..MAX_VISION_TURNS) {
                if (state.state == "cancelled") return

                // Early termination: too many consecutive LLM failures
                if (state.consecutiveLlmFailures >= MAX_CONSECUTIVE_LLM_FAILURES) {
                    state.state = "failed"; state.phase = "ERROR"
                    state.detail = "AI 模型连续调用失败 ${state.consecutiveLlmFailures} 次，请检查网络和模型配置"
                    DebugLog.e("DirectBridge", "Aborting: $MAX_CONSECUTIVE_LLM_FAILURES consecutive LLM failures")
                    break
                }

                state.visionTurns = turn
                state.detail = "AI 分析第 $turn 轮..."

                // 1. Screenshot
                val screenshotBytes = when (val r = senseEngine.screenshot()) {
                    is ScreenshotResult.Success -> r.pngBytes
                    else -> null
                }

                // 2. Dump UI hierarchy
                val hierarchyXml = when (val r = senseEngine.dumpHierarchy()) {
                    is HierarchyResult.Success -> r.xml
                    else -> ""
                }

                // 3. Refresh activity signature for streak detection
                refreshActivitySignature(state, hierarchyXml)

                // 4. Build prompt and call LLM with retry budget
                val prompt = buildVisionPrompt(userTask, state, turn)
                val visionResult = executeVisionTurn(state, prompt, screenshotBytes, hierarchyXml)

                when (visionResult) {
                    is VisionTurnResult.DONE -> {
                        state.consecutiveLlmFailures = 0
                        state.phase = "completed"; state.state = "success"
                        state.detail = visionResult.summary.ifEmpty { "任务完成" }
                        DebugLog.i("DirectBridge", "Task $taskId DONE: ${visionResult.summary}")
                        break
                    }
                    is VisionTurnResult.FAIL -> {
                        state.consecutiveLlmFailures = 0
                        state.state = "failed"; state.phase = "ERROR"
                        state.detail = visionResult.reason.ifEmpty { "AI 判断任务无法完成" }
                        DebugLog.e("DirectBridge", "Task $taskId FAIL: ${visionResult.reason}")
                        break
                    }
                    is VisionTurnResult.LOOP_DETECTED -> {
                        state.consecutiveLlmFailures = 0
                        state.state = "failed"; state.phase = "ERROR"
                        state.detail = "AI 陷入操作循环，任务失败"
                        DebugLog.w("DirectBridge", "Loop detected: ${visionResult.detail}")
                        break
                    }
                    is VisionTurnResult.ACTIVITY_STUCK -> {
                        state.consecutiveLlmFailures = 0
                        state.state = "failed"; state.phase = "ERROR"
                        state.detail = "页面长时间无变化，任务失败"
                        DebugLog.w("DirectBridge", "Activity stuck detected")
                        break
                    }
                    is VisionTurnResult.EXECUTED -> {
                        state.consecutiveLlmFailures = 0
                        state.detail = "执行: ${visionResult.step.summary}"
                        state.executedSteps.add(visionResult.step)
                        state.currentStep = state.executedSteps.size
                        // Wait for UI to stabilize after successful action
                        if (visionResult.step.success) {
                            if (visionResult.step.op == "SWIPE") {
                                delay(SWIPE_POST_WAIT_MS)
                            }
                            waitForUiStable()
                        } else {
                            delay(1000)
                        }
                    }
                    is VisionTurnResult.RETRY_EXHAUSTED -> {
                        state.consecutiveLlmFailures++
                        DebugLog.w("DirectBridge", "Vision turn $turn retry exhausted (consecutive failures: ${state.consecutiveLlmFailures})")
                        // Exponential backoff between turns on failure
                        val backoffMs = RETRY_BASE_DELAY_MS * (1L shl (state.consecutiveLlmFailures - 1).coerceAtMost(4))
                        DebugLog.i("DirectBridge", "Backing off ${backoffMs}ms before next turn")
                        delay(backoffMs)
                    }
                }
            }

            // Check if we hit the turn limit
            if (state.state == "running") {
                state.state = "failed"; state.phase = "ERROR"
                state.detail = "AI 操作轮次达到上限 ($MAX_VISION_TURNS)"
            }

            // Bring SmartTasker back to foreground
            bringAppToForeground()

        } catch (e: Exception) {
            state.state = "failed"; state.phase = "ERROR"; state.detail = "执行异常: ${e.message}"
            DebugLog.e("DirectBridge", "Task $taskId exception: ${e.message}")
        }
    }

    // ===== Vision Turn Execution (with retry budget) =====

    sealed class VisionTurnResult {
        data class DONE(val summary: String) : VisionTurnResult()
        data class FAIL(val reason: String) : VisionTurnResult()
        data class LOOP_DETECTED(val detail: String) : VisionTurnResult()
        data object ACTIVITY_STUCK : VisionTurnResult()
        data class EXECUTED(val step: RecordedStep) : VisionTurnResult()
        data object RETRY_EXHAUSTED : VisionTurnResult()
    }

    /**
     * Execute a single VISION_ACT turn with retry budget.
     * Parse failures + arg validation failures + exec failures share the same budget (3 attempts).
     * Mirrors AutoLXB's VISION_ACT retry logic.
     */
    private suspend fun executeVisionTurn(
        state: TaskState,
        prompt: String,
        screenshotBytes: ByteArray?,
        hierarchyXml: String
    ): VisionTurnResult {
        var retryReason = ""

        for (attempt in 1..MAX_VISION_ATTEMPTS) {
            // Exponential backoff between retries within a turn
            if (attempt > 1) {
                val retryDelay = RETRY_BASE_DELAY_MS * (1L shl (attempt - 2))
                DebugLog.i("DirectBridge", "Retry attempt $attempt, waiting ${retryDelay}ms")
                delay(retryDelay)
            }

            val attemptPrompt = if (attempt > 1) {
                "$prompt\n\n[RETRY_CONTEXT]\nPrevious attempt failed. reason: $retryReason\nFix the output and return exactly the required 12 tags in order.\nIn <command>, output one valid command line with strict argument format.\n"
            } else {
                prompt
            }

            // Call LLM
            val llmResponse = callLlmVision(attemptPrompt, screenshotBytes, hierarchyXml)
            if (llmResponse == null) {
                retryReason = "LLM call failed"
                DebugLog.e("DirectBridge", "LLM call failed on turn ${state.visionTurns} attempt $attempt")
                continue
            }

            DebugLog.i("DirectBridge", "Vision turn ${state.visionTurns} attempt $attempt: ${llmResponse.take(200)}")

            // Parse 12-tag response
            val parsed = parseVisionResponse12(llmResponse)
            if (parsed == null) {
                retryReason = "Failed to parse 12-tag response"
                DebugLog.w("DirectBridge", "Parse failed on attempt $attempt")
                continue
            }

            // Update vision history (AutoLXB pattern)
            updateVisionHistory(state, parsed)

            // Update AI thinking for UI display
            state.aiObserving = parsed.observing.take(200)
            state.aiThinking = parsed.thinking.take(200)
            state.aiAction = parsed.action.take(100)
            state.aiExpected = parsed.expected.take(100)
            state.detail = "AI: ${parsed.action.take(50)}"

            // Update working memory
            if (parsed.memoryWrite.isNotBlank() && parsed.memoryWrite != "none") {
                state.workingMemory.add(parsed.memoryWrite)
                if (state.workingMemory.size > 10) state.workingMemory.removeAt(0)
            }

            // Stash pending for next turn
            state.pendingInstruction = parsed.action
            state.pendingExpected = parsed.expected
            state.pendingCarryContext = parsed.carryContext

            // Check for completion
            if (parsed.op == "DONE") {
                return VisionTurnResult.DONE(parsed.summary)
            }
            if (parsed.op == "FAIL") {
                return VisionTurnResult.FAIL(parsed.summary)
            }

            // Validate command args (AutoLXB strict validation)
            val argError = validateCommandArgs(parsed)
            if (argError != null) {
                retryReason = argError
                DebugLog.w("DirectBridge", "Arg validation failed: $argError on attempt $attempt")
                continue
            }

            // Track command streak (loop detection)
            val currentCmd = "${parsed.op} ${parsed.args}"
            if (currentCmd == state.lastCommand) {
                state.sameCommandStreak++
            } else {
                state.sameCommandStreak = 1
            }
            state.lastCommand = currentCmd

            if (state.sameCommandStreak >= SAME_COMMAND_STREAK_LIMIT) {
                return VisionTurnResult.LOOP_DETECTED(currentCmd)
            }

            // Activity streak detection
            if (state.sameActivityStreak >= SAME_ACTIVITY_STREAK_LIMIT) {
                return VisionTurnResult.ACTIVITY_STUCK
            }

            // Execute the action
            val step = executeVisionAction(parsed, hierarchyXml)

            if (!step.success) {
                DebugLog.w("DirectBridge", "Action failed on attempt $attempt: ${parsed.op}")
                return VisionTurnResult.EXECUTED(step)
            }

            return VisionTurnResult.EXECUTED(step)
        }

        return VisionTurnResult.RETRY_EXHAUSTED
    }

    // ===== 12-Tag Vision Prompt (AutoLXB compatible) =====

    private fun buildVisionPrompt(userTask: String, state: TaskState, @Suppress("UNUSED_PARAMETER") turn: Int): String {
        val sb = StringBuilder()
        sb.appendLine("You are a mobile UI agent. Focus on completing the current objective safely.")
        sb.appendLine()

        // [TASK_BLOCK]
        sb.appendLine("[TASK_BLOCK]")
        sb.appendLine("Current objective: $userTask")
        sb.appendLine("Mode: single")
        sb.appendLine("Completion condition: Complete the current objective with clear visible evidence.")
        sb.appendLine()

        // [RECENT_HISTORY_BLOCK]
        sb.appendLine("[RECENT_HISTORY_BLOCK]")
        if (state.visionHistory.isEmpty() && state.pendingInstruction.isEmpty()) {
            sb.appendLine("Recent turns: none")
        } else {
            sb.appendLine("Recent turns (oldest -> newest):")
            state.visionHistory.takeLast(5).forEachIndexed { i, row ->
                sb.appendLine("${i + 1}) action: ${row.instruction}")
                sb.appendLine("   expected: ${row.expected}")
                sb.appendLine("   actual: ${row.actual}")
                sb.appendLine("   judge_prev: ${row.judgePrev}")
                sb.appendLine("   judge_global: ${row.judgeGlobal}")
            }
            if (state.pendingInstruction.isNotEmpty()) {
                val idx = state.visionHistory.size + 1
                sb.appendLine("$idx) action: ${state.pendingInstruction}")
                sb.appendLine("   expected: ${state.pendingExpected}")
                sb.appendLine("   actual: pending - observe actual result in this turn")
                sb.appendLine("   judge_prev: pending - evaluate previous action outcome in this turn")
                sb.appendLine("   judge_global: pending - evaluate global progress in this turn")
            }
        }
        sb.appendLine("Recent-history guidance:")
        sb.appendLine("- judge_prev checks only previous action vs previous expected result.")
        sb.appendLine("- judge_global checks whether the overall objective is converging or drifting.")
        sb.appendLine("- Do not repeat actions that already failed with no_effect/wrong_target.")
        sb.appendLine("- If repeated no progress, change action strategy.")
        sb.appendLine()

        // [WORKING_MEMORY_BLOCK]
        sb.appendLine("[WORKING_MEMORY_BLOCK]")
        if (state.workingMemory.isEmpty()) {
            sb.appendLine("Working memory: none")
        } else {
            sb.appendLine("Durable facts from earlier turns (oldest -> newest):")
            state.workingMemory.forEach { sb.appendLine("- $it") }
        }
        sb.appendLine()

        // [ACTION_BLOCK]
        sb.appendLine("[ACTION_BLOCK]")
        sb.appendLine("Available actions:")
        sb.appendLine("- TAP x y")
        sb.appendLine("- SWIPE x1 y1 x2 y2 duration_ms")
        sb.appendLine("- INPUT \"text\"")
        sb.appendLine("- WAIT ms")
        sb.appendLine("- BACK")
        sb.appendLine("- DONE summary_text")
        sb.appendLine("- FAIL reason_text")
        sb.appendLine("Action semantics and parameters:")
        sb.appendLine("1) TAP x y: tap one target point. x/y are coordinates.")
        sb.appendLine("2) SWIPE x1 y1 x2 y2 duration_ms: drag to scroll/reveal more content.")
        sb.appendLine("3) INPUT \"text\": input text into focused input field.")
        sb.appendLine("4) WAIT ms: wait for UI/network transition.")
        sb.appendLine("5) BACK: press Android back once.")
        sb.appendLine("6) DONE summary_text: terminate current objective with concise summary.")
        sb.appendLine("7) FAIL reason_text: report that the task cannot be completed.")
        sb.appendLine()
        sb.appendLine("Coordinate convention (required for TAP/SWIPE):")
        sb.appendLine("- Use normalized coordinates in a ${COORD_SPACE}x${COORD_SPACE} logical plane.")
        sb.appendLine("- Top-left is (0,0); bottom-right is (${COORD_SPACE},${COORD_SPACE}).")
        sb.appendLine("- All TAP/SWIPE coordinates should be integers in [0,${COORD_SPACE}].")
        sb.appendLine("- Do NOT output device pixel coordinates.")
        sb.appendLine("- Numeric tokens must be pure digits only (0-9), with no comma, no period, no unit, no parentheses.")
        sb.appendLine("- Use ASCII half-width spaces to separate tokens.")
        sb.appendLine("- Valid examples: TAP 373 947 ; SWIPE 500 820 500 220 450")
        sb.appendLine("- Invalid examples: TAP 373, 947 ; TAP (373,947) ; SWIPE 500 820 500 220 450ms")
        sb.appendLine()
        sb.appendLine("Constraints:")
        sb.appendLine("- One turn chooses one primary action.")
        sb.appendLine("- Do not BACK immediately just because one expected control is not visible.")
        sb.appendLine("- If UI seems to be the expected page but target control is missing, first use SWIPE to explore more of the same page.")
        sb.appendLine("- Before BACK in such cases, try 1-3 SWIPEs (typically upward to reveal lower content; if already at bottom, try downward).")
        sb.appendLine("- Only BACK after swipe-based exploration still cannot find expected control.")
        sb.appendLine("- If repeated no progress, change action type/intent.")
        sb.appendLine("- Prefer safe, incremental exploration before failure.")
        sb.appendLine()

        // [SCREENSHOT_BLOCK]
        sb.appendLine("[SCREENSHOT_BLOCK]")
        sb.appendLine("Screenshot: attached")
        sb.appendLine()

        // [OUTPUT_BLOCK] — 12 tags (AutoLXB compatible)
        sb.appendLine("[OUTPUT_BLOCK]")
        sb.appendLine("You MUST output exactly the following tags in this exact order:")
        sb.appendLine("<Observing>...</Observing>")
        sb.appendLine("<Ovserve_result>...</Ovserve_result>")
        sb.appendLine("<Judging_prev>...</Judging_prev>")
        sb.appendLine("<Judge_prev_result>...</Judge_prev_result>")
        sb.appendLine("<Judging_global>...</Judging_global>")
        sb.appendLine("<Judge_global_result>...</Judge_global_result>")
        sb.appendLine("<Thinking>...</Thinking>")
        sb.appendLine("<action>...</action>")
        sb.appendLine("<expected>...</expected>")
        sb.appendLine("<carry_context>...</carry_context>")
        sb.appendLine("<memory_write>...</memory_write>")
        sb.appendLine("<command>...</command>")
        sb.appendLine("Field meaning:")
        sb.appendLine("- <Observing>: describe what is currently visible and relevant to the current objective.")
        sb.appendLine("- <Ovserve_result>: one-sentence summary of current page/result; used as actual outcome.")
        sb.appendLine("- <Judging_prev>: evaluate previous action vs previous expected result; explain mismatch cause if any.")
        sb.appendLine("- <Judge_prev_result>: one-sentence verdict for previous action (match/mismatch and why).")
        sb.appendLine("- <Judging_global>: evaluate global progress toward current objective; detect drift/repetition/unfinished key requirements.")
        sb.appendLine("- <Judge_global_result>: one-sentence verdict for global progress (on_track/stuck/drifting and why).")
        sb.appendLine("- <Thinking>: analyze current situation and decide next strategy.")
        sb.appendLine("- <action>: one short natural-language next action intent.")
        sb.appendLine("- <expected>: expected result after next action.")
        sb.appendLine("- <carry_context>: short-term notes for immediate next turns; use 'none' if not needed.")
        sb.appendLine("- <memory_write>: one durable fact to store for later turns (long-context tasks); use 'none' if no new durable fact.")
        sb.appendLine("- <command>: executable command string.")
        sb.appendLine("Command format strictness:")
        sb.appendLine("- <command> must contain exactly one command line and nothing else.")
        sb.appendLine("- Allowed command signatures only:")
        sb.appendLine("  TAP x y")
        sb.appendLine("  SWIPE x1 y1 x2 y2 duration_ms")
        sb.appendLine("  INPUT \"text\"")
        sb.appendLine("  WAIT ms")
        sb.appendLine("  BACK")
        sb.appendLine("  DONE summary_text")
        sb.appendLine("  FAIL reason_text")
        sb.appendLine("- Before output, self-check that numeric args are digits-only tokens with no punctuation.")
        sb.appendLine("Special first-turn rule:")
        sb.appendLine("- If there is no previous action/history, output:")
        sb.appendLine("  <Judging_prev>none</Judging_prev>")
        sb.appendLine("  <Judge_prev_result>none</Judge_prev_result>")
        sb.appendLine("- If global progress cannot be judged yet, output:")
        sb.appendLine("  <Judging_global>none</Judging_global>")
        sb.appendLine("  <Judge_global_result>none</Judge_global_result>")
        sb.appendLine("Do not output markdown, code fences, JSON, or any extra text outside these tags.")

        return sb.toString()
    }

    // ===== LLM Vision Call =====

    /**
     * Call LLM with vision capabilities.
     * Automatically falls back to text-only mode if multimodal is not supported (HTTP 404).
     * Handles rate limiting (HTTP 429) with exponential backoff.
     */
    private suspend fun callLlmVision(prompt: String, screenshotBytes: ByteArray?, hierarchyXml: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val messages = JSONArray()

                // System message
                messages.put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are a mobile UI agent. Focus on completing the current objective safely. Output exactly the required 12 tags in order.")
                })

                // Decide whether to use multimodal or text-only based on visionMode
                val useMultimodal = screenshotBytes != null && screenshotBytes.size > 100 && currentState?.visionMode != "text_only"

                // User message
                val userContent = JSONArray()

                // Add text prompt
                userContent.put(JSONObject().apply {
                    put("type", "text")
                    put("text", prompt)
                })

                // Add UI hierarchy as text
                if (hierarchyXml.isNotBlank()) {
                    val compressedHierarchy = compressHierarchy(hierarchyXml)
                    userContent.put(JSONObject().apply {
                        put("type", "text")
                        put("text", "[UI层级]\n$compressedHierarchy")
                    })
                }

                // Add screenshot as image only if multimodal mode
                if (useMultimodal) {
                    try {
                        val base64 = Base64.encodeToString(screenshotBytes!!, Base64.NO_WRAP)
                        userContent.put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", "data:image/png;base64,$base64")
                            })
                        })
                    } catch (e: Exception) {
                        DebugLog.w("DirectBridge", "Failed to attach screenshot: ${e.message}")
                    }
                }

                messages.put(JSONObject().apply {
                    put("role", "user")
                    put("content", userContent)
                })

                val requestBody = JSONObject().apply {
                    put("model", llmModel)
                    put("messages", messages)
                    put("temperature", 0.1)
                    put("max_tokens", 1500)
                }

                DebugLog.i("DirectBridge", "Calling LLM: url=$llmApiUrl model=$llmModel multimodal=$useMultimodal")

                val request = Request.Builder()
                    .url(llmApiUrl)
                    .addHeader("Authorization", "Bearer $llmApiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = visionClient.newCall(request).execute()
                val httpCode = response.code

                // Handle 429 Rate Limiting with retry
                if (httpCode == 429) {
                    val retryAfter = response.header("Retry-After")?.toLongOrNull()
                    val waitMs = retryAfter?.times(1000) ?: RATE_LIMIT_BASE_DELAY_MS
                    DebugLog.w("DirectBridge", "Rate limited (429), waiting ${waitMs}ms")
                    response.body?.close()
                    delay(waitMs)
                    // Retry once after waiting
                    val retryResponse = visionClient.newCall(request).execute()
                    val retryBody = retryResponse.body?.string()
                    if (!retryResponse.isSuccessful || retryBody.isNullOrBlank()) {
                        DebugLog.e("DirectBridge", "LLM retry after 429 also failed: HTTP ${retryResponse.code}")
                        return@withContext null
                    }
                    return@withContext extractContentFromResponse(retryBody)
                }

                // Handle 404 — model doesn't support multimodal, fallback to text-only
                if (httpCode == 404 && useMultimodal) {
                    DebugLog.w("DirectBridge", "HTTP 404 with multimodal — falling back to text-only mode")
                    response.body?.close()
                    currentState?.visionMode = "text_only"
                    // Retry without screenshot
                    return@withContext callLlmVisionTextOnly(prompt, hierarchyXml)
                }

                // Handle 404 in text-only mode — wrong URL or model
                if (httpCode == 404) {
                    val errorBody = response.body?.string() ?: ""
                    DebugLog.e("DirectBridge", "HTTP 404 — URL or model not found: $llmApiUrl model=$llmModel body=${errorBody.take(200)}")
                    return@withContext null
                }

                // Handle 400 — Bad Request (might be model doesn't support image format)
                if (httpCode == 400 && useMultimodal) {
                    val errorBody = response.body?.string() ?: ""
                    DebugLog.w("DirectBridge", "HTTP 400 with multimodal — falling back to text-only. Body: ${errorBody.take(200)}")
                    currentState?.visionMode = "text_only"
                    return@withContext callLlmVisionTextOnly(prompt, hierarchyXml)
                }

                val responseBody = response.body?.string()
                if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                    DebugLog.e("DirectBridge", "LLM vision call failed: HTTP $httpCode")
                    return@withContext null
                }

                extractContentFromResponse(responseBody)
            } catch (e: Exception) {
                DebugLog.e("DirectBridge", "LLM vision call error: ${e.message}")
                null
            }
        }
    }

    /**
     * Text-only LLM call (no screenshot, hierarchy only).
     * Used as fallback when the model doesn't support multimodal input.
     */
    private suspend fun callLlmVisionTextOnly(prompt: String, hierarchyXml: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val messages = JSONArray()
                messages.put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are a mobile UI agent. Focus on completing the current objective safely. Output exactly the required 12 tags in order. You are operating in text-only mode — rely on the UI hierarchy data to understand the screen layout.")
                })

                val userContent = JSONArray()
                userContent.put(JSONObject().apply {
                    put("type", "text")
                    put("text", prompt)
                })

                if (hierarchyXml.isNotBlank()) {
                    val compressedHierarchy = compressHierarchy(hierarchyXml)
                    userContent.put(JSONObject().apply {
                        put("type", "text")
                        put("text", "[UI层级]\n$compressedHierarchy")
                    })
                }

                messages.put(JSONObject().apply {
                    put("role", "user")
                    put("content", userContent)
                })

                val requestBody = JSONObject().apply {
                    put("model", llmModel)
                    put("messages", messages)
                    put("temperature", 0.1)
                    put("max_tokens", 1500)
                }

                DebugLog.i("DirectBridge", "Calling LLM text-only: url=$llmApiUrl model=$llmModel")

                val request = Request.Builder()
                    .url(llmApiUrl)
                    .addHeader("Authorization", "Bearer $llmApiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = visionClient.newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                    DebugLog.e("DirectBridge", "LLM text-only call failed: HTTP ${response.code}")
                    return@withContext null
                }

                extractContentFromResponse(responseBody)
            } catch (e: Exception) {
                DebugLog.e("DirectBridge", "LLM text-only call error: ${e.message}")
                null
            }
        }
    }

    private fun extractContentFromResponse(responseBody: String): String? {
        val json = JSONObject(responseBody)
        val choices = json.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            return choices.getJSONObject(0)
                .optJSONObject("message")?.optString("content", null)
        }
        return null
    }

    // ===== Hierarchy Compression =====

    private fun compressHierarchy(xml: String): String {
        val sb = StringBuilder()
        val nodePattern = Regex("""<node\s[^>]*?>""")
        val boundsPattern = Regex("""bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"""")
        val textPattern = Regex("""text="([^"]*?)"""")
        val descPattern = Regex("""content-desc="([^"]*?)"""")
        val idPattern = Regex("""resource-id="([^"]*?)"""")
        val classPattern = Regex("""class="([^"]*?)"""")
        val clickablePattern = Regex("""clickable="true"""")
        val focusablePattern = Regex("""focusable="true"""")
        val checkablePattern = Regex("""checkable="true"""")
        val scrollablePattern = Regex("""scrollable="true"""")

        var count = 0
        for (nodeMatch in nodePattern.findAll(xml)) {
            val nodeStr = nodeMatch.value
            val text = textPattern.find(nodeStr)?.groupValues?.get(1) ?: ""
            val desc = descPattern.find(nodeStr)?.groupValues?.get(1) ?: ""
            val id = idPattern.find(nodeStr)?.groupValues?.get(1) ?: ""
            val className = classPattern.find(nodeStr)?.groupValues?.get(1) ?: ""
            val isClickable = clickablePattern.containsMatchIn(nodeStr)
            val isFocusable = focusablePattern.containsMatchIn(nodeStr)
            val isCheckable = checkablePattern.containsMatchIn(nodeStr)
            val isScrollable = scrollablePattern.containsMatchIn(nodeStr)

            // Only include elements with useful info
            if (text.isBlank() && desc.isBlank() && id.isBlank() && !isClickable && !isFocusable && !isScrollable) continue

            val boundsMatch = boundsPattern.find(nodeStr) ?: continue
            val x1 = boundsMatch.groupValues[1].toInt()
            val y1 = boundsMatch.groupValues[2].toInt()
            val x2 = boundsMatch.groupValues[3].toInt()
            val y2 = boundsMatch.groupValues[4].toInt()

            // Convert to normalized coordinates for the LLM
            val nx1 = (x1 * COORD_SPACE / deviceWidth).coerceIn(0, COORD_SPACE)
            val ny1 = (y1 * COORD_SPACE / deviceHeight).coerceIn(0, COORD_SPACE)
            val nx2 = (x2 * COORD_SPACE / deviceWidth).coerceIn(0, COORD_SPACE)
            val ny2 = (y2 * COORD_SPACE / deviceHeight).coerceIn(0, COORD_SPACE)
            val ncx = (nx1 + nx2) / 2
            val ncy = (ny1 + ny2) / 2

            val attrs = mutableListOf<String>()
            if (text.isNotBlank()) attrs.add("text=\"$text\"")
            if (desc.isNotBlank()) attrs.add("desc=\"$desc\"")
            if (id.isNotBlank()) attrs.add("id=\"$id\"")
            if (isClickable) attrs.add("clickable")
            if (isFocusable) attrs.add("focusable")
            if (isCheckable) attrs.add("checkable")
            if (isScrollable) attrs.add("scrollable")
            if (className.isNotBlank()) attrs.add("class=\"$className\"")

            sb.appendLine("[$ncx,$ncy] ${attrs.joinToString(" ")}")
            count++
            if (count >= MAX_HIERARCHY_ELEMENTS) {
                sb.appendLine("... (更多元素省略)")
                break
            }
        }
        return sb.toString()
    }

    // ===== 12-Tag Response Parsing =====

    data class ParsedVisionResponse(
        val observing: String,
        val observeResult: String,
        val judgingPrev: String,
        val judgePrevResult: String,
        val judgingGlobal: String,
        val judgeGlobalResult: String,
        val thinking: String,
        val action: String,
        val expected: String,
        val carryContext: String,
        val memoryWrite: String,
        val command: String,
        // Parsed command
        val op: String,
        val args: List<String>,
        val summary: String
    )

    private fun parseVisionResponse12(response: String): ParsedVisionResponse? {
        try {
            val observing = extractTagText(response, "Observing")
            val observeResult = extractTagText(response, "Ovserve_result")
            val judgingPrev = extractTagText(response, "Judging_prev")
                .ifEmpty { extractTagText(response, "Judging") }
            val judgePrevResult = extractTagText(response, "Judge_prev_result")
                .ifEmpty { extractTagText(response, "Judge_result") }
            val judgingGlobal = extractTagText(response, "Judging_global")
                .ifEmpty { judgingPrev }
            val judgeGlobalResult = extractTagText(response, "Judge_global_result")
                .ifEmpty { judgePrevResult }
            val thinking = extractTagText(response, "Thinking")
            val action = extractTagText(response, "action")
            val expected = extractTagText(response, "expected")
            val carryContext = extractTagText(response, "carry_context")
            val memoryWrite = extractTagText(response, "memory_write")
            val command = extractTagText(response, "command")

            if (command.isBlank()) {
                DebugLog.w("DirectBridge", "Missing <command> tag in response")
                return null
            }

            val cmdTrimmed = command.trim()
            val parts = shellSplit(cmdTrimmed)
            if (parts.isEmpty()) return null

            val op = parts[0].uppercase()
            val args = parts.drop(1)

            val summary = when (op) {
                "DONE" -> args.joinToString(" ").ifEmpty { "任务完成" }
                "FAIL" -> args.joinToString(" ").ifEmpty { "任务失败" }
                else -> action.ifEmpty { "$op ${args.joinToString(" ")}" }
            }

            return ParsedVisionResponse(
                observing = observing,
                observeResult = observeResult,
                judgingPrev = judgingPrev,
                judgePrevResult = judgePrevResult,
                judgingGlobal = judgingGlobal,
                judgeGlobalResult = judgeGlobalResult,
                thinking = thinking,
                action = action,
                expected = expected,
                carryContext = carryContext,
                memoryWrite = memoryWrite,
                command = command,
                op = op,
                args = args,
                summary = summary
            )
        } catch (e: Exception) {
            DebugLog.e("DirectBridge", "Parse 12-tag response error: ${e.message}")
            return null
        }
    }

    /**
     * Shell-style split that respects quoted strings.
     * Mirrors AutoLXB VisionCommandParser.shellSplit.
     */
    private fun shellSplit(line: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var quoteChar = 0.toChar()

        for (c in line) {
            if (inQuotes) {
                if (c == quoteChar) {
                    inQuotes = false
                } else {
                    current.append(c)
                }
            } else {
                when {
                    c == '\'' || c == '"' -> {
                        inQuotes = true
                        quoteChar = c
                    }
                    c.isWhitespace() -> {
                        if (current.isNotEmpty()) {
                            out.add(current.toString())
                            current.clear()
                        }
                    }
                    else -> current.append(c)
                }
            }
        }
        if (current.isNotEmpty()) out.add(current.toString())
        return out
    }

    private fun extractTagText(text: String, tag: String): String {
        if (text.isEmpty() || tag.isEmpty()) return ""
        val safeTag = Regex.escape(tag)
        // Strict: <tag>content</tag>
        val strict = Regex("(?is)<\\s*$safeTag\\s*>\\s*([\\s\\S]*?)\\s*</\\s*$safeTag\\s*>")
        val m = strict.find(text)
        if (m != null) return m.groupValues[1].trim()
        // Fallback: <tag>content until next tag or end
        val openOnly = Regex("(?is)<\\s*$safeTag\\s*>\\s*([\\s\\S]*?)(?=<\\s*[A-Za-z_][A-Za-z0-9_]*\\s*>|$)")
        val m2 = openOnly.find(text)
        return m2?.groupValues?.get(1)?.trim() ?: ""
    }

    // ===== Command Arg Validation (AutoLXB strict) =====

    private fun validateCommandArgs(parsed: ParsedVisionResponse): String? {
        when (parsed.op) {
            "TAP" -> {
                if (parsed.args.size != 2) return "TAP expects 2 args, got ${parsed.args.size}"
                val x = parsed.args[0].toIntOrNull() ?: return "TAP args must be digits-only integers"
                val y = parsed.args[1].toIntOrNull() ?: return "TAP args must be digits-only integers"
                if (x < 0 || x > COORD_SPACE || y < 0 || y > COORD_SPACE)
                    return "TAP args out of range [0,$COORD_SPACE]"
            }
            "SWIPE" -> {
                if (parsed.args.size != 5) return "SWIPE expects 5 args, got ${parsed.args.size}"
                for (i in 0..3) {
                    val v = parsed.args[i].toIntOrNull() ?: return "SWIPE coord args must be digits-only integers"
                    if (v < 0 || v > COORD_SPACE) return "SWIPE coord args out of range [0,$COORD_SPACE]"
                }
                parsed.args[4].toIntOrNull() ?: return "SWIPE duration must be digits-only integer"
            }
            "INPUT" -> {
                if (parsed.args.isEmpty()) return "INPUT expects at least 1 arg"
            }
            "WAIT" -> {
                if (parsed.args.size != 1) return "WAIT expects 1 arg"
                parsed.args[0].toIntOrNull() ?: return "WAIT arg must be digits-only integer"
            }
            "BACK" -> {
                if (parsed.args.isNotEmpty()) return "BACK expects 0 args"
            }
            "DONE", "FAIL" -> { /* any args allowed */ }
            else -> return "Unknown command: ${parsed.op}"
        }
        return null // valid
    }

    // ===== Coordinate Mapping (AutoLXB normalized → device pixels) =====

    /**
     * Map normalized [0, 1000] coordinates to device pixel coordinates.
     * Mirrors AutoLXB mapPointByProbe.
     */
    private fun mapPoint(xf: Int, yf: Int): IntArray {
        val rx = ((xf.toDouble() / COORD_SPACE) * (deviceWidth - 1)).toInt()
            .coerceIn(0, deviceWidth - 1)
        val ry = ((yf.toDouble() / COORD_SPACE) * (deviceHeight - 1)).toInt()
            .coerceIn(0, deviceHeight - 1)
        return intArrayOf(rx, ry)
    }

    // ===== Vision History (AutoLXB pattern) =====

    private fun updateVisionHistory(state: TaskState, parsed: ParsedVisionResponse) {
        // Match previous pending instruction with current actual/judgement
        if (state.pendingInstruction.isNotEmpty() || state.pendingExpected.isNotEmpty()) {
            val actual = if (parsed.observeResult.isNotBlank()) parsed.observeResult else parsed.observing
            val jp = if (parsed.judgePrevResult.isNotBlank()) parsed.judgePrevResult else "unknown"
            val jg = if (parsed.judgeGlobalResult.isNotBlank()) parsed.judgeGlobalResult else jp

            state.visionHistory.add(VisionHistoryRow(
                instruction = state.pendingInstruction,
                expected = state.pendingExpected,
                actual = actual,
                judgePrev = jp,
                judgeGlobal = jg,
                carryContext = state.pendingCarryContext
            ))
            // Keep only last 10 entries
            while (state.visionHistory.size > 10) state.visionHistory.removeAt(0)
        }
    }

    // ===== Activity Signature Tracking =====

    private fun refreshActivitySignature(state: TaskState, hierarchyXml: String) {
        // Simple signature: count of nodes + hash of text content
        val nodeCount = hierarchyXml.lines().count { it.trim().startsWith("<node") }
        val sig = nodeCount.toString()
        if (sig == state.lastActivitySig && sig != "0") {
            state.sameActivityStreak++
        } else {
            state.sameActivityStreak = 0
        }
        state.lastActivitySig = sig
    }

    // ===== Vision Action Execution =====

    private suspend fun executeVisionAction(parsed: ParsedVisionResponse, hierarchyXml: String): RecordedStep {
        return try {
            when (parsed.op) {
                "TAP" -> {
                    val nx = parsed.args[0].toInt()
                    val ny = parsed.args[1].toInt()
                    val mapped = mapPoint(nx, ny)
                    val x = mapped[0]
                    val y = mapped[1]

                    // Build locator (RuntimeLocatorBuilder pattern)
                    val locator = buildLocator(x, y, hierarchyXml)
                    val containerProbe = buildContainerProbe(x, y, hierarchyXml)

                    val ok = inputEngine.tap(x, y)
                    delay(300)
                    RecordedStep(
                        op = "TAP",
                        args = listOf(nx.toString(), ny.toString()),  // Store normalized coords
                        summary = parsed.action.ifEmpty { "点击 ($nx, $ny)" },
                        locator = locator,
                        containerProbe = containerProbe,
                        tapPoint = listOf(x, y),  // Device pixels for fallback
                        success = ok,
                        semanticNote = parsed.observeResult,
                        expected = parsed.expected,
                        visionNote = parsed.observing
                    )
                }
                "SWIPE" -> {
                    val nx1 = parsed.args[0].toInt()
                    val ny1 = parsed.args[1].toInt()
                    val nx2 = parsed.args[2].toInt()
                    val ny2 = parsed.args[3].toInt()
                    val dur = parsed.args[4].toIntOrNull() ?: 600

                    val p1 = mapPoint(nx1, ny1)
                    val p2 = mapPoint(nx2, ny2)

                    val ok = inputEngine.swipe(p1[0], p1[1], p2[0], p2[1], dur)
                    delay(500)
                    RecordedStep(
                        op = "SWIPE",
                        args = listOf(nx1.toString(), ny1.toString(), nx2.toString(), ny2.toString(), dur.toString()),
                        summary = parsed.action.ifEmpty { "滑动" },
                        swipe = mapOf(
                            "start" to listOf(p1[0], p1[1]),
                            "end" to listOf(p2[0], p2[1]),
                            "duration_ms" to dur
                        ),
                        success = ok,
                        semanticNote = parsed.observeResult,
                        expected = parsed.expected,
                        visionNote = parsed.observing
                    )
                }
                "INPUT" -> {
                    val text = parsed.args.joinToString(" ")
                    val ok = inputEngine.inputText(text)
                    delay(500)
                    RecordedStep(
                        op = "INPUT",
                        args = listOf(text),
                        summary = parsed.action.ifEmpty { "输入「$text」" },
                        success = ok,
                        semanticNote = parsed.observeResult,
                        expected = parsed.expected,
                        visionNote = parsed.observing
                    )
                }
                "BACK" -> {
                    val ok = inputEngine.pressBack()
                    delay(300)
                    RecordedStep(
                        op = "BACK",
                        args = emptyList(),
                        summary = parsed.action.ifEmpty { "按返回键" },
                        success = ok,
                        semanticNote = parsed.observeResult,
                        expected = parsed.expected,
                        visionNote = parsed.observing
                    )
                }
                "WAIT" -> {
                    val ms = parsed.args.firstOrNull()?.toLongOrNull() ?: 1000L
                    delay(ms)
                    RecordedStep(
                        op = "WAIT",
                        args = listOf(ms.toString()),
                        summary = parsed.action.ifEmpty { "等待" },
                        success = true,
                        semanticNote = parsed.observeResult,
                        expected = parsed.expected,
                        visionNote = parsed.observing
                    )
                }
                else -> {
                    RecordedStep(
                        op = parsed.op,
                        args = parsed.args,
                        summary = parsed.action,
                        success = false,
                        visionNote = "未知操作: ${parsed.op}"
                    )
                }
            }
        } catch (e: Exception) {
            DebugLog.e("DirectBridge", "Vision action failed: ${parsed.op} - ${e.message}")
            RecordedStep(
                op = parsed.op,
                args = parsed.args,
                summary = parsed.action,
                success = false,
                visionNote = "执行异常: ${e.message}"
            )
        }
    }

    // ===== Locator Building (AutoLXB RuntimeLocatorBuilder pattern) =====

    /**
     * Build a locator for the element at the given device-pixel coordinates.
     * Returns a map with: resource_id, text, content_desc, class, bounds_hint,
     * locator_index, locator_count, fallback_point.
     */
    private fun buildLocator(x: Int, y: Int, xml: String): Map<String, Any> {
        try {
            val nodePattern = Regex("""<node\s[^>]*?>""")
            val boundsPattern = Regex("""bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"""")
            val textPattern = Regex("""text="([^"]*?)"""")
            val descPattern = Regex("""content-desc="([^"]*?)"""")
            val idPattern = Regex("""resource-id="([^"]*?)"""")
            val classPattern = Regex("""class="([^"]*?)"""")

            // Find all nodes containing the point, pick the smallest (most specific)
            var bestMatch: Map<String, Any>? = null
            var bestArea = Int.MAX_VALUE

            for (nodeMatch in nodePattern.findAll(xml)) {
                val nodeStr = nodeMatch.value
                val boundsMatch = boundsPattern.find(nodeStr) ?: continue
                val x1 = boundsMatch.groupValues[1].toInt()
                val y1 = boundsMatch.groupValues[2].toInt()
                val x2 = boundsMatch.groupValues[3].toInt()
                val y2 = boundsMatch.groupValues[4].toInt()

                if (x in x1..x2 && y in y1..y2) {
                    val area = (x2 - x1) * (y2 - y1)
                    if (area < bestArea) {
                        bestArea = area
                        val locator = mutableMapOf<String, Any>()
                        idPattern.find(nodeStr)?.groupValues?.get(1)?.let {
                            if (it.isNotBlank() && isInformativeResourceId(it)) locator["resource_id"] = it
                        }
                        textPattern.find(nodeStr)?.groupValues?.get(1)?.let {
                            if (it.isNotBlank()) locator["text"] = it
                        }
                        descPattern.find(nodeStr)?.groupValues?.get(1)?.let {
                            if (it.isNotBlank()) locator["content_desc"] = it
                        }
                        classPattern.find(nodeStr)?.groupValues?.get(1)?.let {
                            if (it.isNotBlank()) locator["class"] = it
                        }
                        locator["bounds_hint"] = listOf(x1, y1, x2, y2)
                        locator["fallback_point"] = listOf(x, y)
                        if (locator.isNotEmpty()) bestMatch = locator
                    }
                }
            }

            return bestMatch ?: mapOf<String, Any>("fallback_point" to listOf(x, y))
        } catch (e: Exception) {
            return mapOf<String, Any>("fallback_point" to listOf(x, y))
        }
    }

    /**
     * Build a container probe (parent-level locator) for fallback.
     * Mirrors AutoLXB RuntimeLocatorBuilder.buildContainerProbe.
     */
    private fun buildContainerProbe(x: Int, y: Int, xml: String): Map<String, String> {
        try {
            val nodePattern = Regex("""<node\s[^>]*?>""")
            val boundsPattern = Regex("""bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"""")
            val textPattern = Regex("""text="([^"]*?)"""")
            val descPattern = Regex("""content-desc="([^"]*?)"""")
            val idPattern = Regex("""resource-id="([^"]*?)"""")
            val classPattern = Regex("""class="([^"]*?)"""")
            val clickablePattern = Regex("""clickable="true"""")

            // Find the smallest clickable element containing the point
            var bestMatch: Map<String, String>? = null
            var bestArea = Int.MAX_VALUE

            for (nodeMatch in nodePattern.findAll(xml)) {
                val nodeStr = nodeMatch.value
                if (!clickablePattern.containsMatchIn(nodeStr)) continue

                val boundsMatch = boundsPattern.find(nodeStr) ?: continue
                val x1 = boundsMatch.groupValues[1].toInt()
                val y1 = boundsMatch.groupValues[2].toInt()
                val x2 = boundsMatch.groupValues[3].toInt()
                val y2 = boundsMatch.groupValues[4].toInt()

                if (x in x1..x2 && y in y1..y2) {
                    val area = (x2 - x1) * (y2 - y1)
                    if (area < bestArea) {
                        bestArea = area
                        val probe = mutableMapOf<String, String>()
                        idPattern.find(nodeStr)?.groupValues?.get(1)?.let {
                            if (it.isNotBlank() && isInformativeResourceId(it)) probe["resource_id"] = it
                        }
                        textPattern.find(nodeStr)?.groupValues?.get(1)?.let {
                            if (it.isNotBlank()) probe["text"] = it
                        }
                        descPattern.find(nodeStr)?.groupValues?.get(1)?.let {
                            if (it.isNotBlank()) probe["content_desc"] = it
                        }
                        classPattern.find(nodeStr)?.groupValues?.get(1)?.let {
                            if (it.isNotBlank()) probe["class"] = it
                        }
                        if (probe.isNotEmpty()) bestMatch = probe
                    }
                }
            }
            return bestMatch ?: emptyMap()
        } catch (e: Exception) {
            return emptyMap()
        }
    }

    /**
     * Check if a resource-id is informative (not just "android:id/..." or empty).
     */
    private fun isInformativeResourceId(rid: String): Boolean {
        if (rid.isBlank()) return false
        if (rid.startsWith("android:")) return false
        return true
    }

    // ===== UI Stability Detection =====

    private suspend fun waitForUiStable(maxWaitMs: Long = UI_SETTLE_TIMEOUT_MS) {
        val sampleMs = UI_SETTLE_SAMPLE_MS
        var hits = 0
        var elapsed = 0L
        var lastHash = ""

        while (elapsed < maxWaitMs) {
            delay(sampleMs)
            elapsed += sampleMs

            val hierarchy = when (val r = senseEngine.dumpHierarchy()) {
                is HierarchyResult.Success -> r.xml
                else -> continue
            }

            // Hash: count of nodes + first N chars of text content
            val nodeCount = hierarchy.lines().count { it.trim().startsWith("<node") }
            val textHash = Regex("""text="([^"]*)"""").findAll(hierarchy)
                .take(20).map { it.groupValues[1] }.joinToString("|").hashCode()
            val currentHash = "${nodeCount}_$textHash"

            if (currentHash == lastHash) {
                hits++
                if (hits >= UI_SETTLE_REQUIRED_HITS) return
            } else {
                hits = 0
                lastHash = currentHash
            }
        }
    }

    // ===== Utility =====

    private suspend fun verifyAppForeground(packageName: String): Boolean {
        val activity = senseEngine.getCurrentActivity() ?: return true
        return activity.contains(packageName)
    }

    /**
     * Try to resolve a package name from an app name using PackageManager.
     */
    private fun tryResolvePackage(appName: String): String {
        try {
            val pm = context.packageManager
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            intent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            val resolveInfos = pm.queryIntentActivities(intent, 0)

            // Exact match first
            for (info in resolveInfos) {
                val label = info.loadLabel(pm).toString()
                if (label.equals(appName, ignoreCase = true)) {
                    return info.activityInfo.packageName
                }
            }
            // Partial match
            for (info in resolveInfos) {
                val label = info.loadLabel(pm).toString()
                if (label.contains(appName, ignoreCase = true)) {
                    return info.activityInfo.packageName
                }
            }
        } catch (e: Exception) {
            DebugLog.w("DirectBridge", "tryResolvePackage failed: ${e.message}")
        }
        return ""
    }

    private fun bringAppToForeground() {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(
                    android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
                context.startActivity(launchIntent)
            }
        } catch (e: Exception) {
            DebugLog.w("DirectBridge", "Failed to bring app to foreground: ${e.message}")
        }
    }

    // ===== CoreBridge Interface =====

    override suspend fun getTaskStatus(taskId: String): TaskStatusResult {
        val s = activeTasks[taskId] ?: return TaskStatusResult.Error(CoreErrorCode.UNKNOWN_ERROR, "任务不存在")
        val thinking = buildString {
            if (s.aiObserving.isNotBlank()) append("观察: ${s.aiObserving}\n")
            if (s.aiThinking.isNotBlank()) append("思考: ${s.aiThinking}\n")
            if (s.aiAction.isNotBlank()) append("行动: ${s.aiAction}\n")
            if (s.aiExpected.isNotBlank()) append("预期: ${s.aiExpected}")
        }
        return TaskStatusResult.Status(taskId, s.state, s.phase, s.detail, aiThinking = thinking.trim())
    }

    override suspend fun cancelTask(taskId: String): CancelResult {
        activeTasks[taskId]?.apply { state = "cancelled"; detail = "用户取消" }
            ?: return CancelResult.Error(CoreErrorCode.UNKNOWN_ERROR, "任务不存在")
        return CancelResult.Success
    }

    /**
     * Return the recorded steps as a route JSON with full locator info.
     * Applies TaskMapAssembler-style optimization:
     * - Filter out non-replayable ops
     * - Validate TAP has locator or tapPoint
     * - Validate SWIPE has valid args
     * - Remove redundant consecutive same-locator TAPs
     */
    override suspend fun getLatestRoute(taskId: String): RouteResult {
        val s = activeTasks[taskId] ?: return RouteResult.Error(CoreErrorCode.ROUTE_NOT_FOUND, "任务不存在")
        if (s.state != "success") return RouteResult.Error(CoreErrorCode.ROUTE_NOT_FOUND, "任务未完成")

        if (s.executedSteps.isEmpty()) {
            return RouteResult.Error(CoreErrorCode.ROUTE_NOT_FOUND, "无执行步骤")
        }

        // Apply TaskMapAssembler-style optimization
        val replayableOps = setOf("TAP", "SWIPE", "INPUT", "BACK", "WAIT", "LAUNCH")
        val optimizedSteps = mutableListOf<RecordedStep>()
        var lastLocatorKey = ""

        for (step in s.executedSteps) {
            // Skip non-replayable ops (like HOME)
            if (step.op !in replayableOps) continue

            // Validate TAP has locator or tapPoint
            if (step.op == "TAP" && step.locator.isEmpty() && step.tapPoint.size < 2) {
                DebugLog.w("DirectBridge", "Skipping TAP step with no locator or tapPoint: ${step.summary}")
                continue
            }

            // Validate SWIPE has valid args
            if (step.op == "SWIPE" && step.swipe.isEmpty() && step.args.size < 5) {
                DebugLog.w("DirectBridge", "Skipping SWIPE step with no swipe data: ${step.summary}")
                continue
            }

            // Remove redundant consecutive same-locator TAPs
            val locatorKey = step.locator.entries
                .filter { it.key != "fallback_point" && it.key != "bounds_hint" }
                .sortedBy { it.key }
                .joinToString("|") { "${it.key}=${it.value}" }
            if (step.op == "TAP" && locatorKey == lastLocatorKey && locatorKey.isNotEmpty()) {
                DebugLog.i("DirectBridge", "Skipping redundant TAP: ${step.summary}")
                continue
            }
            if (step.op == "TAP") lastLocatorKey = locatorKey
            else lastLocatorKey = ""

            optimizedSteps.add(step)
        }

        if (optimizedSteps.isEmpty()) {
            return RouteResult.Error(CoreErrorCode.ROUTE_NOT_FOUND, "无可回放步骤")
        }

        // Build route JSON from optimized steps
        val stepsJson = optimizedSteps.mapIndexed { _, step ->
            val op = when (step.op) {
                "LAUNCH" -> "LAUNCH"
                "TAP" -> "TAP"
                "INPUT" -> "INPUT"
                "SWIPE" -> "SWIPE"
                "BACK" -> "KEY"
                "WAIT" -> "WAIT"
                else -> "TAP"
            }
            val args = when (step.op) {
                "LAUNCH" -> """["${step.args.firstOrNull() ?: ""}"]"""
                "TAP" -> step.args.joinToString(",", "[", "]")
                "INPUT" -> """["${step.args.firstOrNull()?.replace("\"", "\\\"") ?: ""}"]"""
                "SWIPE" -> step.args.joinToString(",", "[", "]")
                "BACK" -> "[4]"
                "WAIT" -> "[${step.args.firstOrNull()?.toLongOrNull() ?: 1000}]"
                else -> "[]"
            }

            // Build locator JSON (AutoLXB TaskMap.Step format)
            val locatorJson = if (step.locator.isNotEmpty()) {
                val entries = step.locator.entries.joinToString(",") { (k, v) ->
                    val valueStr = when (v) {
                        is List<*> -> v.joinToString(",", "[", "]")
                        else -> """"${v.toString().replace("\"", "\\\"")}""""
                    }
                    """"$k":$valueStr"""
                }
                ""","locator":{$entries}"""
            } else ""

            // Build containerProbe JSON
            val containerProbeJson = if (step.containerProbe.isNotEmpty()) {
                val entries = step.containerProbe.entries.joinToString(",") { """"${it.key}":"${it.value.replace("\"", "\\\"")}"""" }
                ""","container_probe":{$entries}"""
            } else ""

            // Build fallback point
            val fallbackJson = if (step.tapPoint.size >= 2) {
                ""","fallback_point":[${step.tapPoint[0]},${step.tapPoint[1]}]"""
            } else ""

            // Build swipe info
            val swipeJson = if (step.swipe.isNotEmpty()) {
                val entries = step.swipe.entries.joinToString(",") { (k, v) ->
                    val valueStr = when (v) {
                        is List<*> -> v.joinToString(",", "[", "]")
                        else -> v.toString()
                    }
                    """"$k":$valueStr"""
                }
                ""","swipe":{$entries}"""
            } else ""

            // Semantic note and expected
            val semanticJson = if (step.semanticNote.isNotBlank()) {
                ""","semantic_note":"${step.semanticNote.replace("\"", "\\\"")}""""
            } else ""
            val expectedJson = if (step.expected.isNotBlank()) {
                ""","expected":"${step.expected.replace("\"", "\\\"")}""""
            } else ""

            // Extra fields for LAUNCH
            val extraFields = when (step.op) {
                "LAUNCH" -> ""","package":"${step.args.firstOrNull() ?: ""}","appName":"${step.summary.replace("打开", "")}""""
                else -> ""
            }

            """{"op":"$op","args":$args,"delay":500,"summary":"${step.summary.replace("\"", "\\\"")}","success":${step.success}$locatorJson$containerProbeJson$fallbackJson$swipeJson$semanticJson$expectedJson$extraFields}"""
        }.joinToString(",")

        val routeJson = """{"segments":[{"steps":[$stepsJson]}]}"""
        DebugLog.i("DirectBridge", "Generated optimized route with ${optimizedSteps.size} steps (from ${s.executedSteps.size} raw)")
        return RouteResult.Found(routeJson)
    }

    override suspend fun getLatestTrace(taskId: String): TraceResult {
        val s = activeTasks[taskId] ?: return TraceResult.Error(CoreErrorCode.TRACE_NOT_FOUND, "无数据")
        val lines = mutableListOf("{\"ts\":${System.currentTimeMillis()},\"event\":\"task_start\"}")
        for ((i, step) in s.executedSteps.withIndex()) {
            val event = if (step.success) "step_ok" else "step_fail"
            lines.add("{\"ts\":${System.currentTimeMillis()},\"event\":\"$event\",\"step\":${i + 1},\"summary\":\"${step.summary}\"}")
        }
        lines.add("{\"ts\":${System.currentTimeMillis()},\"event\":\"task_${s.state}\"}")
        return TraceResult.Found(lines)
    }

    override suspend fun runRoute(taskId: String, routeJson: String): RouteRunResult {
        return try {
            val segments = SafeJson.getArray(routeJson, "segments") ?: return RouteRunResult.Failed(-1, "路由无步骤")
            val start = System.currentTimeMillis()
            for (seg in SafeJson.parseArrayItems(segments)) {
                val steps = SafeJson.getArray(seg, "steps") ?: continue
                for (step in SafeJson.parseArrayItems(steps)) executeStep(step)
            }
            RouteRunResult.Success(durationMs = System.currentTimeMillis() - start)
        } catch (e: Exception) { RouteRunResult.Failed(-1, e.message ?: "路由执行失败") }
    }

    /**
     * Execute a single step from a route JSON.
     * 3-level replay: locator → containerProbe → fallback_point (AutoLXB pattern).
     */
    private suspend fun executeStep(stepJson: String) {
        val op = SafeJson.getString(stepJson, "op")?.uppercase() ?: return
        when (op) {
            "TAP" -> {
                val args = SafeJson.getArray(stepJson, "args") ?: return

                // Level 1: Try locator-based replay
                val locatorStr = SafeJson.getString(stepJson, "locator")
                if (locatorStr != null) {
                    val hierarchy = when (val r = senseEngine.dumpHierarchy()) {
                        is HierarchyResult.Success -> r.xml
                        else -> null
                    }
                    if (hierarchy != null) {
                        val coords = findElementByLocator(hierarchy, locatorStr)
                        if (coords != null) {
                            DebugLog.i("DirectBridge", "Replay TAP via locator: (${coords.first}, ${coords.second})")
                            inputEngine.tap(coords.first, coords.second)
                            delay(200)
                            return
                        }
                    }
                }

                // Level 2: Try containerProbe-based replay
                val containerProbeStr = SafeJson.getString(stepJson, "container_probe")
                if (containerProbeStr != null) {
                    val hierarchy = when (val r = senseEngine.dumpHierarchy()) {
                        is HierarchyResult.Success -> r.xml
                        else -> null
                    }
                    if (hierarchy != null) {
                        val coords = findElementByLocator(hierarchy, containerProbeStr)
                        if (coords != null) {
                            DebugLog.i("DirectBridge", "Replay TAP via containerProbe: (${coords.first}, ${coords.second})")
                            inputEngine.tap(coords.first, coords.second)
                            delay(200)
                            return
                        }
                    }
                }

                // Level 3: Use fallback_point coordinates
                val fallbackStr = SafeJson.getString(stepJson, "fallback_point")
                if (fallbackStr != null) {
                    val fx = SafeJson.arrayInt(fallbackStr, 0)
                    val fy = SafeJson.arrayInt(fallbackStr, 1)
                    if (fx != null && fy != null) {
                        DebugLog.i("DirectBridge", "Replay TAP via fallback_point: ($fx, $fy)")
                        inputEngine.tap(fx, fy)
                        delay(200)
                        return
                    }
                }

                // Last resort: use args as coordinates
                val x = SafeJson.arrayInt(args, 0) ?: return
                val y = SafeJson.arrayInt(args, 1) ?: return
                DebugLog.i("DirectBridge", "Replay TAP via args: ($x, $y)")
                inputEngine.tap(x, y)
            }
            "SWIPE" -> {
                // Try swipe object first, then args
                val swipeStr = SafeJson.getString(stepJson, "swipe")
                if (swipeStr != null) {
                    val startStr = SafeJson.getString(swipeStr, "start")
                    val endStr = SafeJson.getString(swipeStr, "end")
                    val durMs = SafeJson.getString(swipeStr, "duration_ms")?.toIntOrNull() ?: 600
                    if (startStr != null && endStr != null) {
                        val sx = SafeJson.arrayInt(startStr, 0) ?: return
                        val sy = SafeJson.arrayInt(startStr, 1) ?: return
                        val ex = SafeJson.arrayInt(endStr, 0) ?: return
                        val ey = SafeJson.arrayInt(endStr, 1) ?: return
                        inputEngine.swipe(sx, sy, ex, ey, durMs)
                        delay(200)
                        return
                    }
                }
                val args = SafeJson.getArray(stepJson, "args") ?: return
                inputEngine.swipe(
                    SafeJson.arrayInt(args, 0) ?: return,
                    SafeJson.arrayInt(args, 1) ?: return,
                    SafeJson.arrayInt(args, 2) ?: return,
                    SafeJson.arrayInt(args, 3) ?: return,
                    SafeJson.arrayInt(args, 4) ?: 300
                )
            }
            "KEY" -> {
                val args = SafeJson.getArray(stepJson, "args") ?: return
                inputEngine.pressKey(SafeJson.arrayInt(args, 0) ?: return)
            }
            "LAUNCH" -> {
                val pkg = SafeJson.getString(stepJson, "package") ?: return
                senseEngine.launchApp(pkg)
            }
            "INPUT" -> {
                val args = SafeJson.getArray(stepJson, "args") ?: return
                val text = SafeJson.arrayString(args, 0) ?: return
                inputEngine.inputText(text)
            }
            "WAIT" -> {
                val args = SafeJson.getArray(stepJson, "args")
                delay((SafeJson.arrayInt(args ?: "[]", 0) ?: 1000).toLong())
            }
        }
        delay(200)
    }

    /**
     * Find element by locator JSON (supports resource_id, text, content_desc, class).
     * Used for locator-based replay.
     */
    private fun findElementByLocator(xml: String, locatorJson: String): Pair<Int, Int>? {
        try {
            val text = SafeJson.getString(locatorJson, "text")
            val desc = SafeJson.getString(locatorJson, "content_desc")
            val resId = SafeJson.getString(locatorJson, "resource_id")

            // Try each locator strategy in priority order
            for (strategy in listOf("text", "content_desc", "resource_id")) {
                val searchValue = when (strategy) {
                    "text" -> text
                    "content_desc" -> desc
                    "resource_id" -> resId
                    else -> null
                } ?: continue
                if (searchValue.isBlank()) continue

                val coords = findElementByAttr(xml, strategy, searchValue)
                if (coords != null) return coords
            }
        } catch (e: Exception) {
            DebugLog.w("DirectBridge", "findElementByLocator error: ${e.message}")
        }
        return null
    }

    private fun findElementByAttr(xml: String, attrName: String, value: String): Pair<Int, Int>? {
        try {
            val xmlAttrName = when (attrName) {
                "text" -> "text"
                "content_desc" -> "content-desc"
                "resource_id" -> "resource-id"
                else -> return null
            }
            val nodePattern = Regex("""<node\s[^>]*?>""")
            val boundsPattern = Regex("""bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"""")
            val attrPattern = Regex("""$xmlAttrName="([^"]*?)"""")

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

    override suspend fun screenshot() = senseEngine.screenshot()
    override suspend fun dumpHierarchy() = senseEngine.dumpHierarchy()
}
