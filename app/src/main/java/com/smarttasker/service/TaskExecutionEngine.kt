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
 * 
 * 负责执行各种类型的任务
 */
class TaskExecutionEngine(
    private val deviceController: DeviceController,
    private val scriptEngine: ScriptExecutionEngine,
    private val llmManager: LlmManager
) {
    companion object {
        private const val TAG = "TaskEngine"
    }
    
    // 执行状态
    var isExecuting = false
        private set
    
    // 当前任务
    var currentTask: TaskEntity? = null
        private set
    
    // 执行回调
    var callback: TaskExecutionCallback? = null
    
    /**
     * 执行任务
     */
    suspend fun execute(
        task: TaskEntity,
        steps: List<ScriptStepEntity>,
        mode: ExecutionMode = ExecutionMode.SCRIPT
    ): TaskExecutionResult {
        if (isExecuting) {
            return TaskExecutionResult(
                success = false,
                error = "任务正在执行中"
            )
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
    
    /**
     * 使用脚本执行
     */
    private suspend fun executeWithScript(
        steps: List<ScriptStepEntity>
    ): TaskExecutionResult {
        val result = scriptEngine.execute(steps)
        
        return TaskExecutionResult(
            success = result.success,
            error = result.error,
            stepsExecuted = result.stepsExecuted,
            executionMode = ExecutionMode.SCRIPT
        )
    }
    
    /**
     * 使用 LLM 执行
     */
    private suspend fun executeWithLlm(task: TaskEntity): TaskExecutionResult {
        try {
            callback?.onProgress("正在使用 AI 分析任务...")
            
            // 获取当前屏幕信息
            val uiTree = deviceController.getUiTree()
            val currentPackage = deviceController.getCurrentPackage()
            
            // 使用 LLM 规划步骤
            val prompt = """
                任务描述：${task.description}
                当前应用：$currentPackage
                当前 UI 树：
                $uiTree
                
                请分析当前屏幕状态，并规划执行此任务的步骤。
                返回 JSON 格式的步骤列表。
            """.trimIndent()
            
            val schema = """
                {
                    "steps": [
                        {
                            "operation": "TAP|INPUT|SWIPE|WAIT|BACK|HOME",
                            "params": {},
                            "description": "步骤描述"
                        }
                    ]
                }
            """.trimIndent()
            
            val plan = llmManager.completeWithCache(prompt)
            
            // 解析步骤
            val steps = parseLlmSteps(plan)
            
            callback?.onProgress("AI 已规划 ${steps.size} 个步骤")
            
            // 执行步骤
            var executedSteps = 0
            for (step in steps) {
                callback?.onProgress("执行步骤 ${executedSteps + 1}/${steps.size}")
                
                val success = executeLlmStep(step)
                if (!success) {
                    return TaskExecutionResult(
                        success = false,
                        error = "步骤执行失败: ${step.description}",
                        stepsExecuted = executedSteps,
                        executionMode = ExecutionMode.LLM_ONLY
                    )
                }
                
                executedSteps++
                delay(500) // 等待 UI 稳定
            }
            
            return TaskExecutionResult(
                success = true,
                stepsExecuted = executedSteps,
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
    
    /**
     * 自动模式执行
     */
    private suspend fun executeAuto(
        task: TaskEntity,
        steps: List<ScriptStepEntity>
    ): TaskExecutionResult {
        // 首先尝试使用脚本
        if (steps.isNotEmpty()) {
            callback?.onProgress("尝试使用脚本执行...")
            
            val scriptResult = executeWithScript(steps)
            if (scriptResult.success) {
                return scriptResult.copy(executionMode = ExecutionMode.AUTO)
            }
            
            callback?.onProgress("脚本执行失败，切换到 AI 模式...")
        }
        
        // 脚本失败或没有脚本，使用 LLM
        return executeWithLlm(task).copy(executionMode = ExecutionMode.AUTO)
    }
    
    /**
     * 执行 LLM 规划的步骤
     */
    private suspend fun executeLlmStep(step: LlmStep): Boolean {
        return try {
            when (step.operation) {
                "TAP" -> {
                    val x = step.params.optInt("x", 0)
                    val y = step.params.optInt("y", 0)
                    deviceController.tap(x, y)
                }
                "INPUT" -> {
                    val text = step.params.optString("text", "")
                    deviceController.inputText(text)
                }
                "SWIPE" -> {
                    val startX = step.params.optInt("startX", 0)
                    val startY = step.params.optInt("startY", 0)
                    val endX = step.params.optInt("endX", 0)
                    val endY = step.params.optInt("endY", 0)
                    deviceController.swipe(startX, startY, endX, endY)
                }
                "WAIT" -> {
                    val duration = step.params.optLong("duration", 1000)
                    deviceController.wait(duration)
                }
                "BACK" -> {
                    deviceController.pressBack()
                }
                "HOME" -> {
                    deviceController.pressHome()
                }
                else -> {
                    Log.w(TAG, "未知操作: ${step.operation}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "步骤执行失败", e)
            false
        }
    }
    
    /**
     * 解析 LLM 返回的步骤
     */
    private fun parseLlmSteps(response: String): List<LlmStep> {
        return try {
            val json = org.json.JSONObject(response)
            val stepsArray = json.getJSONArray("steps")
            val steps = mutableListOf<LlmStep>()
            
            for (i in 0 until stepsArray.length()) {
                val stepObj = stepsArray.getJSONObject(i)
                steps.add(LlmStep(
                    operation = stepObj.getString("operation"),
                    params = stepObj.optJSONObject("params") ?: org.json.JSONObject(),
                    description = stepObj.optString("description", "")
                ))
            }
            
            steps
        } catch (e: Exception) {
            Log.e(TAG, "解析 LLM 步骤失败", e)
            emptyList()
        }
    }
    
    /**
     * 停止执行
     */
    fun stop() {
        isExecuting = false
        scriptEngine.stop()
    }
}

/**
 * 执行模式
 */
enum class ExecutionMode(val title: String, val description: String) {
    SCRIPT("脚本模式", "优先使用保存的脚本，失败时回退到 LLM"),
    LLM_ONLY("全 LLM 模式", "每次都使用 LLM 执行，不使用脚本"),
    AUTO("自动模式", "智能选择最佳执行方式")
}

/**
 * LLM 步骤
 */
data class LlmStep(
    val operation: String,
    val params: org.json.JSONObject,
    val description: String
)

/**
 * 任务执行结果
 */
data class TaskExecutionResult(
    val success: Boolean,
    val error: String? = null,
    val stepsExecuted: Int = 0,
    val durationMs: Long = 0,
    val executionMode: ExecutionMode = ExecutionMode.SCRIPT
)

/**
 * 任务执行回调
 */
interface TaskExecutionCallback {
    fun onTaskStart(task: TaskEntity)
    fun onTaskComplete(task: TaskEntity, durationMs: Long)
    fun onTaskError(task: TaskEntity, error: String)
    fun onProgress(message: String)
}
