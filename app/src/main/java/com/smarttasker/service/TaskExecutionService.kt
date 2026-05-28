package com.smarttasker.service

import android.content.Context
import com.smarttasker.core.bridge.*
import com.smarttasker.core.parser.TaskSpec
import com.smarttasker.core.adapter.RouteAdapter
import com.smarttasker.core.adapter.TraceAdapter
import com.smarttasker.core.retry.RetryExecutor
import com.smarttasker.core.retry.RetryPolicy
import com.smarttasker.core.screenshot.ScreenshotManager
import com.smarttasker.data.entity.RouteStepEntity
import com.smarttasker.data.entity.RunRecordEntity
import com.smarttasker.data.repository.RouteRepository
import com.smarttasker.data.repository.RunRepository
import com.smarttasker.util.DebugLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
 
/**
 * Service that orchestrates task execution through CoreBridge.
 * Handles: submit → monitor → collect route/trace → save.
 */
class TaskExecutionService(
    private val context: Context,
    private val routeRepository: RouteRepository,
    private val runRepository: RunRepository,
    private val screenshotManager: ScreenshotManager? = null
) {
    private val manager = CoreBridgeManager.getInstance(context)
    private val bridge: CoreBridge get() = manager.bridge

    private val submitRetryExecutor = RetryExecutor(RetryPolicy.DEFAULT)
    private val pollRetryExecutor = RetryExecutor(RetryPolicy.FAST)

    // ===== Execution State =====
    
    private val _executionState = MutableStateFlow<ExecutionState>(ExecutionState.Idle)
    val executionState: StateFlow<ExecutionState> = _executionState.asStateFlow()
    
    private var monitorJob: Job? = null
    
    /**
     * Execute a quick task (trial run).
     * Flow: submit → monitor status → on success, collect route → save
     */
    suspend fun executeQuickTask(taskSpec: TaskSpec): ExecutionResult {
        var result: ExecutionResult
        var totalRetryCount = 0
        try {
            // Build payload FIRST (before any state changes that trigger recomposition)
            val payload = try {
                buildTaskPayload(taskSpec)
            } catch (e: Exception) {
                _executionState.value = ExecutionState.Error("构建任务参数失败: ${e.message}")
                return ExecutionResult.Error("构建任务参数失败: ${e.message}")
            }

            // NOW set submitting state (triggers recomposition)
            _executionState.value = ExecutionState.Submitting

            // 提交任务（带重试）
            DebugLog.i("TaskExec", "提交任务，启用重试机制")
            val submitRetryResult = submitRetryExecutor.executeCatching(
                operation = {
                    val submitResult = bridge.submitQuickTask(payload)
                    when (submitResult) {
                        is TaskSubmitResult.Accepted -> Pair(submitResult.taskId, submitResult.runId)
                        is TaskSubmitResult.Error -> throw RuntimeException(submitResult.message)
                    }
                },
                onRetry = { retryCount, exception, delayMs ->
                    totalRetryCount += 1
                    DebugLog.w("TaskExec", "提交重试 #$retryCount: ${exception.message}, ${delayMs}ms后重试")
                }
            )

            if (submitRetryResult.isFailure) {
                val error = submitRetryResult.exceptionOrNull()
                DebugLog.e("TaskExec", "提交失败(已重试${totalRetryCount}次): ${error?.message}")
                _executionState.value = ExecutionState.Error("提交失败: ${error?.message}")
                return ExecutionResult.Error("提交失败: ${error?.message}")
            }

            val (taskId, runId) = submitRetryResult.getOrThrow()
            DebugLog.i("TaskExec", "任务提交成功 taskId=$taskId, 提交重试次数=$totalRetryCount")

            _executionState.value = ExecutionState.Running(taskId = taskId, phase = "执行中", progress = 0f)

            val (finalStatus, pollRetryCount) = pollUntilDone(taskId)
            totalRetryCount += pollRetryCount

            result = when (finalStatus.state) {
                "success" -> {
                    _executionState.value = ExecutionState.Completed(taskId)
 
                    // 任务成功后截图验证
                    val screenshotPath = screenshotManager?.let { manager ->
                        when (val result = manager.captureScreen("task_${taskId}")) {
                            is com.smarttasker.core.screenshot.ScreenshotManager.ScreenshotResult.Success -> {
                                DebugLog.i("TaskExec", "任务截图成功: ${result.path}")
                                result.path
                            }
                            is com.smarttasker.core.screenshot.ScreenshotManager.ScreenshotResult.Error -> {
                                DebugLog.w("TaskExec", "任务截图失败: ${result.message}")
                                null
                            }
                            else -> null
                            }
                    }
 
                    val route = collectRoute(taskId)
                    val trace = collectTrace(taskId)
                    val parsedRoute = route?.let { RouteAdapter.parseRoute(it, taskId) }
                    val steps = parsedRoute?.steps ?: emptyList()

                    DebugLog.i("TaskExec", "任务成功完成, 总重试次数=$totalRetryCount")
                    val runRecord = RunRecordEntity(
                        runId = runId,
                        taskId = taskId,
                        status = "success",
                        diagnosisSummary = "试跑成功",
                        diagnosisSuggestion = "路线已学习，可复用",
                        modelCalls = 0,
                        routeSnapshot = route ?: "",
                        retryCount = totalRetryCount,
                        screenshotPath = screenshotPath
                        )
                    runRepository.insertRun(runRecord)

                    ExecutionResult.Success(
                        taskId = taskId,
                        routeJson = route,
                        steps = steps,
                        traceLines = trace ?: emptyList()
                    )
                }
                "failed" -> {
                    _executionState.value = ExecutionState.Error(finalStatus.detail)

                    val trace = collectTrace(taskId)
                    val parsedTrace = TraceAdapter.parseTrace(trace ?: emptyList(), taskId, runId)
                    val diagnosis = Pair(parsedTrace.runRecord.diagnosisSummary, parsedTrace.runRecord.diagnosisSuggestion)

                    DebugLog.w("TaskExec", "任务失败, 总重试次数=$totalRetryCount, 原因=${finalStatus.detail}")
                    val runRecord = RunRecordEntity(
                        runId = runId,
                        taskId = taskId,
                        status = "failed",
                        diagnosisSummary = diagnosis.first,
                        diagnosisSuggestion = diagnosis.second,
                        modelCalls = 0,
                        routeSnapshot = "",
                        retryCount = totalRetryCount
                    )
                    runRepository.insertRun(runRecord)

                    ExecutionResult.Failed(
                        taskId = taskId,
                        reason = finalStatus.detail,
                        traceLines = trace ?: emptyList(),
                        diagnosis = diagnosis
                    )
                }
                "cancelled" -> {
                    _executionState.value = ExecutionState.Idle
                    ExecutionResult.Cancelled
                }
                else -> {
                    _executionState.value = ExecutionState.Error("未知状态: ${finalStatus.state}")
                    ExecutionResult.Error("未知状态: ${finalStatus.state}")
                }
            }
        } catch (e: Exception) {
            _executionState.value = ExecutionState.Error(e.message ?: "未知异常")
            result = ExecutionResult.Error(e.message ?: "未知异常")
        }
        return result
    }
    
    /**
     * Execute a saved route (replay mode).
     */
    suspend fun executeSavedRoute(
        taskId: String,
        routeSteps: List<RouteStepEntity>
    ): ExecutionResult {
        val runId = "run_${System.currentTimeMillis()}"
        _executionState.value = ExecutionState.Running(taskId, "回放路线", 0f)
        
        // Convert steps back to AutoLXB route JSON
        val routeJson = RouteAdapter.toRouteJson(routeSteps)
        
        val startTime = System.currentTimeMillis()
        val result = bridge.runRoute(taskId, routeJson)
        val durationMs = System.currentTimeMillis() - startTime
        
        return when (result) {
            is RouteRunResult.Success -> {
                _executionState.value = ExecutionState.Completed(taskId)
                
                // Collect trace for the successful run
                val trace = collectTrace(taskId)
                
                // Save success run record
                val runRecord = RunRecordEntity(
                    runId = runId,
                    taskId = taskId,
                    status = "success",
                    durationMs = durationMs,
                    modelCalls = 0,
                    diagnosisSummary = "路线回放成功",
                    diagnosisSuggestion = "路线执行完成",
                    routeSnapshot = routeJson,
                    retryCount = 0
                )
                runRepository.insertRun(runRecord)
                DebugLog.i("TaskExec", "路线回放成功, runId=$runId, duration=${durationMs}ms")
                
                ExecutionResult.Success(
                    taskId = taskId,
                    routeJson = routeJson,
                    steps = routeSteps,
                    traceLines = trace ?: emptyList()
                )
            }
            is RouteRunResult.Failed -> {
                _executionState.value = ExecutionState.Error("步骤 ${result.stepIndex}: ${result.reason}")
                
                // Collect trace for the failed run
                val trace = collectTrace(taskId)
                
                // Save failed run record
                val runRecord = RunRecordEntity(
                    runId = runId,
                    taskId = taskId,
                    status = "failed",
                    durationMs = durationMs,
                    modelCalls = 0,
                    failedStepId = result.stepIndex?.toString(),
                    failureType = "route_step_failed",
                    diagnosisSummary = "路线执行失败: 步骤 ${result.stepIndex}",
                    diagnosisSuggestion = result.reason,
                    routeSnapshot = routeJson,
                    retryCount = 0
                )
                runRepository.insertRun(runRecord)
                DebugLog.w("TaskExec", "路线回放失败, runId=$runId, step=${result.stepIndex}, reason=${result.reason}")
                
                ExecutionResult.Failed(
                    taskId = taskId,
                    reason = "步骤 ${result.stepIndex}: ${result.reason}",
                    traceLines = trace ?: emptyList(),
                    diagnosis = Pair("路线执行失败", result.reason)
                )
            }
            is RouteRunResult.Error -> {
                _executionState.value = ExecutionState.Error(result.message)
                
                // Save error run record
                val runRecord = RunRecordEntity(
                    runId = runId,
                    taskId = taskId,
                    status = "failed",
                    durationMs = durationMs,
                    modelCalls = 0,
                    failureType = "route_error",
                    diagnosisSummary = "路线执行错误",
                    diagnosisSuggestion = result.message,
                    routeSnapshot = routeJson,
                    retryCount = 0
                )
                runRepository.insertRun(runRecord)
                DebugLog.e("TaskExec", "路线执行错误, runId=$runId, error=${result.message}")
                
                ExecutionResult.Error(result.message)
            }
        }
    }
    
    /**
     * Cancel the current execution.
     */
    suspend fun cancelExecution(taskId: String) {
        monitorJob?.cancel()
        bridge.cancelTask(taskId)
        _executionState.value = ExecutionState.Idle
    }
    
    // ===== Private Helpers =====
    
    private suspend fun pollUntilDone(taskId: String): Pair<TaskStatusResult.Status, Int> {
        var pollRetryCount = 0
        val status = withTimeoutOrNull(120_000L) { // 2 min timeout
            while (true) {
                val retryResult = pollRetryExecutor.executeCatching(
                    operation = { bridge.getTaskStatus(taskId) },
                    onRetry = { retryCount, exception, delayMs ->
                        pollRetryCount += 1
                        DebugLog.w("TaskExec", "轮询重试 #$retryCount: ${exception.message}, ${delayMs}ms后重试")
                    }
                )

                if (retryResult.isFailure) {
                    DebugLog.e("TaskExec", "轮询状态失败(已重试${pollRetryCount}次): ${retryResult.exceptionOrNull()?.message}")
                    return@withTimeoutOrNull TaskStatusResult.Status(
                        taskId = taskId,
                        state = "failed",
                        phase = "error",
                        detail = "轮询失败: ${retryResult.exceptionOrNull()?.message}"
                    )
                }

                when (val result = retryResult.getOrThrow()) {
                    is TaskStatusResult.Status -> {
                        _executionState.value = ExecutionState.Running(
                            taskId = taskId,
                            phase = result.phase,
                            progress = estimateProgress(result.phase)
                        )
                        if (result.state in listOf("success", "failed", "cancelled")) {
                            return@withTimeoutOrNull result
                        }
                    }
                    is TaskStatusResult.Error -> {
                        return@withTimeoutOrNull TaskStatusResult.Status(
                            taskId = taskId,
                            state = "failed",
                            phase = "error",
                            detail = result.message
                        )
                    }
                }
                delay(1000) // Poll every second
            }
            @Suppress("UNREACHABLE_CODE")
            TaskStatusResult.Status(taskId, "failed", "timeout", "执行超时")
        } ?: TaskStatusResult.Status(taskId, "failed", "timeout", "执行超时 (2分钟)")

        if (pollRetryCount > 0) {
            DebugLog.i("TaskExec", "轮询完成, 轮询重试次数=$pollRetryCount")
        }
        return Pair(status, pollRetryCount)
    }
    
    private suspend fun collectRoute(taskId: String): String? {
        return when (val result = bridge.getLatestRoute(taskId)) {
            is RouteResult.Found -> result.routeJson
            else -> null
        }
    }
    
    private suspend fun collectTrace(taskId: String): List<String>? {
        return when (val result = bridge.getLatestTrace(taskId)) {
            is TraceResult.Found -> result.traceLines
            else -> null
        }
    }
    
    private fun buildTaskPayload(spec: TaskSpec): String {
        // Manual JSON to avoid any JSONObject issues on some devices
        val sb = StringBuilder(256)
        sb.append('{')
        sb.append("\"type\":\"quick\"")
        sb.append(",\"name\":").append(jsonEscape(spec.name))
        sb.append(",\"description\":").append(jsonEscape(spec.description ?: ""))
        sb.append(",\"playbook\":").append(jsonEscape(spec.playbook ?: ""))
        sb.append(",\"execution_mode\":").append(jsonEscape(spec.execution.mode ?: "manual"))
        sb.append(",\"route_enabled\":").append(spec.execution.routeEnabled)

        // target_app
        sb.append(",\"target_app\":{")
        sb.append("\"name\":").append(jsonEscape(spec.targetApp?.name ?: ""))
        sb.append(",\"package\":").append(jsonEscape(spec.targetApp?.packageName ?: ""))
        sb.append(",\"confidence\":").append(spec.targetApp?.confidence ?: 0f)
        sb.append('}')

        // trigger
        sb.append(",\"trigger\":{")
        sb.append("\"type\":").append(jsonEscape(spec.trigger.type))
        sb.append(",\"time\":").append(jsonEscape(spec.trigger.time ?: ""))
        sb.append(",\"repeat\":").append(jsonEscape(spec.trigger.repeat ?: "once"))
        sb.append('}')

        sb.append('}')
        return sb.toString()
    }

    private fun jsonEscape(s: String): String {
        val escaped = s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }
    
    private fun estimateProgress(phase: String): Float = when (phase) {
        "analyzing" -> 0.1f
        "launching" -> 0.2f
        "navigating" -> 0.5f
        "interacting" -> 0.7f
        "verifying" -> 0.9f
        "completed" -> 1.0f
        else -> 0.3f
    }
}

// ===== State & Result Types =====

sealed class ExecutionState {
    object Idle : ExecutionState()
    object Submitting : ExecutionState()
    data class Running(val taskId: String, val phase: String, val progress: Float) : ExecutionState()
    data class Completed(val taskId: String) : ExecutionState()
    data class Error(val message: String) : ExecutionState()
}

sealed class ExecutionResult {
    data class Success(
        val taskId: String,
        val routeJson: String?,
        val steps: List<RouteStepEntity>,
        val traceLines: List<String>
    ) : ExecutionResult()
    
    data class Failed(
        val taskId: String,
        val reason: String,
        val traceLines: List<String>,
        val diagnosis: Pair<String, String>
    ) : ExecutionResult()
    
    object Cancelled : ExecutionResult()
    data class Error(val message: String) : ExecutionResult()
}
