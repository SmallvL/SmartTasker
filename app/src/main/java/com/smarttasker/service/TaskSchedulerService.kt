package com.smarttasker.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * 任务调度服务
 * 
 * 负责定时任务和触发任务的调度
 */
class TaskSchedulerService(
    private val context: Context
) {
    companion object {
        private const val TAG = "TaskScheduler"
        private const val WORK_NAME_PREFIX = "smarttasker_"
    }
    
    private val workManager = WorkManager.getInstance(context)
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    
    /**
     * 调度定时任务
     */
    fun scheduleTask(
        taskId: String,
        intervalMinutes: Long,
        isPeriodic: Boolean = true
    ) {
        val workName = "$WORK_NAME_PREFIX$taskId"
        
        if (isPeriodic) {
            val workRequest = PeriodicWorkRequestBuilder<TaskWorker>(
                intervalMinutes,
                TimeUnit.MINUTES
            )
                .setInputData(
                    androidx.work.workDataOf("taskId" to taskId)
                )
                .build()
            
            workManager.enqueueUniquePeriodicWork(
                workName,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
            
            Log.d(TAG, "调度周期任务: $taskId, 间隔: ${intervalMinutes}分钟")
        } else {
            val workRequest = OneTimeWorkRequestBuilder<TaskWorker>()
                .setInputData(
                    androidx.work.workDataOf("taskId" to taskId)
                )
                .setInitialDelay(intervalMinutes, TimeUnit.MINUTES)
                .build()
            
            workManager.enqueueUniqueWork(
                workName,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            
            Log.d(TAG, "调度一次性任务: $taskId, 延迟: ${intervalMinutes}分钟")
        }
    }
    
    /**
     * 调度指定时间的任务
     */
    fun scheduleAtTime(
        taskId: String,
        triggerAtMillis: Long
    ) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("taskId", taskId)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
        
        Log.d(TAG, "调度定时任务: $taskId, 时间: $triggerAtMillis")
    }
    
    /**
     * 取消任务调度
     */
    fun cancelTask(taskId: String) {
        val workName = "$WORK_NAME_PREFIX$taskId"
        workManager.cancelUniqueWork(workName)
        
        // 取消闹钟
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        
        Log.d(TAG, "取消任务调度: $taskId")
    }
    
    /**
     * 取消所有任务调度
     */
    fun cancelAllTasks() {
        workManager.cancelAllWork()
        Log.d(TAG, "取消所有任务调度")
    }
}

/**
 * 任务执行 Worker
 */
class TaskWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "TaskWorker"
    }
    
    override suspend fun doWork(): Result {
        val taskId = inputData.getString("taskId") ?: return Result.failure()
        
        Log.d(TAG, "执行任务: $taskId")
        
        return try {
            // 这里需要获取任务执行引擎并执行任务
            // 实际实现需要依赖注入
            
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "任务执行失败", e)
            Result.retry()
        }
    }
}

/**
 * 闹钟接收器
 */
class AlarmReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "AlarmReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra("taskId") ?: return
        
        Log.d(TAG, "收到闹钟: $taskId")
        
        // 使用 WorkManager 执行任务
        val workRequest = OneTimeWorkRequestBuilder<TaskWorker>()
            .setInputData(
                androidx.work.workDataOf("taskId" to taskId)
            )
            .build()
        
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}

/**
 * 开机启动接收器
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "开机启动")
            
            // 重新调度所有任务
            // 这里需要获取任务列表并重新调度
        }
    }
}
