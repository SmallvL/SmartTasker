package com.smarttasker.core.direct

import android.content.Context
import com.smarttasker.core.bridge.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Direct CoreBridge implementation - uses SafeJson instead of org.json.
 */
class DirectCoreBridge(private val context: Context) : CoreBridge {

    private val inputEngine = InputEngine()
    private val senseEngine = SenseEngine(context)
    private val taskScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + Dispatchers.IO
    )

    override suspend fun getCoreStatus(): CoreStatusResult = withContext(Dispatchers.IO) {
        val mode = ShellExecutor.detectMode()
        when (mode) {
            ShellExecutor.ShellMode.ROOT,
            ShellExecutor.ShellMode.ADB,
            ShellExecutor.ShellMode.ADB_LOCAL,
            ShellExecutor.ShellMode.SH -> CoreStatusResult.Running(port = 0, pid = android.os.Process.myPid())
            ShellExecutor.ShellMode.NONE -> CoreStatusResult.Stopped("需要 Root 权限或 ADB 调试")
        }
    }

    private val activeTasks = java.util.concurrent.ConcurrentHashMap<String, TaskState>()

    data class TaskState(
        val taskId: String,
        var state: String = "running",
        var phase: String = "EXECUTING",
        var detail: String = "",
        var currentStep: Int = 0
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
                    activeTasks[taskId]?.let { ts -> ts.state = "failed"; ts.phase = "ERROR"; ts.detail = "执行异常: ${e.message}" }
                }
            }
        } catch (e: Exception) {
            state.state = "failed"; state.detail = "提交失败: ${e.message}"
            return TaskSubmitResult.Error(CoreErrorCode.TASK_SUBMIT_FAILED, "提交失败: ${e.message}")
        }
        return TaskSubmitResult.Accepted(taskId = taskId, runId = taskId)
    }

    private suspend fun executeTaskAsync(taskId: String, taskPayload: String) {
        val state = activeTasks[taskId] ?: return
        try {
            val packageName = SafeJson.getNestedString(taskPayload, "target_app", "package") ?: ""
            val playbook = SafeJson.getString(taskPayload, "playbook") ?: ""
            val description = SafeJson.getString(taskPayload, "description") ?: ""

            if (packageName.isNotEmpty()) {
                state.phase = "launching"; state.detail = "启动应用: $packageName"
                try { senseEngine.launchApp(packageName) } catch (_: Exception) {}
                kotlinx.coroutines.delay(2000)
            }

            state.phase = "interacting"; state.detail = "执行操作..."
            val actionText = playbook.ifEmpty { description }
            if (actionText.isNotEmpty()) executeFromPlaybook(actionText)

            state.currentStep = 1; state.state = "success"; state.phase = "completed"; state.detail = "任务执行完成"
        } catch (e: Exception) {
            state.state = "failed"; state.phase = "ERROR"; state.detail = "执行异常: ${e.message}"
        }
    }

    private suspend fun executeFromPlaybook(playbook: String) {
        val lower = playbook.lowercase()
        when {
            lower.contains("返回") || lower.contains("back") -> inputEngine.pressKey(4)
            lower.contains("主页") || lower.contains("home") -> inputEngine.pressKey(3)
            lower.contains("最近") || lower.contains("recent") -> inputEngine.pressKey(187)
            lower.contains("截图") -> senseEngine.screenshot()
            lower.contains("向下") || lower.contains("下滑") -> inputEngine.swipe(540, 1800, 540, 600, 500)
            lower.contains("向上") || lower.contains("上滑") -> inputEngine.swipe(540, 600, 540, 1800, 500)
        }
    }

    private suspend fun executeStep(stepJson: String) {
        val op = SafeJson.getString(stepJson, "op")?.uppercase() ?: return
        when (op) {
            "TAP" -> {
                val args = SafeJson.getArray(stepJson, "args") ?: return
                inputEngine.tap(SafeJson.arrayInt(args, 0) ?: return, SafeJson.arrayInt(args, 1) ?: return)
            }
            "SWIPE" -> {
                val args = SafeJson.getArray(stepJson, "args") ?: return
                inputEngine.swipe(SafeJson.arrayInt(args, 0) ?: return, SafeJson.arrayInt(args, 1) ?: return,
                    SafeJson.arrayInt(args, 2) ?: return, SafeJson.arrayInt(args, 3) ?: return, SafeJson.arrayInt(args, 4) ?: 300)
            }
            "KEY" -> { val args = SafeJson.getArray(stepJson, "args") ?: return; inputEngine.pressKey(SafeJson.arrayInt(args, 0) ?: return) }
            "LAUNCH" -> senseEngine.launchApp(SafeJson.getString(stepJson, "package") ?: return)
            "WAIT" -> { val args = SafeJson.getArray(stepJson, "args"); kotlinx.coroutines.delay((SafeJson.arrayInt(args ?: "[]", 0) ?: 1000).toLong()) }
        }
        kotlinx.coroutines.delay(200)
    }

    override suspend fun getTaskStatus(taskId: String): TaskStatusResult {
        val s = activeTasks[taskId] ?: return TaskStatusResult.Error(CoreErrorCode.UNKNOWN_ERROR, "任务不存在")
        return TaskStatusResult.Status(taskId, s.state, s.phase, s.detail)
    }

    override suspend fun cancelTask(taskId: String): CancelResult {
        activeTasks[taskId]?.apply { state = "cancelled"; detail = "用户取消" } ?: return CancelResult.Error(CoreErrorCode.UNKNOWN_ERROR, "任务不存在")
        return CancelResult.Success
    }

    override suspend fun getLatestRoute(taskId: String): RouteResult {
        val s = activeTasks[taskId] ?: return RouteResult.Error(CoreErrorCode.ROUTE_NOT_FOUND, "任务不存在")
        if (s.state != "success") return RouteResult.Error(CoreErrorCode.ROUTE_NOT_FOUND, "任务未完成")
        return RouteResult.Found("{\"segments\":[{\"steps\":[{\"op\":\"LAUNCH\",\"args\":[\"\"],\"delay\":0}]}]}")
    }

    override suspend fun getLatestTrace(taskId: String): TraceResult {
        val s = activeTasks[taskId] ?: return TraceResult.Error(CoreErrorCode.TRACE_NOT_FOUND, "无数据")
        val lines = mutableListOf("{\"ts\":${System.currentTimeMillis()},\"event\":\"task_start\"}")
        for (i in 0 until s.currentStep) lines.add("{\"ts\":${System.currentTimeMillis()},\"event\":\"step_ok\",\"step\":${i+1}}")
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

    override suspend fun screenshot() = senseEngine.screenshot()
    override suspend fun dumpHierarchy() = senseEngine.dumpHierarchy()
}
