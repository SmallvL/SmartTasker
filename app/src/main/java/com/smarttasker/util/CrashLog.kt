package com.smarttasker.util

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent file logger — survives app crashes.
 * Writes to app internal storage immediately (flush after each line).
 */
object CrashLog {
    private var logFile: File? = null
    private val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun init(dir: File) {
        logFile = File(dir, "crash_trace.log")
        // Don't clear on init — keep previous crash trace
    }

    fun log(tag: String, msg: String) {
        val ts = sdf.format(Date())
        val line = "[$ts][$tag] $msg\n"
        try {
            logFile?.appendText(line)
        } catch (_: Exception) {}
    }

    fun read(): String {
        return try {
            logFile?.takeIf { it.exists() }?.readText() ?: ""
        } catch (_: Exception) { "" }
    }

    fun clear() {
        try {
            logFile?.writeText("")
        } catch (_: Exception) {}
    }
}
