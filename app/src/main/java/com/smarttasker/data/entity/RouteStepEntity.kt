package com.smarttasker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "route_steps")
data class RouteStepEntity(
    @PrimaryKey val stepId: String,
    val routeId: String,
    val stepIndex: Int,
    val enabled: Boolean = true,
    val type: String = "tap",                    // open_app / tap / input / swipe / back / wait / assert / confirm / finish
    val summary: String = "",
    val screenshotRef: String = "",
    // Locator
    val locatorStrategy: String = "text",        // text / content_desc / resource_id / coordinate / visual_description
    val locatorValue: String = "",
    val locatorConfidence: Float = 0f,
    val fallbackStrategy: String = "",
    val fallbackValue: String = "",
    // Timing
    val waitTimeMs: Long = 1000,                 // Wait time before this step
    // Retry
    val maxRetries: Int = 2,
    val retryIntervalMs: Long = 1000,
    // Fail
    val onFailAction: String = "fallback_to_vision",
    // Risk
    val riskLevel: String = "low",
    val requiresConfirmation: Boolean = false,
    // Edit meta
    val source: String = "ai_learned",
    val userModified: Boolean = false,
    val lockedByUser: Boolean = false
)
