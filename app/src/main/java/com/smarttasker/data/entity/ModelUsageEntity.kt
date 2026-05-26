package com.smarttasker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "model_usage")
data class ModelUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: String,
    val runId: String = "",
    val modelId: String = "",
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val costCents: Float = 0f,
    val durationMs: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)
