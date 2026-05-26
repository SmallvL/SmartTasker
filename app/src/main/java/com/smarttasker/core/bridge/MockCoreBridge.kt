package com.smarttasker.core.bridge

/**
 * Mock CoreBridge for UI development and testing without lxb-core.
 * IMPORTANT: Mock always returns Stopped status and marks all results as mock.
 * This prevents users from thinking the app is actually working.
 */
class MockCoreBridge : CoreBridge {

    override suspend fun getCoreStatus(): CoreStatusResult {
        // Mock NEVER pretends to be running
        return CoreStatusResult.Stopped("Mock 模式：未连接真实 Core")
    }

    override suspend fun submitQuickTask(taskPayload: String): TaskSubmitResult {
        return TaskSubmitResult.Error(
            CoreErrorCode.CORE_NOT_RUNNING,
            "Mock 模式：无法执行任务，请先连接真实 Core"
        )
    }

    override suspend fun getTaskStatus(taskId: String): TaskStatusResult {
        return TaskStatusResult.Error(
            CoreErrorCode.CORE_NOT_RUNNING,
            "Mock 模式：无真实任务状态"
        )
    }

    override suspend fun cancelTask(taskId: String): CancelResult {
        return CancelResult.Error(CoreErrorCode.CORE_NOT_RUNNING, "Mock 模式")
    }

    override suspend fun getLatestRoute(taskId: String): RouteResult {
        return RouteResult.Error(CoreErrorCode.ROUTE_NOT_FOUND, "Mock 模式：无真实路由")
    }

    override suspend fun getLatestTrace(taskId: String): TraceResult {
        return TraceResult.Error(CoreErrorCode.TRACE_NOT_FOUND, "Mock 模式：无真实 Trace")
    }

    override suspend fun runRoute(taskId: String, routeJson: String): RouteRunResult {
        return RouteRunResult.Error(CoreErrorCode.CORE_NOT_RUNNING, "Mock 模式：无法执行路由")
    }

    override suspend fun screenshot(): ScreenshotResult {
        return ScreenshotResult.Error(CoreErrorCode.CORE_NOT_RUNNING, "Mock 模式：无法截图")
    }

    override suspend fun dumpHierarchy(): HierarchyResult {
        return HierarchyResult.Error(CoreErrorCode.CORE_NOT_RUNNING, "Mock 模式：无法获取页面结构")
    }
}
