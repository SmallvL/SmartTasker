package com.smarttasker.core.record.accessibility

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.CopyOnWriteArrayList

class AccessibilityEventBuffer {

    data class BufferedEvent(
        val type: Int,
        val packageName: String?,
        val className: String?,
        val text: String?,
        val contentDescription: String?,
        val viewIdResourceName: String?,
        val boundsLeft: Int,
        val boundsTop: Int,
        val boundsRight: Int,
        val boundsBottom: Int,
        val isClickable: Boolean,
        val isEnabled: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val events = CopyOnWriteArrayList<BufferedEvent>()
    private val maxAge = 5000L // 5 seconds
    private val maxSize = 200

    fun add(event: AccessibilityEvent) {
        val node = event.source
        val now = System.currentTimeMillis()
        val buffered = BufferedEvent(
            type = event.eventType,
            packageName = event.packageName?.toString(),
            className = event.className?.toString(),
            text = if (event.text.isNotEmpty()) event.text.joinToString(" ") else null,
            contentDescription = node?.contentDescription?.toString(),
            viewIdResourceName = node?.viewIdResourceName,
            boundsLeft = node?.getBoundsLeft() ?: 0,
            boundsTop = node?.getBoundsTop() ?: 0,
            boundsRight = node?.getBoundsRight() ?: 0,
            boundsBottom = node?.getBoundsBottom() ?: 0,
            isClickable = node?.isClickable ?: false,
            isEnabled = node?.isEnabled ?: false,
            timestamp = now
        )
        // Release the node to avoid leaks
        node?.recycle()
        events.add(buffered)
        prune()
    }

    /**
     * Find the nearest event of a given type within a time window.
     */
    fun findNearest(type: Int, time: Long, toleranceMs: Long = 300): BufferedEvent? {
        prune()
        return events
            .filter { it.type == type && Math.abs(it.timestamp - time) <= toleranceMs }
            .minByOrNull { Math.abs(it.timestamp - time) }
    }

    /**
     * Find the most recent click event within a time window.
     * Unlike findNearestClick, this doesn't filter by coordinates.
     */
    fun findRecentClick(time: Long, toleranceMs: Long = 500): BufferedEvent? {
        prune()
        return events
            .filter {
                (it.type == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                 it.type == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) &&
                Math.abs(it.timestamp - time) <= toleranceMs
            }
            .maxByOrNull { it.timestamp }
    }

    /**
     * Find the most recent event with meaningful (non-zero, non-fullscreen) bounds.
     * Used for content change burst detection to infer tap location.
     */
    fun findRecentWithBounds(time: Long, toleranceMs: Long = 1000): BufferedEvent? {
        prune()
        return events
            .filter {
                Math.abs(it.timestamp - time) <= toleranceMs &&
                (it.boundsRight - it.boundsLeft) > 0 &&
                (it.boundsBottom - it.boundsTop) > 0
            }
            .maxByOrNull { it.timestamp }
    }

    /**
     * Find the event with the smallest bounds area within a time window.
     * Smaller bounds are more likely to represent the actual tapped element,
     * rather than a large container view.
     */
    fun findSmallestRecentBounds(time: Long, toleranceMs: Long = 1500, maxArea: Long = 500000): BufferedEvent? {
        prune()
        return events
            .filter {
                Math.abs(it.timestamp - time) <= toleranceMs &&
                (it.boundsRight - it.boundsLeft) > 0 &&
                (it.boundsBottom - it.boundsTop) > 0 &&
                // Exclude fullscreen or near-fullscreen bounds (area > maxArea)
                ((it.boundsRight - it.boundsLeft).toLong() * (it.boundsBottom - it.boundsTop)) < maxArea
            }
            .minByOrNull {
                val width = it.boundsRight - it.boundsLeft
                val height = it.boundsBottom - it.boundsTop
                width.toLong() * height
            }
    }

    /**
     * Find the nearest click event near coordinates.
     */
    fun findNearestClick(x: Int, y: Int, time: Long, toleranceMs: Long = 300): BufferedEvent? {
        prune()
        return events
            .filter {
                (it.type == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                 it.type == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) &&
                Math.abs(it.timestamp - time) <= toleranceMs &&
                x >= it.boundsLeft && x <= it.boundsRight &&
                y >= it.boundsTop && y <= it.boundsBottom
            }
            .minByOrNull { Math.abs(it.timestamp - time) }
    }

    /**
     * Find the nearest scroll event within a time window.
     * Only matches TYPE_VIEW_SCROLLED — WINDOW_CONTENT_CHANGED is too noisy.
     */
    fun findNearestScroll(time: Long, toleranceMs: Long = 500): BufferedEvent? {
        prune()
        return events
            .filter {
                it.type == AccessibilityEvent.TYPE_VIEW_SCROLLED &&
                Math.abs(it.timestamp - time) <= toleranceMs
            }
            .minByOrNull { Math.abs(it.timestamp - time) }
    }

    /**
     * Find the nearest text change event within a time window.
     */
    fun findNearestTextChange(time: Long, toleranceMs: Long = 500): BufferedEvent? {
        prune()
        return events
            .filter {
                it.type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED &&
                Math.abs(it.timestamp - time) <= toleranceMs
            }
            .minByOrNull { Math.abs(it.timestamp - time) }
    }

    /**
     * Get recent window state changes.
     */
    fun findRecentWindowChange(toleranceMs: Long = 1000): BufferedEvent? {
        prune()
        val now = System.currentTimeMillis()
        return events
            .filter {
                it.type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                (now - it.timestamp) <= toleranceMs
            }
            .maxByOrNull { it.timestamp }
    }

    fun clear() {
        events.clear()
    }

    private fun prune() {
        val cutoff = System.currentTimeMillis() - maxAge
        events.removeAll { it.timestamp < cutoff }
        while (events.size > maxSize) {
            events.removeAt(0)
        }
    }

    private fun AccessibilityNodeInfo.getBoundsLeft(): Int {
        val rect = android.graphics.Rect()
        getBoundsInScreen(rect)
        return rect.left
    }
    private fun AccessibilityNodeInfo.getBoundsTop(): Int {
        val rect = android.graphics.Rect()
        getBoundsInScreen(rect)
        return rect.top
    }
    private fun AccessibilityNodeInfo.getBoundsRight(): Int {
        val rect = android.graphics.Rect()
        getBoundsInScreen(rect)
        return rect.right
    }
    private fun AccessibilityNodeInfo.getBoundsBottom(): Int {
        val rect = android.graphics.Rect()
        getBoundsInScreen(rect)
        return rect.bottom
    }
}
