package com.smarttasker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "permission_snapshots")
data class PermissionSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val coreRunning: Boolean = false,
    val adbConnected: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val notificationPermission: Boolean = false,
    val batteryOptimizationDisabled: Boolean = false,
    val modelConfigured: Boolean = false,
    val overallScore: Int = 0,                    // 0-100
    val checkedAt: Long = System.currentTimeMillis()
)
