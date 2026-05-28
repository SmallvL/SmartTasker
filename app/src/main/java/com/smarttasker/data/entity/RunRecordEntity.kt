package com.smarttasker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "run_records")
data class RunRecordEntity(
    @PrimaryKey val runId: String,
    val taskId: String,
    val routeVersion: String = "",
    val triggerType: String = "manual",
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val status: String = "running",              // running / success / failed / cancelled / blocked_by_safety
    val durationMs: Long = 0,
    val modelCalls: Int = 0,
    val failedStepId: String? = null,
    val failureType: String? = null,             // locator_not_found / timeout / model_error / safety_blocked / etc
    val diagnosisSummary: String = "",
    val diagnosisSuggestion: String = "",
    val routeSnapshot: String = "",              // AutoLXB route JSON snapshot
    val retryCount: Int = 0,                    // 重试次数
    val screenshotPath: String? = null           // 截图路径
    )
