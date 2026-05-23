package com.smarttasker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.smarttasker.R
import com.smarttasker.SmartTaskerApp
import com.smarttasker.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 任务执行前台服务
 * 
 * 负责在后台执行任务
 */
class TaskExecutionService : Service() {
    
    companion object {
        private const val TAG = "TaskExecService"
        private const val NOTIFICATION_ID = SmartTaskerApp.NOTIFICATION_ID_SERVICE
        
        // 服务状态
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning
        
        // 当前任务状态
        private val _taskStatus = MutableStateFlow("")
        val taskStatus: StateFlow<String> = _taskStatus
        
        /**
         * 启动服务
         */
        fun startService(context: Context) {
            val intent = Intent(context, TaskExecutionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        /**
         * 停止服务
         */
        fun stopService(context: Context) {
            val intent = Intent(context, TaskExecutionService::class.java)
            context.stopService(intent)
        }
    }
    
    // 服务作用域
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // 任务执行引擎
    private var taskEngine: TaskExecutionEngine? = null
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("服务运行中"))
        
        Log.d(TAG, "服务创建")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TASK -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID)
                if (taskId != null) {
                    startTask(taskId)
                }
            }
            ACTION_STOP_TASK -> {
                stopTask()
            }
            ACTION_STOP_SERVICE -> {
                stopSelf()
            }
        }
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        serviceScope.cancel()
        
        Log.d(TAG, "服务销毁")
    }
    
    /**
     * 开始执行任务
     */
    private fun startTask(taskId: String) {
        serviceScope.launch {
            try {
                _taskStatus.value = "正在执行任务..."
                updateNotification("正在执行任务: $taskId")
                
                // 这里需要获取任务执行引擎并执行任务
                // 实际实现需要依赖注入
                
                _taskStatus.value = "任务执行完成"
                updateNotification("任务执行完成")
            } catch (e: Exception) {
                Log.e(TAG, "任务执行失败", e)
                _taskStatus.value = "任务执行失败: ${e.message}"
                updateNotification("任务执行失败")
            }
        }
    }
    
    /**
     * 停止执行任务
     */
    private fun stopTask() {
        taskEngine?.stop()
        _taskStatus.value = "任务已停止"
        updateNotification("任务已停止")
    }
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SmartTaskerApp.NOTIFICATION_CHANNEL_ID,
                SmartTaskerApp.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = SmartTaskerApp.NOTIFICATION_CHANNEL_DESCRIPTION
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 创建通知
     */
    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, SmartTaskerApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("SmartTasker")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    /**
     * 更新通知
     */
    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    companion object {
        const val ACTION_START_TASK = "com.smarttasker.START_TASK"
        const val ACTION_STOP_TASK = "com.smarttasker.STOP_TASK"
        const val ACTION_STOP_SERVICE = "com.smarttasker.STOP_SERVICE"
        const val EXTRA_TASK_ID = "task_id"
    }
}
