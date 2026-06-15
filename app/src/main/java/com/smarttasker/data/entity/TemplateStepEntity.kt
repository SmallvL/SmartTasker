package com.smarttasker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "template_steps")
data class TemplateStepEntity(
    @PrimaryKey val stepId: String,
    val templateId: String,
    val versionCode: Int,                         // 对应模板版本
    val stepIndex: Int,
    val enabled: Boolean = true,
    val type: String = "tap",
    val summary: String = "",
    val locatorStrategy: String = "coordinate",
    val locatorValue: String = "",
    val waitTimeMs: Long = 1000,
    val riskLevel: String = "low",
    val notes: String = ""
)
