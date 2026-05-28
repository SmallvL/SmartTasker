package com.smarttasker.core.condition

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 条件类型枚举
 */
enum class ConditionType {
    SCREEN_ON,          // 屏幕亮起
    SCREEN_OFF,         // 屏幕熄灭
    APP_RUNNING,        // 应用运行中
    APP_NOT_RUNNING,    // 应用未运行
    NETWORK_CONNECTED,  // 网络已连接
    NETWORK_DISCONNECTED, // 网络断开
    WIFI_CONNECTED,     // WiFi已连接
    MOBILE_CONNECTED,   // 移动数据已连接
    BATTERY_ABOVE,      // 电量高于阈值
    BATTERY_BELOW,      // 电量低于阈值
    TIME_BETWEEN,       // 时间在范围内
    DAY_OF_WEEK         // 星期几
}

/**
 * 条件检查器接口
 */
interface ConditionChecker {
    suspend fun check(condition: Condition): Boolean
}

/**
 * 条件数据类
 */
data class Condition(
    val type: ConditionType,
    val params: Map<String, Any> = emptyMap()
)

/**
 * 条件执行器
 */
class ConditionalExecutor private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "ConditionalExecutor"
        
        @Volatile
        private var instance: ConditionalExecutor? = null
        
        fun getInstance(context: Context): ConditionalExecutor {
            return instance ?: synchronized(this) {
                instance ?: ConditionalExecutor(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val checkers = mutableMapOf<ConditionType, ConditionChecker>()
    private val _lastResult = MutableStateFlow<ConditionCheckResult?>(null)
    val lastResult: StateFlow<ConditionCheckResult?> = _lastResult.asStateFlow()
    
    init {
        // 注册内置检查器
        registerChecker(ConditionType.SCREEN_ON, ScreenConditionChecker(context))
        registerChecker(ConditionType.SCREEN_OFF, ScreenConditionChecker(context))
        registerChecker(ConditionType.NETWORK_CONNECTED, NetworkConditionChecker(context))
        registerChecker(ConditionType.NETWORK_DISCONNECTED, NetworkConditionChecker(context))
        registerChecker(ConditionType.WIFI_CONNECTED, NetworkConditionChecker(context))
        registerChecker(ConditionType.MOBILE_CONNECTED, NetworkConditionChecker(context))
        registerChecker(ConditionType.BATTERY_ABOVE, BatteryConditionChecker(context))
        registerChecker(ConditionType.BATTERY_BELOW, BatteryConditionChecker(context))
    }
    
    /**
     * 注册条件检查器
     */
    fun registerChecker(type: ConditionType, checker: ConditionChecker) {
        checkers[type] = checker
        Log.d(TAG, "注册检查器: $type")
    }
    
    /**
     * 执行条件检查
     */
    suspend fun checkCondition(condition: Condition): ConditionCheckResult {
        val checker = checkers[condition.type]
        
        if (checker == null) {
            val result = ConditionCheckResult(
                condition = condition,
                success = false,
                message = "未找到条件检查器: ${condition.type}"
            )
            _lastResult.value = result
            return result
        }
        
        return try {
            val success = checker.check(condition)
            val result = ConditionCheckResult(
                condition = condition,
                success = success,
                message = if (success) "条件满足" else "条件不满足"
            )
            _lastResult.value = result
            Log.d(TAG, "条件检查: ${condition.type} -> $success")
            result
        } catch (e: Exception) {
            val result = ConditionCheckResult(
                condition = condition,
                success = false,
                message = "检查失败: ${e.message}"
            )
            _lastResult.value = result
            Log.e(TAG, "条件检查失败", e)
            result
        }
    }
    
    /**
     * 批量检查条件
     */
    suspend fun checkConditions(conditions: List<Condition>): List<ConditionCheckResult> {
        return conditions.map { checkCondition(it) }
    }
    
    /**
     * 检查所有条件是否满足
     */
    suspend fun allConditionsMet(conditions: List<Condition>): Boolean {
        val results = checkConditions(conditions)
        return results.all { it.success }
    }
}

/**
 * 条件检查结果
 */
data class ConditionCheckResult(
    val condition: Condition,
    val success: Boolean,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 屏幕状态检查器
 */
class ScreenConditionChecker(private val context: Context) : ConditionChecker {
    
    override suspend fun check(condition: Condition): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        
        return when (condition.type) {
            ConditionType.SCREEN_ON -> powerManager.isInteractive
            ConditionType.SCREEN_OFF -> !powerManager.isInteractive
            else -> false
        }
    }
}

/**
 * 网络状态检查器
 */
class NetworkConditionChecker(private val context: Context) : ConditionChecker {
    
    override suspend fun check(condition: Condition): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        
        val isConnected = capabilities != null
        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isMobile = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        
        return when (condition.type) {
            ConditionType.NETWORK_CONNECTED -> isConnected
            ConditionType.NETWORK_DISCONNECTED -> !isConnected
            ConditionType.WIFI_CONNECTED -> isWifi
            ConditionType.MOBILE_CONNECTED -> isMobile
            else -> false
        }
    }
}

/**
 * 电量状态检查器
 */
class BatteryConditionChecker(private val context: Context) : ConditionChecker {
    
    override suspend fun check(condition: Condition): Boolean {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        
        val threshold = (condition.params["threshold"] as? Int) ?: 50
        
        return when (condition.type) {
            ConditionType.BATTERY_ABOVE -> batteryLevel > threshold
            ConditionType.BATTERY_BELOW -> batteryLevel < threshold
            else -> false
        }
    }
}
