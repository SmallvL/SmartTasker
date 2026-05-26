package com.smarttasker.core.bridge

/**
 * Core Bridge interface - abstraction over AutoLXB lxb-core.
 * All AutoLXB interactions MUST go through this interface.
 */
interface CoreBridge {

    // ===== Status =====

    suspend fun getCoreStatus(): CoreStatusResult

    // ===== Task Execution =====

    suspend fun submitQuickTask(taskPayload: String): TaskSubmitResult
    suspend fun getTaskStatus(taskId: String): TaskStatusResult
    suspend fun cancelTask(taskId: String): CancelResult

    // ===== Route & Trace =====

    suspend fun getLatestRoute(taskId: String): RouteResult
    suspend fun getLatestTrace(taskId: String): TraceResult

    // ===== Route Execution =====

    suspend fun runRoute(taskId: String, routeJson: String): RouteRunResult

    // ===== Device =====

    suspend fun screenshot(): ScreenshotResult
    suspend fun dumpHierarchy(): HierarchyResult
}

// ===== Result Types =====

sealed class CoreStatusResult {
    data class Running(val port: Int, val pid: Int) : CoreStatusResult()
    data class Stopped(val reason: String) : CoreStatusResult()
    data class Error(val code: CoreErrorCode, val message: String) : CoreStatusResult()
}

sealed class TaskSubmitResult {
    data class Accepted(val taskId: String, val runId: String) : TaskSubmitResult()
    data class Error(val code: CoreErrorCode, val message: String) : TaskSubmitResult()
}

sealed class TaskStatusResult {
    data class Status(
        val taskId: String,
        val state: String,    // idle / running / success / failed / cancelled
        val phase: String,
        val detail: String
    ) : TaskStatusResult()
    data class Error(val code: CoreErrorCode, val message: String) : TaskStatusResult()
}

sealed class CancelResult {
    object Success : CancelResult()
    data class Error(val code: CoreErrorCode, val message: String) : CancelResult()
}

sealed class RouteResult {
    data class Found(val routeJson: String) : RouteResult()
    object NotFound : RouteResult()
    data class Error(val code: CoreErrorCode, val message: String) : RouteResult()
}

sealed class TraceResult {
    data class Found(val traceLines: List<String>) : TraceResult()
    object NotFound : TraceResult()
    data class Error(val code: CoreErrorCode, val message: String) : TraceResult()
}

sealed class RouteRunResult {
    data class Success(val durationMs: Long) : RouteRunResult()
    data class Failed(val stepIndex: Int, val reason: String) : RouteRunResult()
    data class Error(val code: CoreErrorCode, val message: String) : RouteRunResult()
}

sealed class ScreenshotResult {
    data class Success(val pngBytes: ByteArray) : ScreenshotResult()
    data class Error(val code: CoreErrorCode, val message: String) : ScreenshotResult()
}

sealed class HierarchyResult {
    data class Success(val xml: String) : HierarchyResult()
    data class Error(val code: CoreErrorCode, val message: String) : HierarchyResult()
}

enum class CoreErrorCode {
    CORE_NOT_RUNNING,
    CORE_START_FAILED,
    ADB_NOT_AVAILABLE,
    ROOT_NOT_AVAILABLE,
    MODEL_NOT_CONFIGURED,
    MODEL_REQUEST_FAILED,
    TASK_SUBMIT_FAILED,
    TASK_TIMEOUT,
    ROUTE_NOT_FOUND,
    TRACE_NOT_FOUND,
    PERMISSION_MISSING,
    DEVICE_DISCONNECTED,
    PROTOCOL_ERROR,
    UNKNOWN_ERROR
}
