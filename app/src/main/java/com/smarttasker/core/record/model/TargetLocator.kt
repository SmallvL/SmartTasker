package com.smarttasker.core.record.model

sealed class TargetLocator {
    data class AccessibilityNode(
        val packageName: String?,
        val className: String?,
        val viewIdResourceName: String?,
        val text: String?,
        val contentDescription: String?,
        val boundsLeft: Int, val boundsTop: Int, val boundsRight: Int, val boundsBottom: Int,
        val clickable: Boolean,
        val enabled: Boolean,
        val depthPath: String = ""
    ) : TargetLocator()

    data class Text(
        val text: String,
        val packageName: String? = null
    ) : TargetLocator()

    data class Coordinate(
        val x: Int, val y: Int,
        val normalizedX: Float = 0f, val normalizedY: Float = 0f
    ) : TargetLocator()
}
