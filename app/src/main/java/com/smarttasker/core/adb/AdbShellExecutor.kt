package com.smarttasker.core.adb

import android.content.Context
import com.smarttasker.core.direct.ShellResult
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import com.smarttasker.util.DebugLog

/**
 * ADB shell executor using libadb-android's TLS connection manager.
 * This properly handles wireless ADB TLS connections (unlike raw TCP AdbClient).
 *
 * After pairing via AdbPairingService, this connects to the saved endpoint
 * and executes shell commands via manager.openStream("shell:...").
 */
class AdbShellExecutor(private val context: Context) {

    private var manager: WirelessAdbConnectionManager? = null
    private var connected = false
    private var lastHost: String = ""
    private var lastPort: Int = 0

    /**
     * Connect to ADB daemon using TLS (via libadb-android).
     * The endpoint should be the _adb-tls-connect._tcp. port discovered by AdbPairingService.
     * If port is 0, uses autoConnect exclusively (for when the connect endpoint was not found during pairing).
     */
    suspend fun connect(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        DebugLog.i("AdbShell", "Connecting to $host:$port via TLS (port==0 means auto-connect only)...")
        try {
            disconnect()
            val mgr = WirelessAdbConnectionManager(context.applicationContext)
            mgr.setTimeout(15, TimeUnit.SECONDS)
            mgr.setThrowOnUnauthorised(true)
            mgr.setHostAddress(host)

            val ok: Boolean
            if (port > 0) {
                // Try direct connect first, then auto-connect as fallback
                val direct = runCatching { mgr.connect(host, port) }.getOrDefault(false)
                val auto = if (!direct) {
                    runCatching { mgr.autoConnect(context.applicationContext, 10_000) }.getOrDefault(false)
                } else false
                ok = direct || auto || mgr.isConnected
                DebugLog.i("AdbShell", "Connect result: direct=$direct auto=$auto isConnected=${mgr.isConnected}")
            } else {
                // port==0: use autoConnect exclusively (NSD discovery)
                DebugLog.i("AdbShell", "Saved port is 0, using autoConnect exclusively...")
                val auto = runCatching { mgr.autoConnect(context.applicationContext, 15_000) }.getOrDefault(false)
                ok = auto || mgr.isConnected
                DebugLog.i("AdbShell", "autoConnect result: auto=$auto isConnected=${mgr.isConnected}")
            }

            if (ok) {
                manager = mgr
                connected = true
                lastHost = host
                lastPort = port
                true
            } else {
                mgr.close()
                connected = false
                false
            }
        } catch (e: Exception) {
            connected = false
            false
        }
    }

    fun isConnected(): Boolean = connected && manager != null

    /**
     * Execute a shell command via ADB TLS stream.
     * Uses the same pattern as AutoLXB: openStream("shell:command") with exit code marker.
     */
    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        val mgr = manager
            ?: return@withContext ShellResult.Error("ADB not connected")

        try {
            // Use exit code marker pattern (same as AutoLXB)
            val marker = "__ST_RC_${System.currentTimeMillis()}__"
            val full = buildString {
                append(command.trim())
                append('\n')
                append("rc=\$?\n")
                append("echo ${marker}\$rc")
            }

            val stream = mgr.openStream("shell:$full")
            val input = stream.openInputStream()
            val sb = StringBuilder()
            val buf = ByteArray(4096)
            val deadline = System.currentTimeMillis() + 15_000

            while (System.currentTimeMillis() < deadline) {
                val avail = runCatching { input.available() }.getOrDefault(0)
                if (avail > 0) {
                    val n = input.read(buf, 0, minOf(avail, buf.size))
                    if (n > 0) {
                        sb.append(String(buf, 0, n))
                        if (sb.indexOf(marker) >= 0) break
                    }
                } else {
                    if (stream.isClosed) break
                    Thread.sleep(30)
                }
            }
            runCatching { input.close() }
            runCatching { stream.close() }

            val text = sb.toString()
            val idx = text.lastIndexOf(marker)
            if (idx < 0) {
                // Timeout or no marker - return raw output
                ShellResult.Success(text.trim())
            } else {
                val out = text.substring(0, idx).trim()
                val tail = text.substring(idx + marker.length).trim()
                val digits = tail.takeWhile { it.isDigit() }
                val code = digits.toIntOrNull() ?: -1
                if (code == 0) {
                    ShellResult.Success(out)
                } else {
                    ShellResult.Error("exit=$code: $out")
                }
            }
        } catch (e: Exception) {
            // Connection might have dropped
            if (e.message?.contains("closed", ignoreCase = true) == true ||
                e.message?.contains("reset", ignoreCase = true) == true) {
                connected = false
            }
            ShellResult.Error("ADB exec failed: ${e.message}")
        }
    }

    suspend fun reconnect(): Boolean {
        if (lastHost.isEmpty()) return false
        return connect(lastHost, lastPort)
    }

    fun disconnect() {
        runCatching { manager?.close() }
        manager = null
        connected = false
    }

    /**
     * Open a streaming shell session for long-running commands like getevent.
     * Returns an InputStream that yields command output line by line.
     * Caller is responsible for closing the stream and the connection.
     *
     * Returns null if not connected or on error.
     */
    fun openShellStream(command: String): Pair<java.io.InputStream, AutoCloseable>? {
        val mgr = manager ?: return null
        return try {
            val stream = mgr.openStream("shell:$command")
            val input = stream.openInputStream()
            Pair(input, AutoCloseable {
                runCatching { input.close() }
                runCatching { stream.close() }
            })
        } catch (e: Exception) {
            DebugLog.e("AdbShell", "openShellStream failed: ${e.message}")
            if (e.message?.contains("closed", ignoreCase = true) == true ||
                e.message?.contains("reset", ignoreCase = true) == true) {
                connected = false
            }
            null
        }
    }
}
