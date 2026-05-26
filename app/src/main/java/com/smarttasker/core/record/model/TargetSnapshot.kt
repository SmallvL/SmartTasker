package com.smarttasker.core.record.model

data class TargetSnapshot(
    val primaryLocator: TargetLocator,
    val fallbackLocators: List<TargetLocator> = emptyList(),
    val rawX: Int, val rawY: Int,
    val normalizedX: Float, val normalizedY: Float,
    val matchStrategy: String = "coordinate", // coordinate / text / resource_id / accessibility
    val confidence: Float = 0.5f
)
