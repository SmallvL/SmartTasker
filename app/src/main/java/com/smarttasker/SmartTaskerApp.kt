package com.smarttasker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class SmartTaskerApp : Application() {
    companion object {
        const val CHANNEL_SERVICE = "smarttask_service"
        const val CHANNEL_ALERTS = "smarttask_alerts"
    }

    override fun onCreate() {
        super.onCreate()

        // Capture ALL uncaught exceptions for debugging
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val msg = "FATAL: ${throwable.javaClass.simpleName}: ${throwable.message}\n" +
                throwable.stackTrace.take(10).joinToString("\n") { "  at $it" }
            android.util.Log.e("SmartTaskerFatal", msg)
            // Persist to SharedPreferences SYNCHRONOUSLY (commit, not apply)
            try {
                getSharedPreferences("smarttasker_crash", MODE_PRIVATE)
                    .edit()
                    .putString("last_crash", msg)
                    .putLong("last_crash_time", System.currentTimeMillis())
                    .commit() // synchronous — survives process death
            } catch (_: Exception) {}
            // Also write to file as backup
            try {
                val crashFile = java.io.File(filesDir, "last_crash.txt")
                crashFile.writeText(msg)
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        createNotificationChannels()
        com.smarttasker.core.direct.ShellExecutor.init(this)
        com.smarttasker.util.CrashLog.init(filesDir)
        com.smarttasker.util.CrashLog.log("App", "=== App started ===")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_SERVICE, "SmartTask 服务", NotificationManager.IMPORTANCE_LOW)
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ALERTS, "SmartTask 提醒", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }
}
