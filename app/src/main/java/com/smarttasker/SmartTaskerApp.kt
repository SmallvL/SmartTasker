package com.smarttasker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SmartTaskerApp : Application() {
    
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "smarttasker_service"
        const val NOTIFICATION_CHANNEL_NAME = "SmartTasker 服务"
        const val NOTIFICATION_CHANNEL_DESCRIPTION = "SmartTasker 后台服务通知"
        
        const val NOTIFICATION_ID_SERVICE = 1001
        const val NOTIFICATION_ID_TASK = 1002
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = NOTIFICATION_CHANNEL_DESCRIPTION
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
