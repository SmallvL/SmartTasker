package com.smarttasker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.smarttasker.R
import com.smarttasker.data.database.AppDatabase
import com.smarttasker.data.repository.RouteRepository
import com.smarttasker.data.repository.RunRepository
import com.smarttasker.schedule.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
        private const val CHANNEL_ID = "smarttask_scheduled"
        private const val CHANNEL_NAME = "计划任务"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val taskId = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_ID) ?: return
        val taskName = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_NAME) ?: "计划任务"
        val routeId = intent.getStringExtra(AlarmScheduler.EXTRA_ROUTE_ID) ?: ""

        // goAsync() allows us to do async work in a BroadcastReceiver
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val taskDao = db.taskDao()
                val routeDao = db.routeDao()
                val runRecordDao = db.runRecordDao()

                val task = taskDao.getTaskById(taskId)
                if (task == null || task.status != "active") {
                    Log.w(TAG, "Task $taskId not found or not active, skipping")
                    pendingResult.finish()
                    return@launch
                }

                // Show alarm notification
                showAlarmNotification(context, taskId, taskName)

                // Attempt to execute the saved route
                val effectiveRouteId = routeId.ifBlank {
                    routeDao.getLatestPublishedRoute(taskId)?.routeId ?: ""
                }

                if (effectiveRouteId.isNotBlank()) {
                    val steps = routeDao.getStepsForRouteSync(effectiveRouteId)
                    if (steps.isNotEmpty()) {
                        val routeRepo = RouteRepository(routeDao)
                        val runRepo = RunRepository(runRecordDao)
                        val executionService = TaskExecutionService(context, routeRepo, runRepo)

                        Log.i(TAG, "Executing scheduled task '$taskName' ($taskId), route=$effectiveRouteId")
                        val result = executionService.executeSavedRoute(taskId, steps)
                        Log.i(TAG, "Execution result: $result")

                        // Show result notification
                        val success = result is com.smarttasker.service.ExecutionResult.Success
                        showResultNotification(context, taskId, taskName, success)
                    } else {
                        Log.w(TAG, "No route steps found for task $taskId, route=$effectiveRouteId")
                    }
                } else {
                    Log.w(TAG, "No published route found for task $taskId")
                }

                // Re-schedule for repeating tasks
                when (task.triggerRepeat) {
                    "daily", "weekly" -> {
                        AlarmScheduler.scheduleAlarm(context, task, effectiveRouteId)
                        Log.i(TAG, "Re-scheduled ${task.triggerRepeat} task '$taskName'")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing scheduled task $taskId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showAlarmNotification(context: Context, taskId: String, taskName: String) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("正在执行计划任务")
            .setContentText(taskName)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(taskId.hashCode() and 0x7FFFFFFF, notification)
    }

    private fun showResultNotification(context: Context, taskId: String, taskName: String, success: Boolean) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (success) "任务完成" else "任务失败")
            .setContentText(taskName)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Use a different notification ID so it doesn't replace the "executing" notification
        nm.notify((taskId.hashCode() + 1) and 0x7FFFFFFF, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
        }
    }
}
