package com.smarttasker.core.record.fusion

import android.view.accessibility.AccessibilityNodeInfo
import com.smarttasker.core.record.accessibility.AccessibilityEventBuffer
import com.smarttasker.core.record.model.*

/**
 * Resolves tap/longpress targets by finding the AccessibilityNode at the given coordinates.
 * Falls back to coordinate-only locator if no node is found.
 */
class TargetResolver(
    private val eventBuffer: AccessibilityEventBuffer
) {

    /**
     * Resolve the target for a tap at (x, y) at the given time.
     * Returns a TargetSnapshot with primary locator and fallbacks.
     */
    fun resolveTap(x: Int, y: Int, time: Long, screenWidth: Int, screenHeight: Int): TargetSnapshot {
        // Try 1: Find from AccessibilityEvent buffer
        val event = eventBuffer.findNearestClick(x, y, time, toleranceMs = 500)
        if (event != null && !event.viewIdResourceName.isNullOrEmpty()) {
            return TargetSnapshot(
                primaryLocator = TargetLocator.AccessibilityNode(
                    packageName = event.packageName,
                    className = event.className,
                    viewIdResourceName = event.viewIdResourceName,
                    text = event.text,
                    contentDescription = event.contentDescription,
                    boundsLeft = event.boundsLeft, boundsTop = event.boundsTop,
                    boundsRight = event.boundsRight, boundsBottom = event.boundsBottom,
                    clickable = true, enabled = event.isEnabled
                ),
                fallbackLocators = listOf(
                    TargetLocator.Coordinate(x, y, x.toFloat() / screenWidth, y.toFloat() / screenHeight)
                ),
                rawX = x, rawY = y,
                normalizedX = x.toFloat() / screenWidth,
                normalizedY = y.toFloat() / screenHeight,
                matchStrategy = "resource_id",
                confidence = 0.9f
            )
        }

        // Try 2: Find from event with text/contentDescription
        if (event != null && (!event.text.isNullOrEmpty() || !event.contentDescription.isNullOrEmpty())) {
            return TargetSnapshot(
                primaryLocator = TargetLocator.AccessibilityNode(
                    packageName = event.packageName,
                    className = event.className,
                    viewIdResourceName = event.viewIdResourceName,
                    text = event.text,
                    contentDescription = event.contentDescription,
                    boundsLeft = event.boundsLeft, boundsTop = event.boundsTop,
                    boundsRight = event.boundsRight, boundsBottom = event.boundsBottom,
                    clickable = true, enabled = event.isEnabled
                ),
                fallbackLocators = listOf(
                    TargetLocator.Coordinate(x, y, x.toFloat() / screenWidth, y.toFloat() / screenHeight)
                ),
                rawX = x, rawY = y,
                normalizedX = x.toFloat() / screenWidth,
                normalizedY = y.toFloat() / screenHeight,
                matchStrategy = "text",
                confidence = 0.8f
            )
        }

        // Try 3: Use event bounds (even without resource ID)
        if (event != null) {
            return TargetSnapshot(
                primaryLocator = TargetLocator.AccessibilityNode(
                    packageName = event.packageName,
                    className = event.className,
                    viewIdResourceName = null,
                    text = event.text,
                    contentDescription = event.contentDescription,
                    boundsLeft = event.boundsLeft, boundsTop = event.boundsTop,
                    boundsRight = event.boundsRight, boundsBottom = event.boundsBottom,
                    clickable = event.isClickable, enabled = event.isEnabled
                ),
                fallbackLocators = listOf(
                    TargetLocator.Coordinate(x, y, x.toFloat() / screenWidth, y.toFloat() / screenHeight)
                ),
                rawX = x, rawY = y,
                normalizedX = x.toFloat() / screenWidth,
                normalizedY = y.toFloat() / screenHeight,
                matchStrategy = "accessibility",
                confidence = 0.6f
            )
        }

        // Fallback: coordinate only
        return TargetSnapshot(
            primaryLocator = TargetLocator.Coordinate(x, y, x.toFloat() / screenWidth, y.toFloat() / screenHeight),
            fallbackLocators = emptyList(),
            rawX = x, rawY = y,
            normalizedX = x.toFloat() / screenWidth,
            normalizedY = y.toFloat() / screenHeight,
            matchStrategy = "coordinate",
            confidence = 0.3f
        )
    }

    /**
     * Resolve target from a node tree by finding the deepest node containing the point.
     */
    fun resolveFromNodeTree(root: AccessibilityNodeInfo?, x: Int, y: Int, screenWidth: Int, screenHeight: Int): TargetSnapshot? {
        if (root == null) return null
        val node = findDeepestNodeAt(root, x, y) ?: return null

        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)

        val locator = TargetLocator.AccessibilityNode(
            packageName = node.packageName?.toString(),
            className = node.className?.toString(),
            viewIdResourceName = node.viewIdResourceName,
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            boundsLeft = rect.left, boundsTop = rect.top,
            boundsRight = rect.right, boundsBottom = rect.bottom,
            clickable = node.isClickable,
            enabled = node.isEnabled
        )

        val confidence = when {
            !node.viewIdResourceName.isNullOrEmpty() -> 0.95f
            !node.text.isNullOrEmpty() -> 0.85f
            !node.contentDescription.isNullOrEmpty() -> 0.75f
            node.isClickable -> 0.6f
            else -> 0.4f
        }

        return TargetSnapshot(
            primaryLocator = locator,
            fallbackLocators = listOf(TargetLocator.Coordinate(x, y, x.toFloat() / screenWidth, y.toFloat() / screenHeight)),
            rawX = x, rawY = y,
            normalizedX = x.toFloat() / screenWidth,
            normalizedY = y.toFloat() / screenHeight,
            matchStrategy = "accessibility",
            confidence = confidence
        )
    }

    private fun findDeepestNodeAt(node: AccessibilityNodeInfo, x: Int, y: Int): AccessibilityNodeInfo? {
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        if (x < rect.left || x > rect.right || y < rect.top || y > rect.bottom) {
            return null
        }
        // Check children first (deepest match)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findDeepestNodeAt(child, x, y)
            if (result != null) {
                child.recycle()
                return result
            }
            child.recycle()
        }
        // Return this node if it's the deepest match
        return node
    }
}
