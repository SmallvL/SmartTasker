package com.smarttasker.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.smarttasker.core.record.accessibility.AccessibilityEventBuffer
import com.smarttasker.util.DebugLog

class SmartTaskerAccessibilityService : AccessibilityService() {

    companion object {
        /** Current active service instance, null when not connected */
        @Volatile
        var instance: SmartTaskerAccessibilityService? = null
            private set

        /** Shared event buffer accessible from RecordingSessionManager */
        val eventBuffer = AccessibilityEventBuffer()

        /** Check if the accessibility service is enabled */
        fun isEnabled(): Boolean = instance != null

        /**
         * Callback for content change bursts.
         * Set by RecordingSessionManager when recording starts.
         */
        @Volatile
        var onContentChangeBurst: ((timestamp: Long) -> Unit)? = null

        // Throttle map: tag -> last log time
        private val lastLogTime = mutableMapOf<String, Long>()
        private const val LOG_THROTTLE_MS = 2000L  // Max 1 log per 2 seconds per tag
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        DebugLog.i("A11yService", "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        // Forward all relevant events to the shared buffer
        eventBuffer.add(event)

        // Notify content change burst detector — use System.currentTimeMillis() for consistency
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            onContentChangeBurst?.invoke(System.currentTimeMillis())
        }

        // Only log significant events with throttling to avoid log spam
        val typeStr = when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "VIEW_CLICKED"
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> "VIEW_LONG_CLICKED"
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> "VIEW_SCROLLED"
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "VIEW_TEXT_CHANGED"
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW_STATE_CHANGED"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "WINDOW_CONTENT_CHANGED"
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> "VIEW_FOCUSED"
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> "VIEW_TEXT_SELECTION_CHANGED"
            else -> null
        }
        // Only log significant events (VIEW_CLICKED, WINDOW_STATE_CHANGED), throttled
        if (typeStr != null && (
            event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        )) {
            val now = System.currentTimeMillis()
            val last = lastLogTime[typeStr] ?: 0L
            if (now - last >= LOG_THROTTLE_MS) {
                lastLogTime[typeStr] = now
                DebugLog.d("A11yService", "Event: $typeStr pkg=${event.packageName} cls=${event.className} text=${event.text}")
            }
        }
    }

    override fun onInterrupt() {
        DebugLog.w("A11yService", "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        onContentChangeBurst = null
        DebugLog.i("A11yService", "Accessibility service destroyed")
    }
}
