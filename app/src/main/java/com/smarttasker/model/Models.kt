package com.smarttasker.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

// ============================================================
// 任务相关模型
// ============================================================

/**
 * 任务实体
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val type: TaskType,
    val scriptId: String? = null,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    
    // 定时任务配置
    val cronExpression: String? = null,
    val intervalMinutes: Long? = null,
    val scheduledTime: Long? = null,
    
    // 触发任务配置
    val triggerType: TriggerType? = null,
    val triggerConfig: String? = null
)

/**
 * 任务类型
 */
enum class TaskType(val title: String) {
    SINGLE("单次任务"),
    SCHEDULED("定时任务"),
    TRIGGERED("触发任务")
}

/**
 * 触发类型
 */
enum class TriggerType {
    NOTIFICATION,  // 通知触发
    APP_OPEN,      // 应用打开触发
    APP_CLOSE,     // 应用关闭触发
    TIME           // 时间触发
}

/**
 * 任务执行记录
 */
@Entity(tableName = "task_executions")
data class TaskExecutionEntity(
    @PrimaryKey
    val id: String,
    val taskId: String,
    val startTime: Long,
    val endTime: Long? = null,
    val status: ExecutionStatus,
    val result: String? = null,
    val error: String? = null,
    val stepsExecuted: Int = 0,
    val totalSteps: Int = 0
)

/**
 * 执行状态
 */
enum class ExecutionStatus {
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED,
    TIMEOUT
}

// ============================================================
// 脚本相关模型
// ============================================================

/**
 * 脚本实体
 */
@Entity(tableName = "scripts")
data class ScriptEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val isAiGenerated: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Int = 1
)

/**
 * 脚本步骤
 */
@Entity(tableName = "script_steps")
data class ScriptStepEntity(
    @PrimaryKey
    val id: String,
    val scriptId: String,
    val stepIndex: Int,
    val operation: StepOperation,
    val params: String,  // JSON 格式
    val description: String,
    val semanticNote: String = "",
    val expected: String = "",
    val fallbackPoint: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 步骤操作类型
 */
enum class StepOperation {
    TAP,              // 点击
    LONG_PRESS,       // 长按
    SWIPE,            // 滑动
    INPUT,            // 输入文本
    WAIT,             // 等待
    BACK,             // 返回键
    HOME,             // Home 键
    SCROLL,           // 滚动
    LAUNCH_APP,       // 启动应用
    WAIT_FOR_ELEMENT, // 等待元素出现
    VERIFY,           // 验证条件
    COMMAND           // 自定义命令
}

// ============================================================
// 路线相关模型
// ============================================================

/**
 * 路线实体
 */
@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey
    val routeId: String,
    val scriptId: String,
    val packageName: String,
    val userTask: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastReplayStatus: ReplayStatus = ReplayStatus.UNKNOWN,
    val finishAfterReplay: Boolean = true,
    val stepCount: Int = 0,
    val segmentCount: Int = 0
)

/**
 * 回放状态
 */
enum class ReplayStatus {
    UNKNOWN,
    SUCCESS,
    FAILED,
    PARTIAL
}

// ============================================================
// 设置相关模型
// ============================================================

/**
 * 应用设置
 */
data class AppSettings(
    val isDarkMode: Boolean = false,
    val language: String = "zh-CN",
    val isDebugMode: Boolean = false,
    val operationMode: OperationMode = OperationMode.SCRIPT,
    val llmProvider: String = "openai",
    val llmModel: String = "gpt-4-vision-preview",
    val llmApiKey: String = "",
    val llmBaseUrl: String = "",
    val autoSaveRoute: Boolean = true,
    val fallbackToLlm: Boolean = true,
    val maxRetries: Int = 3,
    val timeoutMs: Long = 300_000,
    val settleDelayMs: Long = 1_000,
    val enableNotifications: Boolean = true,
    val enableBootStart: Boolean = false
)

/**
 * 操作模式
 */
enum class OperationMode(val title: String, val description: String) {
    SCRIPT("脚本模式", "优先使用保存的脚本，失败时回退到 LLM"),
    LLM_ONLY("全 LLM 模式", "每次都使用 LLM 执行，不使用脚本"),
    AUTO("自动模式", "智能选择最佳执行方式")
}

// ============================================================
// UI 状态模型
// ============================================================

/**
 * 首页 UI 状态
 */
data class HomeUiState(
    val serviceStatus: ServiceStatus = ServiceStatus(),
    val guideSteps: List<GuideStep> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * 服务状态
 */
data class ServiceStatus(
    val isRunning: Boolean = false,
    val isPaired: Boolean = false,
    val isConnected: Boolean = false,
    val message: String = "服务未启动"
)

/**
 * 引导步骤
 */
data class GuideStep(
    val id: Int,
    val title: String,
    val description: String,
    val isCompleted: Boolean = false,
    val isCurrent: Boolean = false
)

/**
 * 任务页面 UI 状态
 */
data class TaskUiState(
    val tasks: List<TaskItem> = emptyList(),
    val scripts: List<ScriptItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * 任务项
 */
data class TaskItem(
    val id: String,
    val name: String,
    val description: String,
    val type: TaskType,
    val isEnabled: Boolean,
    val lastRunTime: Long? = null
)

/**
 * 脚本项
 */
data class ScriptItem(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val stepCount: Int,
    val lastModified: Long
)

/**
 * 设置页面 UI 状态
 */
data class SettingsUiState(
    val isDarkMode: Boolean = false,
    val language: String = "中文",
    val isDebugMode: Boolean = false,
    val operationMode: String = "脚本模式",
    val llmProvider: String = "OpenAI",
    val appVersion: String = "1.0.0",
    val isLoading: Boolean = false,
    val error: String? = null
)
