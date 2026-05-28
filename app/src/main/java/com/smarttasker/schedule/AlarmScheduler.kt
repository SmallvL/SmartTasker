package com.smarttasker.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.smarttasker.data.database.AppDatabase
import com.smarttasker.data.entity.TaskEntity
import com.smarttasker.service.AlarmReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Manages exact alarms for scheduled tasks.
 * Uses AlarmManager.setExactAndAllowWhileIdle() to ensure alarms fire even in Doze mode.
 */
object AlarmScheduler {

    const val EXTRA_TASK_ID = "task_id"
    const val EXTRA_TASK_NAME = "task_name"
    const val EXTRA_ROUTE_ID = "route_id"

    /**
     * Schedule an exact alarm for the given task.
     * Parses triggerTime ("HH:mm") and computes the next firing time.
     * For repeating tasks (daily/weekly), the alarm will be re-scheduled when it fires.
     */
    fun scheduleAlarm(context: Context, task: TaskEntity, routeId: String = "") {
        val triggerTime = task.triggerTime
        if (triggerTime.isBlank()) return

        val parts = triggerTime.split(":")
        if (parts.size != 2) return
        val hour = parts[0].toIntOrNull() ?: return
        val minute = parts[1].toIntOrNull() ?: return

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If the scheduled time already passed today, move to the next occurrence
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            when (task.triggerRepeat) {
                "daily" -> calendar.add(Calendar.DAY_OF_YEAR, 1)
                "weekly" -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
                else -> calendar.add(Calendar.DAY_OF_YEAR, 1) // "once" — try tomorrow
            }
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_TASK_ID, task.taskId)
            putExtra(EXTRA_TASK_NAME, task.name)
            putExtra(EXTRA_ROUTE_ID, routeId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.taskId.hashCode(), // unique per task
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } catch (_: SecurityException) {
            // Fallback if SCHEDULE_EXACT_ALARM permission is not granted on API 31+
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }

    /**
     * Cancel a pending alarm for the given task.
     */
    fun cancelAlarm(context: Context, taskId: String) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pendingIntent)
    }

    /**
     * Re-schedule all active scheduled tasks.
     * Called after device reboot by BootReceiver.
     */
    suspend fun rescheduleAll(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val taskDao = db.taskDao()
        val routeDao = db.routeDao()

        // Get all tasks with schedule trigger type
        val scheduledTasks = taskDao.getTasksByTriggerSync("schedule")

        for (task in scheduledTasks) {
            if (task.status != "active") continue

            // Look up the latest published route for this task
            val route = routeDao.getLatestPublishedRoute(task.taskId)
            val routeId = route?.routeId ?: ""

            scheduleAlarm(context, task, routeId)
        }
    }
}
