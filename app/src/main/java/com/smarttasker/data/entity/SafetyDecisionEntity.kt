package com.smarttasker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "safety_decisions")
data class SafetyDecisionEntity(
    @PrimaryKey val decisionId: String,
    val taskId: String,
    val stepId: String = "",
    val riskLevel: String = "low",
    val riskReason: String = "",
    val policy: String = "allow",
    val userAction: String = "",                  // allow_once / allow_always / deny
    val createdAt: Long = System.currentTimeMillis()
)
