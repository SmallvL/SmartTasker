package com.smarttasker.service

import android.util.Log
import com.smarttasker.model.ScriptStepEntity
import com.smarttasker.model.StepOperation
import kotlinx.coroutines.delay

/**
 * Route-Then-Act 引擎
 * 
 * 实现"首次用 AI 学习，后续用脚本回放"的模式
 */
class RouteThenActEngine(
    private val deviceController: DeviceController,
    private val llmManager: LlmManager,
    private val scriptEngine: ScriptExecutionEngine
) {
    companion object {
        private const val TAG = "RouteThenAct"
        private const val MAX_ITERATIONS = 20
    }
    
    /**
     * 执行任务
     * 
     * 如果有保存的路线，优先使用路线回放
     * 如果没有路线或回放失败，使用 LLM 学习并记录路线
     */
    suspend fun execute(
        task: String,
        packageName: String,
        existingRoute: List<ScriptStepEntity>? = null,
        mode: RouteExecutionMode = RouteExecutionMode.AUTO
    ): RouteExecutionResult {
        Log.d(TAG, "执行任务: $task, 模式: $mode")
        
        return when (mode) {
            RouteExecutionMode.AUTO -> executeAuto(task, packageName, existingRoute)
            RouteExecutionMode.ROUTE_ONLY -> executeWithRoute(existingRoute!!)
            RouteExecutionMode.LLM_ONLY -> executeWithLlm(task, packageName)
            RouteExecutionMode.RECORD -> executeAndRecord(task, packageName)
        }
    }
    
    /**
     * 自动模式
     */
    private suspend fun executeAuto(
        task: String,
        packageName: String,
        existingRoute: List<ScriptStepEntity>?
    ): RouteExecutionResult {
        // 首先尝试使用路线
        if (existingRoute != null && existingRoute.isNotEmpty()) {
            Log.d(TAG, "尝试使用路线回放")
            
            val routeResult = executeWithRoute(existingRoute)
            if (routeResult.success) {
                return routeResult.copy(usedRoute = true)
            }
            
            Log.d(TAG, "路线回放失败，切换到 LLM 模式")
        }
        
        // 路线失败或没有路线，使用 LLM
        return executeWithLlm(task, packageName)
    }
    
    /**
     * 使用路线执行
     */
    private suspend fun executeWithRoute(
        route: List<ScriptStepEntity>
    ): RouteExecutionResult {
        val result = scriptEngine.execute(route)
        
        return RouteExecutionResult(
            success = result.success,
            error = result.error,
            stepsExecuted = result.stepsExecuted,
            route = route,
            usedRoute = true,
            fellBackToLlm = false
        )
    }
    
    /**
     * 使用 LLM 执行
     */
    private suspend fun executeWithLlm(
        task: String,
        packageName: String
    ): RouteExecutionResult {
        try {
            val steps = mutableListOf<ScriptStepEntity>()
            var iteration = 0
            
            while (iteration < MAX_ITERATIONS) {
                iteration++
                
                // 截图
                val uiTree = deviceController.getUiTree()
                val currentPackage = deviceController.getCurrentPackage()
                
                // 使用 LLM 规划下一步
                val prompt = """
                    任务描述：$task
                    目标应用：$packageName
                    当前应用：$currentPackage
                    当前 UI 树：
                    $uiTree
                    
                    已执行步骤：${steps.size}
                    
                    请分析当前屏幕状态，并规划下一步操作。
                    如果任务已完成，请返回 {"action": "DONE"}。
                    否则返回下一步操作。
                """.trimIndent()
                
                val schema = """
                    {
                        "action": "TAP|INPUT|SWIPE|WAIT|BACK|HOME|DONE",
                        "params": {},
                        "description": "步骤描述",
                        "expected": "预期结果"
                    }
                """.trimIndent()
                
                val response = llmManager.completeWithCache(prompt)
                val action = parseAction(response)
                
                if (action.action == "DONE") {
                    Log.d(TAG, "任务完成")
                    return RouteExecutionResult(
                        success = true,
                        stepsExecuted = steps.size,
                        route = steps,
                        usedRoute = false,
                        fellBackToLlm = true
                    )
                }
                
                // 执行步骤
                val step = createStep(steps.size, action)
                val success = executeStep(step)
                
                if (success) {
                    steps.add(step)
                    delay(500) // 等待 UI 稳定
                } else {
                    Log.e(TAG, "步骤执行失败: ${action.description}")
                    return RouteExecutionResult(
                        success = false,
                        error = "步骤执行失败: ${action.description}",
                        stepsExecuted = steps.size,
                        route = steps,
                        usedRoute = false,
                        fellBackToLlm = true
                    )
                }
            }
            
            return RouteExecutionResult(
                success = false,
                error = "达到最大迭代次数",
                stepsExecuted = steps.size,
                route = steps,
                usedRoute = false,
                fellBackToLlm = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "LLM 执行失败", e)
            return RouteExecutionResult(
                success = false,
                error = e.message,
                usedRoute = false,
                fellBackToLlm = true
            )
        }
    }
    
    /**
     * 执行并记录
     */
    private suspend fun executeAndRecord(
        task: String,
        packageName: String
    ): RouteExecutionResult {
        // 使用 LLM 执行并记录路线
        return executeWithLlm(task, packageName)
    }
    
    /**
     * 执行步骤
     */
    private suspend fun executeStep(step: ScriptStepEntity): Boolean {
        return try {
            when (step.operation) {
                StepOperation.TAP -> {
                    val params = org.json.JSONObject(step.params)
                    val x = params.optInt("x", 0)
                    val y = params.optInt("y", 0)
                    deviceController.tap(x, y)
                }
                StepOperation.INPUT -> {
                    val params = org.json.JSONObject(step.params)
                    val text = params.optString("text", "")
                    deviceController.inputText(text)
                }
                StepOperation.SWIPE -> {
                    val params = org.json.JSONObject(step.params)
                    val startX = params.optInt("startX", 0)
                    val startY = params.optInt("startY", 0)
                    val endX = params.optInt("endX", 0)
                    val endY = params.optInt("endY", 0)
                    deviceController.swipe(startX, startY, endX, endY)
                }
                StepOperation.WAIT -> {
                    val params = org.json.JSONObject(step.params)
                    val duration = params.optLong("duration", 1000)
                    deviceController.wait(duration)
                }
                StepOperation.BACK -> {
                    deviceController.pressBack()
                }
                StepOperation.HOME -> {
                    deviceController.pressHome()
                }
                else -> false
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "步骤执行失败", e)
            false
        }
    }
    
    /**
     * 创建步骤
     */
    private fun createStep(index: Int, action: LlmAction): ScriptStepEntity {
        val operation = when (action.action) {
            "TAP" -> StepOperation.TAP
            "INPUT" -> StepOperation.INPUT
            "SWIPE" -> StepOperation.SWIPE
            "WAIT" -> StepOperation.WAIT
            "BACK" -> StepOperation.BACK
            "HOME" -> StepOperation.HOME
            else -> StepOperation.TAP
        }
        
        return ScriptStepEntity(
            id = "step_$index",
            scriptId = "temp",
            stepIndex = index,
            operation = operation,
            params = action.params.toString(),
            description = action.description,
            semanticNote = action.description,
            expected = action.expected
        )
    }
    
    /**
     * 解析动作
     */
    private fun parseAction(response: String): LlmAction {
        return try {
            val json = org.json.JSONObject(response)
            LlmAction(
                action = json.getString("action"),
                params = json.optJSONObject("params") ?: org.json.JSONObject(),
                description = json.optString("description", ""),
                expected = json.optString("expected", "")
            )
        } catch (e: Exception) {
            Log.e(TAG, "解析动作失败", e)
            LlmAction("DONE")
        }
    }
}

/**
 * 路线执行模式
 */
enum class RouteExecutionMode {
    AUTO,        // 自动模式：优先回放，失败回退 LLM
    ROUTE_ONLY,  // 仅回放
    LLM_ONLY,    // 仅 LLM
    RECORD       // 录制模式
}

/**
 * LLM 动作
 */
data class LlmAction(
    val action: String,
    val params: org.json.JSONObject = org.json.JSONObject(),
    val description: String = "",
    val expected: String = ""
)

/**
 * 路线执行结果
 */
data class RouteExecutionResult(
    val success: Boolean,
    val error: String? = null,
    val stepsExecuted: Int = 0,
    val route: List<ScriptStepEntity>? = null,
    val usedRoute: Boolean = false,
    val fellBackToLlm: Boolean = false
)
