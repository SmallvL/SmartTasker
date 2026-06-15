package com.smarttasker.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global debug logger for SmartTasker.
 * UI can observe [logs] to display real-time debug output.
 *
 * Rate limiting: For DEBUG level, only the latest entry per tag within a 5-second
 * window is kept in the log list. This prevents repetitive debug messages from
 * growing the list unboundedly and triggering excessive UI recompositions.
 */
object DebugLog {

    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val tag: String,
        val message: String,
        val level: Level = Level.INFO
    ) {
        enum class Level { DEBUG, INFO, WARN, ERROR }
        fun formatted(): String {
            val ts = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(timestamp)
            return "[$ts][$tag] $message"
        }
    }

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private const val MAX_LOGS = 500

    // Rate limiting for DEBUG level: tag -> last entry timestamp
    private val debugLastSeen = mutableMapOf<String, Long>()
    private const val DEBUG_RATE_LIMIT_MS = 5000L  // 5-second window per tag

    fun d(tag: String, message: String) = add(LogEntry.Level.DEBUG, tag, message)
    fun i(tag: String, message: String) = add(LogEntry.Level.INFO, tag, message)
    fun w(tag: String, message: String) = add(LogEntry.Level.WARN, tag, message)
    fun e(tag: String, message: String) = add(LogEntry.Level.ERROR, tag, message)

    private fun add(level: LogEntry.Level, tag: String, message: String) {
        val entry = LogEntry(tag = tag, message = message, level = level)
        synchronized(this) {
            val current = _logs.value.toMutableList()

            if (level == LogEntry.Level.DEBUG) {
                val now = entry.timestamp
                val lastTime = debugLastSeen[tag]
                if (lastTime != null && now - lastTime < DEBUG_RATE_LIMIT_MS) {
                    // Within rate limit window: replace the previous DEBUG entry for this tag
                    val lastIdx = current.indexOfLast { it.tag == tag && it.level == LogEntry.Level.DEBUG }
                    if (lastIdx >= 0) {
                        current[lastIdx] = entry
                    } else {
                        current.add(entry)
                    }
                } else {
                    current.add(entry)
                }
                debugLastSeen[tag] = now
            } else {
                current.add(entry)
            }

            if (current.size > MAX_LOGS) {
                // Remove oldest entries, but also clean up debugLastSeen for removed tags
                val removed = current.subList(0, current.size - MAX_LOGS)
                current.removeAll(removed)
            }
            _logs.value = current
        }
        // Also log to Android logcat (no rate limiting — logcat can handle it)
        when (level) {
            LogEntry.Level.DEBUG -> android.util.Log.d(tag, message)
            LogEntry.Level.INFO -> android.util.Log.i(tag, message)
            LogEntry.Level.WARN -> android.util.Log.w(tag, message)
            LogEntry.Level.ERROR -> android.util.Log.e(tag, message)
        }
    }

    fun clear() {
        synchronized(this) {
            _logs.value = emptyList()
            debugLastSeen.clear()
        }
    }
}
