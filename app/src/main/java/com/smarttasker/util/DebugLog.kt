package com.smarttasker.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global debug logger for SmartTasker.
 * UI can observe [logs] to display real-time debug output.
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

    fun d(tag: String, message: String) = add(LogEntry.Level.DEBUG, tag, message)
    fun i(tag: String, message: String) = add(LogEntry.Level.INFO, tag, message)
    fun w(tag: String, message: String) = add(LogEntry.Level.WARN, tag, message)
    fun e(tag: String, message: String) = add(LogEntry.Level.ERROR, tag, message)

    private fun add(level: LogEntry.Level, tag: String, message: String) {
        val entry = LogEntry(tag = tag, message = message, level = level)
        synchronized(this) {
            val current = _logs.value.toMutableList()
            current.add(entry)
            if (current.size > MAX_LOGS) {
                current.removeAt(0)
            }
            _logs.value = current
        }
        // Also log to Android logcat
        when (level) {
            LogEntry.Level.DEBUG -> android.util.Log.d(tag, message)
            LogEntry.Level.INFO -> android.util.Log.i(tag, message)
            LogEntry.Level.WARN -> android.util.Log.w(tag, message)
            LogEntry.Level.ERROR -> android.util.Log.e(tag, message)
        }
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
