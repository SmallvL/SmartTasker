package com.smarttasker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey val templateId: String,          // UUID
    val name: String,                             // 模板名称
    val description: String = "",                 // 模板描述
    val category: String = "通用",                // 分类：通用/社交/购物/工具/办公
    val icon: String = "📋",                      // 图标 emoji
    val version: String = "1.0.0",               // 当前版本号
    val versionCode: Int = 1,                     // 版本代码（递增）
    val sourceTaskId: String = "",                // 来源任务ID
    val sourceRouteId: String = "",               // 来源路线ID
    val stepCount: Int = 0,                       // 步骤数量
    val avgDurationMs: Long = 0,                  // 平均执行时长
    val successRate: Float = 0f,                  // 成功率
    val usageCount: Int = 0,                      // 使用次数
    val isBuiltIn: Boolean = false,               // 是否内置模板
    val tags: String = "",                        // 标签（逗号分隔）
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
