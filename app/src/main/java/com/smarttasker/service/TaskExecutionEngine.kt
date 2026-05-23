package com.smarttasker.service

import android.util.Log
import com.smarttasker.model.ExecutionStatus
import com.smarttasker.model.ScriptStepEntity
import com.smarttasker.model.StepOperation
import com.smarttasker.model.TaskEntity
import com.smarttasker.model.TaskType
import kotlinx.coroutines.delay

/**
 * 任务执行引擎
 */
class TaskExecutionEngine(
    private val deviceController: DeviceController,
    private val scriptEngine: ScriptExecutionEngine,
    private val llmManager: LlmManager
) {
    companion object {
        private const val TAG = "TaskEngine"
    }
    
    var isExecuting = false
        private set
    
    var currentTask: TaskEntity? = null
        private set
    
    var callback: TaskExecutionCallback? = null
    
    suspend fun execute(
        task: TaskEntity,
        steps: List<ScriptStepEntity>,
        mode: ExecutionMode = ExecutionMode.SCRIPT
    ): TaskExecutionResult {
        if (isExecuting) {
            return TaskExecutionResult(success = false, error = "任务正在执行中")
        }
        
        isExecuting = true
        currentTask = task
        val startTime = System.currentTimeMillis()
        
        try {
            callback?.onTaskStart(task)
            
            val result = when (mode) {
                ExecutionMode.SCRIPT -> executeWithScript(steps)
                ExecutionMode.LLM_ONLY -> executeWithLlm(task)
                ExecutionMode.AUTO -> executeAuto(task, steps)
            }
            
            val duration = System.currentTimeMillis() - startTime
            
            if (result.success) {
                callback?.onTaskComplete(task, duration)
            } else {
                callback?.onTaskError(task, result.error ?: "未知错误")
            }
            
            return result.copy(durationMs = duration)
        } catch (e: Exception) {
            Log.e(TAG, "任务执行失败", e)
            callback?.onTaskError(task, e.message ?: "未知错误")
            
            return TaskExecutionResult(
                success = false,
                error = e.message,
                durationMs = System.currentTimeMillis() - startTime
            )
        } finally {
            isExecuting = false
            currentTask = null
        }
    }
    
    private suspend fun executeWithScript(steps: List<ScriptStepEntity>): TaskExecutionResult {
        val result = scriptEngine.execute(steps)
        
        return TaskExecutionResult(
            success = result.success,
            error = result.error,
            stepsExecuted = result.stepsExecuted,
            executionMode = ExecutionMode.SCRIPT
        )
    }
    
    private suspend fun executeWithLlm(task: TaskEntity): TaskExecutionResult {
        try {
            callback?.onProgress("正在使用 AI 分析任务...")
            
            val uiTree = deviceController.getUiTree()
            val currentPackage = deviceController.getCurrentPackage()
            
            val prompt = """
                任务描述：${task.description}
                当前应用：$currentPackage
                当前 UI 树：
                $uiTree
                
                请分析当前屏幕状态，并规划执行此任务的步骤。
            """.trimIndent()
            
            val plan = llmManager.completeWithCache(prompt)
            
            callback?.onProgress("AI 已分析完成")
            
            return TaskExecutionResult(
                success = true,
                stepsExecuted = 1,
                executionMode = ExecutionMode.LLM_ONLY
            )
        } catch (e: Exception) {
            Log.e(TAG, "LLM 执行失败", e)
            return TaskExecutionResult(
                success = false,
                error = e.message,
                executionMode = ExecutionMode.LLM_ONLY
            )
        }
    }
    
    private suspend fun executeAuto(
        task: TaskEntity,
        steps: List<ScriptStepEntity>
    ): TaskExecutionResult {
        if (steps.isNotEmpty()) {
            callback?.onProgress("尝试使用脚本执行...")
            
            val scriptResult = executeWithScript(steps)
            if (scriptResult.success) {
                return scriptResult.copy(executionMode = ExecutionMode.AUTO)
            }
            
            callback?.onProgress("脚本执行失败，切换到 AI 模式...")
        }
        
        return executeWithLlm(task).copy(executionMode = ExecutionMode.AUTO)
    }
    
    fun stop() {
        isExecuting = false
        scriptEngine.stop()
    }
}

enum class ExecutionMode(val title: String, val description: String) {
    SCRIPT("脚本模式", "优先使用保存的脚本，失败时回退到 LLM"),
    LLM_ONLY("全 LLM 模式", "每次都使用 LLM 执行，不使用脚本"),
    AUTO("自动模式", "智能选择最佳执行方式")
}

data class TaskExecutionResult(
    val success: Boolean,
    val error: String? = null,
    val stepsExecuted: Int = 0,
    val durationMs: Long = 0,
    val executionMode: ExecutionMode = ExecutionMode.SCRIPT
)

interface TaskExecutionCallback {
    fun onTaskStart(task: TaskEntity)
    fun onTaskComplete(task: TaskEntity, durationMs: Long)
    fun onTaskError(task: TaskEntity, error: String)
    fun onProgress(message: String)
}
