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
    val eventType: String = "",                    // step_start / step_end / locator_match / model_call / error
    val message: String = "",
    val details: String = ""                       // JSON string
)
