package com.smarttasker.core.direct

import android.content.Context
import android.os.Build
import com.smarttasker.core.adb.ShellAdbClient
import com.smarttasker.core.adb.AdbShellExecutor
import com.smarttasker.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shell command executor - supports Root, ADB (TLS), Local ADB (TCP), and SH modes.
 * Priority: Root > ADB (TLS) > Local ADB (TCP) > SH > None
 *
 * SH mode uses Runtime.exec("sh -c ...") — runs as app UID, no root.
 * Sufficient for: input, am, pm, screencap, settings, getprop.
 * NOT sufficient for: getevent (needs input group — may or may not work).
 */
object ShellExecutor {

    enum class ShellMode { ROOT, ADB, ADB_LOCAL, SH, NONE }

    private var cachedMode: ShellMode? = null
    private var adbExecutor: AdbShellExecutor? = null
    private var localAdbClient: ShellAdbClient? = null
    private var cachedSuPath: String = "su"

    /**
     * Initialize with application context. Must be called before using ADB mode.
     */
    fun init(context: Context) {
        if (adbExecutor == null) {
            adbExecutor = AdbShellExecutor(context.applicationContext)
        }
    }

    /**
     * Detect available shell mode.
     * Priority: Root > ADB (TLS) > Local ADB (TCP) > SH > None
     * Thread-safe: single detection at a time.
     */
    private val detectLock = Any()

    suspend fun detectMode(): ShellMode = withContext(Dispatchers.IO) {
        cachedMode?.let { return@withContext it }

        synchronized(detectLock) {
            cachedMode?.let { return@withContext it }

            // Try root first (with timeout)
            if (tryRoot()) {
                cachedMode = ShellMode.ROOT
                DebugLog.i("ShellExec", "Detected ROOT mode (su=$cachedSuPath)")
                return@withContext ShellMode.ROOT
            }

            // Try TLS ADB (wireless debugging)
            if (adbExecutor?.isConnected() == true) {
                cachedMode = ShellMode.ADB
                DebugLog.i("ShellExec", "Detected ADB (TLS) mode")
                return@withContext ShellMode.ADB
            }

            // Try local ADB TCP (emulator / localhost:5555)
            if (tryLocalAdb()) {
                cachedMode = ShellMode.ADB_LOCAL
                DebugLog.i("ShellExec", "Detected ADB_LOCAL (TCP) mode")
                return@withContext ShellMode.ADB_LOCAL
            }

            // Try sh (app-level shell, no root)
            if (trySh()) {
                cachedMode = ShellMode.SH
                DebugLog.i("ShellExec", "Detected SH mode (app-level shell)")
                return@withContext ShellMode.SH
            }

            cachedMode = ShellMode.NONE
            DebugLog.i("ShellExec", "No shell mode available")
            ShellMode.NONE
        }
    }

    /**
     * Connect ADB to a specific host:port using TLS.
     */
    suspend fun connectAdb(host: String, port: Int): Boolean {
        val executor = adbExecutor ?: return false
        val result = executor.connect(host, port)
        if (result) {
            cachedMode = ShellMode.ADB
        }
        return result
    }

    /**
     * Execute a shell command.
     */
    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        val mode = cachedMode ?: detectMode()
        when (mode) {
            ShellMode.ROOT -> execRoot(command)
            ShellMode.ADB -> {
                val executor = adbExecutor
                    ?: return@withContext ShellResult.Error("ADB executor not initialized")
                val result = executor.exec(command)
                if (result is ShellResult.Error && result.message.contains("not connected")) {
                    if (executor.reconnect()) {
                        return@withContext executor.exec(command)
                    }
                }
                result
            }
            ShellMode.ADB_LOCAL -> execLocalAdb(command)
            ShellMode.SH -> execSh(command)
            ShellMode.NONE -> ShellResult.Error("No shell available: need Root or ADB")
        }
    }

    /**
     * Execute and return only the output string, or null on error.
     */
    suspend fun execOutput(command: String): String? {
        return when (val result = exec(command)) {
            is ShellResult.Success -> result.output
            is ShellResult.Error -> null
        }
    }

    /**
     * Check if we have a working shell.
     */
    suspend fun isAvailable(): Boolean {
        val mode = detectMode()
        return mode != ShellMode.NONE
    }

    /**
     * Check if we have enough permissions for recording (getevent).
     * SH mode is NOT sufficient - needs ROOT or ADB.
     */
    suspend fun canRecord(): Boolean {
        val mode = detectMode()
        return mode == ShellMode.ROOT || mode == ShellMode.ADB || mode == ShellMode.ADB_LOCAL
    }

    /**
     * Get a human-readable description of current capabilities.
     */
    suspend fun getCapabilityDescription(): String {
        val mode = detectMode()
        return when (mode) {
            ShellMode.ROOT -> "Root 模式 - 完全控制"
            ShellMode.ADB -> "ADB TLS 模式 - 支持录制"
            ShellMode.ADB_LOCAL -> "ADB 本地模式 - 支持录制"
            ShellMode.SH -> "SH 模式 - 仅基本命令，不支持录制"
            ShellMode.NONE -> "无可用 Shell"
        }
    }

    /**
     * Get the cached su path (for use in streaming commands like getevent).
     * Returns null if not in ROOT mode.
     */
    fun getCachedSuPath(): String? {
        return if (cachedMode == ShellMode.ROOT) cachedSuPath else null
    }

    /**
     * Get the current shell mode (for use in streaming commands).
     * Returns null if not yet detected.
     */
    fun getMode(): ShellMode? {
        return cachedMode
    }

    /**
     * Get the ADB shell executor (for TLS streaming commands like getevent).
     * Returns null if not initialized or not connected.
     */
    fun getAdbExecutor(): AdbShellExecutor? {
        return if (adbExecutor?.isConnected() == true) adbExecutor else null
    }

    /**
     * Get the local ADB client (for streaming commands like getevent via ADB_LOCAL).
     * Returns null if not in ADB_LOCAL mode.
     */
    fun getLocalAdbClient(): ShellAdbClient? {
        return if (cachedMode == ShellMode.ADB_LOCAL) localAdbClient else null
    }

    /**
     * Force re-detection.
     */
    fun resetCache() {
        cachedMode = null
        adbExecutor?.disconnect()
        runCatching { localAdbClient?.close() }
        localAdbClient = null
    }

    // ===== Root =====

    private fun tryRoot(): Boolean {
        val suPaths = arrayOf("su", "/system/xbin/su", "/system/bin/su", "/sbin/su")
        for (suPath in suPaths) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf(suPath, "-c", "id"))
                val worker = Thread {
                    try { Thread.sleep(3000); process.destroy() } catch (_: Exception) {}
                }
                worker.start()
                val exitCode = process.waitFor()
                worker.interrupt()
                val output = process.inputStream.bufferedReader().readText().trim()
                if (exitCode == 0 && output.contains("uid=0")) {
                    cachedSuPath = suPath
                    return true
                }
            } catch (_: Exception) {
                // Try next path
            }
        }
        return false
    }

    private fun execRoot(command: String): ShellResult {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf(cachedSuPath, "-c", command))
            val worker = Thread {
                try { Thread.sleep(15_000); process.destroy() } catch (_: Exception) {}
            }
            worker.start()
            val stdout = process.inputStream.bufferedReader().readText().trim()
            val stderr = process.errorStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            worker.interrupt()
            if (exitCode == 0) {
                ShellResult.Success(stdout)
            } else {
                ShellResult.Error("exit=$exitCode: $stderr")
            }
        } catch (e: Exception) {
            ShellResult.Error(e.message ?: "Root exec failed")
        }
    }

    // ===== Local ADB (TCP, no TLS) =====

    private fun tryLocalAdb(): Boolean {
        // Try multiple addresses: IPv6 localhost first, then IPv4, then emulator host alias
        val addresses = listOf(
            "::1" to 5555,       // IPv6 localhost (emulator adbd listens on :::5555)
            "127.0.0.1" to 5555, // IPv4 localhost
            "10.0.2.2" to 5555   // Emulator host alias (ADB forwarded port)
        )
        
        for ((host, port) in addresses) {
            try {
                val client = ShellAdbClient(host, port, 3000)
                val ok = client.connect()
                if (ok) {
                    val test = try { client.shell("echo ok").trim() } catch (e: Exception) {
                        DebugLog.d("ShellExec", "Local ADB shell test exception for $host:$port: ${e.message}")
                        ""
                    }
                    if (test == "ok") {
                        localAdbClient = client
                        DebugLog.i("ShellExec", "Local ADB TCP connected to $host:$port")
                        return true
                    } else {
                        DebugLog.d("ShellExec", "Local ADB shell test failed for $host:$port: test='$test'")
                        client.close()
                    }
                } else {
                    client.close()
                }
            } catch (e: Exception) {
                DebugLog.d("ShellExec", "Local ADB TCP failed for $host:$port: ${e.message}")
            }
        }
        return false
    }

    private fun execLocalAdb(command: String): ShellResult {
        val client = localAdbClient
            ?: return ShellResult.Error("Local ADB not connected")
        return try {
            val output = client.shell(command)
            ShellResult.Success(output)
        } catch (e: Exception) {
            try {
                val newClient = ShellAdbClient("::1", 5555, 5000)
                if (newClient.connect()) {
                    localAdbClient = newClient
                    val output = newClient.shell(command)
                    ShellResult.Success(output)
                } else {
                    newClient.close()
                    localAdbClient = null
                    cachedMode = null
                    ShellResult.Error("Local ADB reconnect failed: ${e.message}")
                }
            } catch (e2: Exception) {
                localAdbClient = null
                cachedMode = null
                ShellResult.Error("Local ADB exec failed: ${e.message}")
            }
        }
    }

    // ===== SH (app-level shell) =====

    /**
     * Check if sh is available (always true on Android, but validate it works).
     */
    private fun trySh(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "echo ok"))
            val worker = Thread {
                try { Thread.sleep(3000); process.destroy() } catch (_: Exception) {}
            }
            worker.start()
            val exitCode = process.waitFor()
            worker.interrupt()
            val output = process.inputStream.bufferedReader().readText().trim()
            exitCode == 0 && output == "ok"
        } catch (_: Exception) {
            false
        }
    }

    private fun execSh(command: String): ShellResult {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val worker = Thread {
                try { Thread.sleep(15_000); process.destroy() } catch (_: Exception) {}
            }
            worker.start()
            val stdout = process.inputStream.bufferedReader().readText().trim()
            val stderr = process.errorStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            worker.interrupt()
            if (exitCode == 0) {
                ShellResult.Success(stdout)
            } else {
                ShellResult.Error("exit=$exitCode: $stderr")
            }
        } catch (e: Exception) {
            ShellResult.Error(e.message ?: "SH exec failed")
        }
    }
}

sealed class ShellResult {
    data class Success(val output: String) : ShellResult()
    data class Error(val message: String) : ShellResult()
}
