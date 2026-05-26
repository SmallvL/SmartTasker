package com.smarttasker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "route_versions")
data class RouteVersionEntity(
    @PrimaryKey val routeId: String,
    val taskId: String,
    val version: String = "1.0.0",
    val status: String = "draft",                // draft / published / archived / candidate
    val source: String = "ai_learned",           // ai_learned / user_edit / ai_suggestion_applied
    val changeSummary: String = "",
    val recentSuccessRate: Float = 0f,
    val avgDurationMs: Long = 0,
    val avgModelCalls: Float = 0f,
    val createdAt: Long = System.currentTimeMillis(),
    val publishedAt: Long? = null
)
