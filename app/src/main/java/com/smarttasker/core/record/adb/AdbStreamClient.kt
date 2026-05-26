package com.smarttasker.core.record.adb

import com.smarttasker.core.adb.AdbShellExecutor
import com.smarttasker.core.direct.ShellExecutor
import com.smarttasker.core.direct.ShellExecutor.ShellMode
import com.smarttasker.core.direct.ShellResult
import com.smarttasker.core.record.model.AppContextSnapshot
import com.smarttasker.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Client for streaming raw input events and executing shell commands.
 *
 * For getevent streaming, priority order:
 * 1. ROOT (su -c getevent -lt)
 * 2. ADB TLS (AdbShellExecutor.openShellStream) — real device wireless debugging
 * 3. ADB binary path (adb shell getevent -lt) — if adb binary found
 * 4. SH fallback (getevent -lt) — will fail on most devices without input group
 */
class AdbStreamClient(private val adbExecutor: AdbShellExecutor? = null) {

    data class RawInputRange(
        val minX: Int, val maxX: Int,
        val minY: Int, val maxY: Int
    )

    suspend fun exec(command: String): ShellResult {
        return ShellExecutor.exec(command)
    }

    suspend fun execOutput(command: String): String? {
        return ShellExecutor.execOutput(command)
    }

    /**
     * Stream getevent -lt output as a Flow of lines.
     * Uses the best available method based on shell mode.
     */
    fun streamGetevent(): Flow<String> = callbackFlow {
        DebugLog.i("AdbStream", "streamGetevent() called, mode=${ShellExecutor.getMode()}, adbTls=${adbExecutor?.isConnected()}")

        // Priority 1: ROOT mode via su
        val suPath = ShellExecutor.getCachedSuPath()
        if (suPath != null) {
            DebugLog.i("AdbStream", "Using ROOT mode: $suPath -c getevent -lt")
            val process = Runtime.getRuntime().exec(arrayOf(suPath, "-c", "getevent -lt"))
            streamProcess(process, this)
            awaitClose { process.destroy() }
            return@callbackFlow
        }

        // Priority 2: ADB TLS streaming (real device via wireless debugging)
        if (adbExecutor?.isConnected() == true) {
            DebugLog.i("AdbStream", "Using ADB TLS stream for getevent")
            val result = adbExecutor.openShellStream("getevent -lt")
            if (result != null) {
                val (inputStream, closer) = result
                DebugLog.i("AdbStream", "ADB TLS stream opened, reading...")
                try {
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    var line: String?
                    var lineCount = 0
                    while (reader.readLine().also { line = it } != null) {
                        line?.let {
                            lineCount++
                            // Log ALL lines for first 20 to diagnose format
                            if (lineCount <= 20) DebugLog.i("AdbStream", "TLS line $lineCount: ${it.take(80)}")
                            if (it.isNotBlank() && it.startsWith("[")) {
                                trySend(it)
                            }
                        }
                    }
                    DebugLog.w("AdbStream", "ADB TLS getevent stream ended after $lineCount lines")
                } catch (e: Exception) {
                    DebugLog.e("AdbStream", "ADB TLS stream error: ${e.message}")
                } finally {
                    closer.close()
                }
                awaitClose { closer.close() }
                return@callbackFlow
            } else {
                DebugLog.w("AdbStream", "ADB TLS stream openShellStream returned null!")
            }
        } else {
            DebugLog.w("AdbStream", "ADB TLS not connected (adbExecutor=${adbExecutor != null}, connected=${adbExecutor?.isConnected()})")
        }

        // Priority 3: Try Runtime.exec with sh
        DebugLog.i("AdbStream", "Using SH fallback: sh -c getevent -lt")
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "getevent -lt"))
        streamProcess(process, this)
        awaitClose { process.destroy() }
    }

    private fun streamProcess(
        process: Process,
        scope: kotlinx.coroutines.channels.ProducerScope<String>
    ) {
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val errorReader = BufferedReader(InputStreamReader(process.errorStream))

        // Read stderr in background
        val errorThread = Thread {
            try {
                var errorLine: String?
                while (errorReader.readLine().also { errorLine = it } != null) {
                    errorLine?.let { DebugLog.e("AdbStream", "getevent stderr: $it") }
                }
            } catch (_: Exception) {}
        }
        errorThread.isDaemon = true
        errorThread.start()

        try {
            var line: String?
            var lineCount = 0
            while (reader.readLine().also { line = it } != null) {
                line?.let {
                    if (it.isNotBlank()) {
                        lineCount++
                        if (lineCount <= 5) DebugLog.d("AdbStream", "getevent line $lineCount: $it")
                        scope.trySend(it)
                    }
                }
            }
            DebugLog.w("AdbStream", "getevent stream ended after $lineCount lines")
        } catch (e: Exception) {
            DebugLog.e("AdbStream", "getevent stream error: ${e.message}")
        } finally {
            process.destroy()
        }
    }

    suspend fun getScreenSize(): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val output = execOutput("wm size") ?: return@withContext null
        val regex = Regex("(\\d+)x(\\d+)")
        val match = regex.find(output) ?: return@withContext null
        Pair(match.groupValues[1].toInt(), match.groupValues[2].toInt())
    }

    suspend fun getDensity(): Int? = withContext(Dispatchers.IO) {
        val output = execOutput("wm density") ?: return@withContext null
        val regex = Regex("(\\d+)")
        regex.find(output)?.value?.toIntOrNull()
    }

    suspend fun getRawInputRange(): RawInputRange? = withContext(Dispatchers.IO) {
        val output = execOutput("getevent -lp") ?: return@withContext null
        var minX = 0; var maxX = 0; var minY = 0; var maxY = 0
        var inTouchDevice = false
        for (line in output.lines()) {
            if (line.contains("ABS_MT_POSITION_X")) {
                val range = Regex("min\\s+(\\d+).*max\\s+(\\d+)").find(line)
                if (range != null) {
                    minX = range.groupValues[1].toInt()
                    maxX = range.groupValues[2].toInt()
                    inTouchDevice = true
                }
            }
            if (line.contains("ABS_MT_POSITION_Y")) {
                val range = Regex("min\\s+(\\d+).*max\\s+(\\d+)").find(line)
                if (range != null) {
                    minY = range.groupValues[1].toInt()
                    maxY = range.groupValues[2].toInt()
                }
            }
        }
        if (inTouchDevice && maxX > 0 && maxY > 0) {
            RawInputRange(minX, maxX, minY, maxY)
        } else null
    }

    suspend fun screenshot(): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("screencap", "-p"))
            val bytes = process.inputStream.readBytes()
            val exitCode = process.waitFor()
            if (exitCode == 0 && bytes.isNotEmpty()) bytes else null
        } catch (e: Exception) {
            DebugLog.e("AdbStream", "screenshot error: ${e.message}")
            null
        }
    }

    suspend fun dumpUiTree(): String? = withContext(Dispatchers.IO) {
        execOutput("uiautomator dump /dev/tty")
    }

    suspend fun getCurrentApp(): AppContextSnapshot? = withContext(Dispatchers.IO) {
        val output = execOutput("dumpsys activity activities | grep mResumedActivity") ?: return@withContext null
        val regex = Regex("([a-zA-Z0-9_.]+)/([a-zA-Z0-9_.]+)")
        val match = regex.find(output) ?: return@withContext null
        AppContextSnapshot(
            packageName = match.groupValues[1],
            activityName = match.groupValues[2]
        )
    }
}
