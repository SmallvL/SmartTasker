package com.smarttasker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val taskId: String,
    val name: String,
    val description: String = "",
    val targetAppName: String = "",
    val targetPackage: String = "",
    val triggerType: String = "manual",         // manual / schedule / notification
    val triggerTime: String = "",                // "09:00"
    val triggerRepeat: String = "once",          // once / daily / weekly
    val executionMode: String = "learn_first_then_replay",
    val routeEnabled: Boolean = true,
    val fallbackToVision: Boolean = true,
    val riskLevel: String = "low",               // low / medium / high / critical
    val requiresConfirmation: Boolean = false,
    val status: String = "draft",                // draft / active / paused / archived
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
