package com.smarttasker.service

import android.content.Context
import com.smarttasker.core.bridge.*
import com.smarttasker.core.parser.TaskSpec
import com.smarttasker.core.adapter.RouteAdapter
import com.smarttasker.core.adapter.TraceAdapter
import com.smarttasker.data.entity.RouteStepEntity
import com.smarttasker.data.entity.RunRecordEntity
import com.smarttasker.data.repository.RouteRepository
import com.smarttasker.data.repository.RunRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Service that orchestrates task execution through CoreBridge.
 * Handles: submit → monitor → collect route/trace → save.
 */
class TaskExecutionService(
    private val context: Context,
    private val routeRepository: RouteRepository,
    private val runRepository: RunRepository
) {
    private val manager = CoreBridgeManager.getInstance(context)
    private val bridge: CoreBridge get() = manager.bridge
    
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

            val submitResult = try {
                bridge.submitQuickTask(payload)
            } catch (e: Exception) {
                _executionState.value = ExecutionState.Error("提交失败: ${e.message}")
                return ExecutionResult.Error("提交失败: ${e.message}")
            }

            val (taskId, runId) = when (submitResult) {
                is TaskSubmitResult.Accepted -> Pair(submitResult.taskId, submitResult.runId)
                is TaskSubmitResult.Error -> {
                    _executionState.value = ExecutionState.Error(submitResult.message)
                    return ExecutionResult.Error(submitResult.message)
                }
            }

            _executionState.value = ExecutionState.Running(taskId = taskId, phase = "执行中", progress = 0f)
            
            val finalStatus = pollUntilDone(taskId)
            
            result = when (finalStatus.state) {
                "success" -> {
                    _executionState.value = ExecutionState.Completed(taskId)
                    
                    val route = collectRoute(taskId)
                    val trace = collectTrace(taskId)
                    val parsedRoute = route?.let { RouteAdapter.parseRoute(it, taskId) }
                    val steps = parsedRoute?.steps ?: emptyList()
                    
                    val runRecord = RunRecordEntity(
                        runId = runId,
                        taskId = taskId,
                        status = "success",
                        diagnosisSummary = "试跑成功",
                        diagnosisSuggestion = "路线已学习，可复用",
                        modelCalls = 0,
                        routeSnapshot = route ?: ""
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
                    
                    val runRecord = RunRecordEntity(
                        runId = runId,
                        taskId = taskId,
                        status = "failed",
                        diagnosisSummary = diagnosis.first,
                        diagnosisSuggestion = diagnosis.second,
                        modelCalls = 0,
                        routeSnapshot = ""
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
        _executionState.value = ExecutionState.Running(taskId, "回放路线", 0f)
        
        // Convert steps back to AutoLXB route JSON
        val routeJson = RouteAdapter.toRouteJson(routeSteps)
        
        val result = bridge.runRoute(taskId, routeJson)
        return when (result) {
            is RouteRunResult.Success -> {
                _executionState.value = ExecutionState.Completed(taskId)
                ExecutionResult.Success(
                    taskId = taskId,
                    routeJson = routeJson,
                    steps = routeSteps,
                    traceLines = emptyList()
                )
            }
            is RouteRunResult.Failed -> {
                _executionState.value = ExecutionState.Error("步骤 ${result.stepIndex}: ${result.reason}")
                ExecutionResult.Failed(
                    taskId = taskId,
                    reason = "步骤 ${result.stepIndex}: ${result.reason}",
                    traceLines = emptyList(),
                    diagnosis = Pair("路线执行失败", result.reason)
                )
            }
            is RouteRunResult.Error -> {
                _executionState.value = ExecutionState.Error(result.message)
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
    
    private suspend fun pollUntilDone(taskId: String): TaskStatusResult.Status {
        return withTimeoutOrNull(120_000L) { // 2 min timeout
            while (true) {
                when (val result = bridge.getTaskStatus(taskId)) {
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
