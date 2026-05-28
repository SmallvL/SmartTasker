package com.smarttasker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trace_events")
data class TraceEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: String,
    val stepId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val level: String = "info",                   // info / warn / error / debug
    val eventType: String = "",                    // step_start / step_end / locator_match / model_call / error / retry
    val message: String = "",
    val details: String = "",                      // JSON string
    // 新增字段：步骤级别日志
    val stepType: String = "",                     // tap / input / swipe / wait / launch / check
    val stepTarget: String = "",                   // 目标元素/坐标
    val stepResult: String = "",                   // success / failed / skipped
    val durationMs: Long = 0,                      // 步骤耗时
    val retryCount: Int = 0,                       // 重试次数
    val screenshotPath: String? = null             // 截图路径
)
