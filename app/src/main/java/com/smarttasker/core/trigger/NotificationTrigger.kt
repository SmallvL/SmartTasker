package com.smarttasker.core.trigger

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 通知触发规则
 */
data class TriggerRule(
    val id: String,
    val name: String,
    val packageName: String, // 目标应用包名
    val pattern: String, // 正则匹配模式
    val taskId: String, // 触发的任务ID
    val enabled: Boolean = true
)

/**
 * 通知触发管理器
 */
class NotificationTrigger private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "NotificationTrigger"
        
        @Volatile
        private var instance: NotificationTrigger? = null
        
        fun getInstance(context: Context): NotificationTrigger {
            return instance ?: synchronized(this) {
                instance ?: NotificationTrigger(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val _rules = MutableStateFlow<List<TriggerRule>>(emptyList())
    val rules: StateFlow<List<TriggerRule>> = _rules.asStateFlow()
    
    private val _lastTrigger = MutableStateFlow<TriggerRule?>(null)
    val lastTrigger: StateFlow<TriggerRule?> = _lastTrigger.asStateFlow()
    
    /**
     * 添加触发规则
     */
    fun addRule(rule: TriggerRule) {
        val current = _rules.value.toMutableList()
        current.add(rule)
        _rules.value = current
        Log.d(TAG, "添加规则: ${rule.name}")
    }
    
    /**
     * 移除触发规则
     */
    fun removeRule(ruleId: String) {
        val current = _rules.value.toMutableList()
        current.removeAll { it.id == ruleId }
        _rules.value = current
        Log.d(TAG, "移除规则: $ruleId")
    }
    
    /**
     * 启用/禁用规则
     */
    fun toggleRule(ruleId: String, enabled: Boolean) {
        val current = _rules.value.toMutableList()
        val index = current.indexOfFirst { it.id == ruleId }
        if (index >= 0) {
            current[index] = current[index].copy(enabled = enabled)
            _rules.value = current
            Log.d(TAG, "规则 $ruleId 已${if (enabled) "启用" else "禁用"}")
        }
    }
    
    /**
     * 检查通知是否匹配规则
     */
    fun checkNotification(packageName: String, title: String?, text: String?): TriggerRule? {
        val enabledRules = _rules.value.filter { it.enabled }
        
        for (rule in enabledRules) {
            // 检查包名匹配
            if (rule.packageName.isNotEmpty() && rule.packageName != packageName) {
                continue
            }
            
            // 检查正则匹配
            val content = buildString {
                title?.let { append(it) }
                text?.let { append(" ").append(it) }
            }
            
            try {
                val regex = Regex(rule.pattern)
                if (regex.containsMatchIn(content)) {
                    Log.d(TAG, "规则匹配: ${rule.name} -> $content")
                    _lastTrigger.value = rule
                    return rule
                }
            } catch (e: Exception) {
                Log.e(TAG, "正则表达式错误: ${rule.pattern}", e)
            }
        }
        
        return null
    }
    
    /**
     * 获取所有规则
     */
    fun getAllRules(): List<TriggerRule> = _rules.value
}

/**
 * 通知监听服务
 */
class SmartTaskerNotificationListener : NotificationListenerService() {
    
    private lateinit var trigger: NotificationTrigger
    private var taskExecutionCallback: ((String) -> Unit)? = null
    
    override fun onCreate() {
        super.onCreate()
        trigger = NotificationTrigger.getInstance(this)
        Log.d("NotificationListener", "通知监听服务已创建")
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let { notification ->
            val packageName = notification.packageName
            val notificationData = notification.notification
            val extras = notificationData?.extras
            
            val title = extras?.getCharSequence("android.title")?.toString()
            val text = extras?.getCharSequence("android.text")?.toString()
            
            Log.d("NotificationListener", "收到通知: $packageName - $title: $text")
            
            // 检查是否匹配触发规则
            val matchedRule = trigger.checkNotification(packageName, title, text)
            matchedRule?.let { rule ->
                Log.d("NotificationListener", "触发任务: ${rule.taskId}")
                // 触发任务执行
                taskExecutionCallback?.invoke(rule.taskId)
            }
        }
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // 通知移除时的处理
    }
    
    /**
     * 设置任务执行回调
     */
    fun setTaskExecutionCallback(callback: (String) -> Unit) {
        taskExecutionCallback = callback
    }
}
