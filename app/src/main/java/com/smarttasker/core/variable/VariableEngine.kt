package com.smarttasker.core.variable

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

/**
 * 变量类型
 */
enum class VariableType {
    SYSTEM,   // 系统变量
    USER,     // 用户变量
    TASK,     // 任务变量
    CONTEXT   // 上下文变量
}

/**
 * 变量数据
 */
data class Variable(
    val name: String,
    val value: String,
    val type: VariableType,
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 变量引擎
 */
class VariableEngine private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "VariableEngine"
        
        // 系统变量前缀
        const val SYSTEM_PREFIX = "sys."
        const val USER_PREFIX = "user."
        const val TASK_PREFIX = "task."
        const val CONTEXT_PREFIX = "ctx."
        
        @Volatile
        private var instance: VariableEngine? = null
        
        fun getInstance(context: Context): VariableEngine {
            return instance ?: synchronized(this) {
                instance ?: VariableEngine(context.applicationContext).also { instance = it }
            }
        }
    }
    
    // 变量存储
    private val variables = mutableMapOf<String, Variable>()
    
    // 系统变量提供者
    private val systemVariableProviders = mutableMapOf<String, () -> String>()
    
    private val _variableChanges = MutableStateFlow<Variable?>(null)
    val variableChanges: StateFlow<Variable?> = _variableChanges.asStateFlow()
    
    init {
        // 注册系统变量
        registerSystemVariables()
    }
    
    /**
     * 注册系统变量
     */
    private fun registerSystemVariables() {
        // 设备信息
        systemVariableProviders["${SYSTEM_PREFIX}device.model"] = { Build.MODEL }
        systemVariableProviders["${SYSTEM_PREFIX}device.brand"] = { Build.BRAND }
        systemVariableProviders["${SYSTEM_PREFIX}device.android"] = { Build.VERSION.RELEASE }
        systemVariableProviders["${SYSTEM_PREFIX}device.sdk"] = { Build.VERSION.SDK_INT.toString() }
        
        // 时间日期
        systemVariableProviders["${SYSTEM_PREFIX}time.now"] = { 
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()) 
        }
        systemVariableProviders["${SYSTEM_PREFIX}time.date"] = { 
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) 
        }
        systemVariableProviders["${SYSTEM_PREFIX}time.datetime"] = { 
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()) 
        }
        systemVariableProviders["${SYSTEM_PREFIX}time.timestamp"] = { 
            System.currentTimeMillis().toString() 
        }
        
        // 电池信息
        systemVariableProviders["${SYSTEM_PREFIX}battery.level"] = {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).toString()
        }
        systemVariableProviders["${SYSTEM_PREFIX}battery.is_charging"] = {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            batteryManager.isCharging.toString()
        }
        
        // 设备ID
        systemVariableProviders["${SYSTEM_PREFIX}device.id"] = {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        }
        
        // 屏幕信息
        systemVariableProviders["${SYSTEM_PREFIX}screen.width"] = {
            val displayMetrics = context.resources.displayMetrics
            displayMetrics.widthPixels.toString()
        }
        systemVariableProviders["${SYSTEM_PREFIX}screen.height"] = {
            val displayMetrics = context.resources.displayMetrics
            displayMetrics.heightPixels.toString()
        }
        systemVariableProviders["${SYSTEM_PREFIX}screen.density"] = {
            val displayMetrics = context.resources.displayMetrics
            displayMetrics.density.toString()
        }
        
        Log.d(TAG, "注册 ${systemVariableProviders.size} 个系统变量")
    }
    
    /**
     * 设置变量
     */
    fun setVariable(name: String, value: String, type: VariableType, description: String = "") {
        val variable = Variable(name, value, type, description)
        variables[name] = variable
        _variableChanges.value = variable
        Log.d(TAG, "设置变量: $name = $value")
    }
    
    /**
     * 获取变量
     */
    fun getVariable(name: String): Variable? {
        // 先检查用户变量
        variables[name]?.let { return it }
        
        // 再检查系统变量
        systemVariableProviders[name]?.let { provider ->
            val value = provider()
            return Variable(name, value, VariableType.SYSTEM, "系统变量")
        }
        
        return null
    }
    
    /**
     * 删除变量
     */
    fun removeVariable(name: String) {
        variables.remove(name)
        Log.d(TAG, "删除变量: $name")
    }
    
    /**
     * 替换字符串中的变量
     */
    fun replaceVariables(input: String): String {
        var result = input
        
        // 匹配 ${variable.name} 格式
        val regex = Regex("\\$\\{([^}]+)\\}")
        val matches = regex.findAll(input)
        
        for (match in matches) {
            val variableName = match.groupValues[1]
            val variable = getVariable(variableName)
            
            if (variable != null) {
                result = result.replace(match.value, variable.value)
            } else {
                Log.w(TAG, "变量未找到: $variableName")
            }
        }
        
        return result
    }
    
    /**
     * 获取所有变量
     */
    fun getAllVariables(): Map<String, Variable> {
        val allVariables = mutableMapOf<String, Variable>()
        
        // 添加用户变量
        allVariables.putAll(variables)
        
        // 添加系统变量
        systemVariableProviders.forEach { (name, provider) ->
            val value = provider()
            allVariables[name] = Variable(name, value, VariableType.SYSTEM, "系统变量")
        }
        
        return allVariables
    }
    
    /**
     * 获取指定类型的变量
     */
    fun getVariablesByType(type: VariableType): Map<String, Variable> {
        return getAllVariables().filter { it.value.type == type }
    }
    
    /**
     * 清除所有用户变量
     */
    fun clearUserVariables() {
        val keysToRemove = variables.keys.filter { it.startsWith(USER_PREFIX) }
        keysToRemove.forEach { variables.remove(it) }
        Log.d(TAG, "清除 ${keysToRemove.size} 个用户变量")
    }
    
    /**
     * 清除任务变量
     */
    fun clearTaskVariables() {
        val keysToRemove = variables.keys.filter { it.startsWith(TASK_PREFIX) }
        keysToRemove.forEach { variables.remove(it) }
        Log.d(TAG, "清除 ${keysToRemove.size} 个任务变量")
    }
    
    /**
     * 设置任务变量
     */
    fun setTaskVariable(taskId: String, name: String, value: String) {
        val variableName = "${TASK_PREFIX}${taskId}.${name}"
        setVariable(variableName, value, VariableType.TASK, "任务变量")
    }
    
    /**
     * 获取任务变量
     */
    fun getTaskVariable(taskId: String, name: String): String? {
        val variableName = "${TASK_PREFIX}${taskId}.${name}"
        return getVariable(variableName)?.value
    }
    
    /**
     * 设置上下文变量
     */
    fun setContextVariable(name: String, value: String) {
        val variableName = "${CONTEXT_PREFIX}${name}"
        setVariable(variableName, value, VariableType.CONTEXT, "上下文变量")
    }
    
    /**
     * 获取上下文变量
     */
    fun getContextVariable(name: String): String? {
        val variableName = "${CONTEXT_PREFIX}${name}"
        return getVariable(variableName)?.value
    }
}
