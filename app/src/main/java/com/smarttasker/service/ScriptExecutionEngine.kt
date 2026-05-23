package com.smarttasker.service

import android.util.Log
import com.smarttasker.model.ScriptStepEntity
import com.smarttasker.model.StepOperation
import kotlinx.coroutines.delay

/**
 * 脚本执行引擎
 * 
 * 负责执行脚本步骤
 */
class ScriptExecutionEngine(
    private val deviceController: DeviceController
) {
    companion object {
        private const val TAG = "ScriptEngine"
        private const val DEFAULT_SETTLE_DELAY = 500L
    }
    
    // 执行状态
    var isExecuting = false
        private set
    
    // 当前步骤索引
    var currentStepIndex = -1
        private set
    
    // 执行回调
    var callback: ScriptExecutionCallback? = null
    
    /**
     * 执行脚本
     */
    suspend fun execute(
        steps: List<ScriptStepEntity>,
        settleDelayMs: Long = DEFAULT_SETTLE_DELAY
    ): ScriptExecutionResult {
        if (isExecuting) {
            return ScriptExecutionResult(
                success = false,
                error = "脚本正在执行中",
                stepsExecuted = 0
            )
        }
        
        isExecuting = true
        currentStepIndex = 0
        val executedSteps = mutableListOf<StepExecutionResult>()
        
        try {
            for ((index, step) in steps.withIndex()) {
                currentStepIndex = index
                callback?.onStepStart(index, step)
                
                val result = executeStep(step)
                executedSteps.add(result)
                
                if (!result.success) {
                    callback?.onStepError(index, step, result.error ?: "未知错误")
                    
                    return ScriptExecutionResult(
                        success = false,
                        error = result.error,
                        stepsExecuted = index,
                        stepResults = executedSteps
                    )
                }
                
                callback?.onStepComplete(index, step)
                
                // 等待 UI 稳定
                if (settleDelayMs > 0) {
                    delay(settleDelayMs)
                }
            }
            
            callback?.onComplete()
            
            return ScriptExecutionResult(
                success = true,
                stepsExecuted = steps.size,
                stepResults = executedSteps
            )
        } catch (e: Exception) {
            Log.e(TAG, "脚本执行失败", e)
            callback?.onError(e)
            
            return ScriptExecutionResult(
                success = false,
                error = e.message,
                stepsExecuted = currentStepIndex,
                stepResults = executedSteps
            )
        } finally {
            isExecuting = false
            currentStepIndex = -1
        }
    }
    
    /**
     * 执行单个步骤
     */
    private suspend fun executeStep(step: ScriptStepEntity): StepExecutionResult {
        return try {
            val startTime = System.currentTimeMillis()
            
            when (step.operation) {
                StepOperation.TAP -> {
                    val params = parseTapParams(step.params)
                    val success = if (params.resourceId != null || params.text != null) {
                        deviceController.clickElement(
                            resourceId = params.resourceId,
                            text = params.text
                        )
                    } else {
                        deviceController.tap(params.x, params.y)
                    }
                    
                    StepExecutionResult(
                        success = success,
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
                
                StepOperation.LONG_PRESS -> {
                    val params = parseTapParams(step.params)
                    val success = deviceController.longPress(
                        params.x,
                        params.y,
                        params.duration
                    )
                    
                    StepExecutionResult(
                        success = success,
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
                
                StepOperation.SWIPE -> {
                    val params = parseSwipeParams(step.params)
                    val success = deviceController.swipe(
                        params.startX,
                        params.startY,
                        params.endX,
                        params.endY,
                        params.duration
                    )
                    
                    StepExecutionResult(
                        success = success,
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
                
                StepOperation.INPUT -> {
                    val params = parseInputParams(step.params)
                    val success = if (params.resourceId != null || params.textMatch != null) {
                        deviceController.inputToElement(
                            text = params.text,
                            resourceId = params.resourceId,
                            textMatch = params.textMatch
                        )
                    } else {
                        deviceController.inputText(params.text)
                    }
                    
                    StepExecutionResult(
                        success = success,
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
                
                StepOperation.WAIT -> {
                    val params = parseWaitParams(step.params)
                    deviceController.wait(params.duration)
                    
                    StepExecutionResult(
                        success = true,
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
                
                StepOperation.BACK -> {
                    val success = deviceController.pressBack()
                    
                    StepExecutionResult(
                        success = success,
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
                
                StepOperation.HOME -> {
                    val success = deviceController.pressHome()
                    
                    StepExecutionResult(
                        success = success,
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
                
                StepOperation.LAUNCH_APP -> {
                    val params = parseLaunchParams(step.params)
                    val success = deviceController.launchApp(params.packageName)
                    
                    StepExecutionResult(
                        success = success,
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
                
                StepOperation.WAIT_FOR_ELEMENT -> {
                    val params = parseWaitForElementParams(step.params)
                    val startTimeMs = System.currentTimeMillis()
                    var found = false
                    
                    while (System.currentTimeMillis() - startTimeMs < params.timeout) {
                        val element = deviceController.findElement(
                            resourceId = params.resourceId,
                            text = params.text,
                            contentDescription = params.contentDescription
                        )
                        
                        if (element != null) {
                            found = true
                            break
                        }
                        
                        delay(500)
                    }
                    
                    StepExecutionResult(
                        success = found,
                        durationMs = System.currentTimeMillis() - startTime,
                        error = if (!found) "等待元素超时" else null
                    )
                }
                
                StepOperation.VERIFY -> {
                    val params = parseVerifyParams(step.params)
                    val element = deviceController.findElement(
                        resourceId = params.resourceId,
                        text = params.text,
                        contentDescription = params.contentDescription
                    )
                    
                    val success = element != null
                    
                    StepExecutionResult(
                        success = success,
                        durationMs = System.currentTimeMillis() - startTime,
                        error = if (!success) "验证失败：元素不存在" else null
                    )
                }
                
                StepOperation.SCROLL -> {
                    // 滚动实现
                    StepExecutionResult(
                        success = false,
                        error = "滚动操作暂未实现"
                    )
                }
                
                StepOperation.COMMAND -> {
                    // 自定义命令实现
                    StepExecutionResult(
                        success = false,
                        error = "自定义命令暂未实现"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "步骤执行失败", e)
            StepExecutionResult(
                success = false,
                error = e.message
            )
        }
    }
    
    /**
     * 停止执行
     */
    fun stop() {
        isExecuting = false
    }
    
    // ============================================================
    // 参数解析
    // ============================================================
    
    private fun parseTapParams(json: String): TapParams {
        return try {
            val obj = org.json.JSONObject(json)
            TapParams(
                x = obj.optInt("x", 0),
                y = obj.optInt("y", 0),
                resourceId = obj.optString("resourceId", null),
                text = obj.optString("text", null),
                duration = obj.optLong("duration", 100)
            )
        } catch (e: Exception) {
            TapParams()
        }
    }
    
    private fun parseSwipeParams(json: String): SwipeParams {
        return try {
            val obj = org.json.JSONObject(json)
            SwipeParams(
                startX = obj.optInt("startX", 0),
                startY = obj.optInt("startY", 0),
                endX = obj.optInt("endX", 0),
                endY = obj.optInt("endY", 0),
                duration = obj.optLong("duration", 300)
            )
        } catch (e: Exception) {
            SwipeParams()
        }
    }
    
    private fun parseInputParams(json: String): InputParams {
        return try {
            val obj = org.json.JSONObject(json)
            InputParams(
                text = obj.optString("text", ""),
                resourceId = obj.optString("resourceId", null),
                textMatch = obj.optString("textMatch", null)
            )
        } catch (e: Exception) {
            InputParams()
        }
    }
    
    private fun parseWaitParams(json: String): WaitParams {
        return try {
            val obj = org.json.JSONObject(json)
            WaitParams(
                duration = obj.optLong("duration", 1000)
            )
        } catch (e: Exception) {
            WaitParams()
        }
    }
    
    private fun parseLaunchParams(json: String): LaunchParams {
        return try {
            val obj = org.json.JSONObject(json)
            LaunchParams(
                packageName = obj.optString("packageName", "")
            )
        } catch (e: Exception) {
            LaunchParams()
        }
    }
    
    private fun parseWaitForElementParams(json: String): WaitForElementParams {
        return try {
            val obj = org.json.JSONObject(json)
            WaitForElementParams(
                resourceId = obj.optString("resourceId", null),
                text = obj.optString("text", null),
                contentDescription = obj.optString("contentDescription", null),
                timeout = obj.optLong("timeout", 10000)
            )
        } catch (e: Exception) {
            WaitForElementParams()
        }
    }
    
    private fun parseVerifyParams(json: String): VerifyParams {
        return try {
            val obj = org.json.JSONObject(json)
            VerifyParams(
                resourceId = obj.optString("resourceId", null),
                text = obj.optString("text", null),
                contentDescription = obj.optString("contentDescription", null)
            )
        } catch (e: Exception) {
            VerifyParams()
        }
    }
    
    // ============================================================
    // 参数数据类
    // ============================================================
    
    data class TapParams(
        val x: Int = 0,
        val y: Int = 0,
        val resourceId: String? = null,
        val text: String? = null,
        val duration: Long = 100
    )
    
    data class SwipeParams(
        val startX: Int = 0,
        val startY: Int = 0,
        val endX: Int = 0,
        val endY: Int = 0,
        val duration: Long = 300
    )
    
    data class InputParams(
        val text: String = "",
        val resourceId: String? = null,
        val textMatch: String? = null
    )
    
    data class WaitParams(
        val duration: Long = 1000
    )
    
    data class LaunchParams(
        val packageName: String = ""
    )
    
    data class WaitForElementParams(
        val resourceId: String? = null,
        val text: String? = null,
        val contentDescription: String? = null,
        val timeout: Long = 10000
    )
    
    data class VerifyParams(
        val resourceId: String? = null,
        val text: String? = null,
        val contentDescription: String? = null
    )
}

/**
 * 脚本执行结果
 */
data class ScriptExecutionResult(
    val success: Boolean,
    val error: String? = null,
    val stepsExecuted: Int,
    val stepResults: List<StepExecutionResult> = emptyList()
)

/**
 * 步骤执行结果
 */
data class StepExecutionResult(
    val success: Boolean,
    val durationMs: Long = 0,
    val error: String? = null
)

/**
 * 脚本执行回调
 */
interface ScriptExecutionCallback {
    fun onStepStart(index: Int, step: ScriptStepEntity)
    fun onStepComplete(index: Int, step: ScriptStepEntity)
    fun onStepError(index: Int, step: ScriptStepEntity, error: String)
    fun onComplete()
    fun onError(error: Exception)
}
