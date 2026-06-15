package com.smarttasker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "template_versions")
data class TemplateVersionEntity(
    @PrimaryKey val versionId: String,
    val templateId: String,
    val versionCode: Int,
    val version: String,                          // "1.0.0"
    val changeSummary: String = "",
    val stepCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
