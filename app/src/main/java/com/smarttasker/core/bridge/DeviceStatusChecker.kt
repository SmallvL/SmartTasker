package com.smarttasker.core.bridge

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.smarttasker.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Comprehensive device & connection status checker.
 * Checks: developer options, wireless debugging, pairing, ADB connection, Core status.
 */
class DeviceStatusChecker(private val context: Context) {

    data class FullStatus(
        val developerOptionsOn: Boolean = false,
        val wirelessDebuggingOn: Boolean = false,
        val hasSavedEndpoint: Boolean = false,
        val savedEndpoint: String = "",
        val adbPortReachable: Boolean = false,
        val adbConnected: Boolean = false,
        val rootAvailable: Boolean = false,
        val shellAvailable: Boolean = false,
        val coreRunning: Boolean = false,
        val activeMode: String = "none", // "root", "adb", "sh", "none"
        val canRecord: Boolean = false,   // needs ADB or root
        val canExecute: Boolean = false,  // SH mode is enough
        val lastCheckTime: Long = 0,
        val isChecking: Boolean = false
    )

    private val _status = MutableStateFlow(FullStatus())
    val status: StateFlow<FullStatus> = _status.asStateFlow()

    /**
     * Run all checks and update state.
     */
    suspend fun checkAll() = withContext(Dispatchers.IO) {
        val previous = _status.value
        _status.value = _status.value.copy(isChecking = true)

        val devOptions = isDeveloperOptionsEnabled()
        val wirelessDebug = isWirelessDebuggingEnabled()
        val savedEp = getSavedEndpoint()
        val hasEndpoint = savedEp.first
        val endpoint = savedEp.second
        val adbConnected = com.smarttasker.core.direct.ShellExecutor.detectMode().let {
            it == com.smarttasker.core.direct.ShellExecutor.ShellMode.ADB ||
            it == com.smarttasker.core.direct.ShellExecutor.ShellMode.ADB_LOCAL
        }
        val shellAvailable = com.smarttasker.core.direct.ShellExecutor.detectMode().let {
            it == com.smarttasker.core.direct.ShellExecutor.ShellMode.SH
        }
        val rootAvail = tryRoot()
        // Port is reachable if ADB is already connected (TLS/TCP) OR raw TCP connects
        val portReachable = if (adbConnected) true else if (hasEndpoint) isPortReachable(endpoint) else false
        val coreRunning = adbConnected || rootAvail  // SH mode is NOT "core running"
        val canRecord = adbConnected || rootAvail     // getevent needs ADB or root
        val canExecute = adbConnected || rootAvail || shellAvailable  // tap/swipe/input works in SH
        val mode = when {
            rootAvail -> "root"
            adbConnected -> "adb"
            shellAvailable -> "sh"
            else -> "none"
        }

        val result = FullStatus(
            developerOptionsOn = devOptions,
            wirelessDebuggingOn = wirelessDebug,
            hasSavedEndpoint = hasEndpoint,
            savedEndpoint = endpoint,
            adbPortReachable = portReachable,
            adbConnected = adbConnected,
            rootAvailable = rootAvail,
            shellAvailable = shellAvailable,
            coreRunning = coreRunning,
            activeMode = mode,
            canRecord = canRecord,
            canExecute = canExecute,
            lastCheckTime = System.currentTimeMillis(),
            isChecking = false
        )
        _status.value = result
        // Only log when key status fields actually change (ignore lastCheckTime/isChecking)
        val statusChanged = result.activeMode != previous.activeMode ||
            result.adbConnected != previous.adbConnected ||
            result.rootAvailable != previous.rootAvailable ||
            result.shellAvailable != previous.shellAvailable ||
            result.adbPortReachable != previous.adbPortReachable ||
            result.coreRunning != previous.coreRunning ||
            result.canRecord != previous.canRecord ||
            result.canExecute != previous.canExecute ||
            result.developerOptionsOn != previous.developerOptionsOn ||
            result.wirelessDebuggingOn != previous.wirelessDebuggingOn ||
            result.hasSavedEndpoint != previous.hasSavedEndpoint
        if (statusChanged) {
            DebugLog.i("Status", "Status changed: mode=${result.activeMode} adb=${result.adbConnected} root=${result.rootAvailable} (was: mode=${previous.activeMode} adb=${previous.adbConnected} root=${previous.rootAvailable})")
        }
        result
    }

    /**
     * Quick check - update connection-related fields including port reachability.
     */
    suspend fun quickCheck() = withContext(Dispatchers.IO) {
        val current = _status.value
        val adbConnected = com.smarttasker.core.direct.ShellExecutor.detectMode().let {
            it == com.smarttasker.core.direct.ShellExecutor.ShellMode.ADB ||
            it == com.smarttasker.core.direct.ShellExecutor.ShellMode.ADB_LOCAL
        }
        val shellAvailable = com.smarttasker.core.direct.ShellExecutor.detectMode().let {
            it == com.smarttasker.core.direct.ShellExecutor.ShellMode.SH
        }
        val rootAvail = tryRoot()
        val coreRunning = adbConnected || rootAvail
        val canRecord = adbConnected || rootAvail
        val canExecute = adbConnected || rootAvail || shellAvailable
        val mode = when {
            rootAvail -> "root"
            adbConnected -> "adb"
            shellAvailable -> "sh"
            else -> "none"
        }
        // Port is reachable if ADB is already connected (TLS handles the real connection)
        // or if raw TCP connect succeeds to the saved endpoint
        val portReachable = if (adbConnected) {
            true
        } else if (current.hasSavedEndpoint) {
            isPortReachable(current.savedEndpoint)
        } else {
            false
        }
        _status.value = current.copy(
            adbConnected = adbConnected,
            adbPortReachable = portReachable,
            rootAvailable = rootAvail,
            shellAvailable = shellAvailable,
            coreRunning = coreRunning,
            activeMode = mode,
            canRecord = canRecord,
            canExecute = canExecute,
            lastCheckTime = System.currentTimeMillis()
        )
    }

    /**
     * Check if developer options is enabled.
     */
    private fun isDeveloperOptionsEnabled(): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Check if wireless debugging is enabled (Android 11+).
     */
    private fun isWirelessDebuggingEnabled(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return try {
            Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) != 0
        } catch (_: Exception) {
            // Try alternative method
            try {
                val process = Runtime.getRuntime().exec(arrayOf("settings", "get", "global", "adb_wifi_enabled"))
                val output = process.inputStream.bufferedReader().readText().trim()
                process.waitFor()
                output == "1"
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Get saved ADB endpoint from SharedPreferences.
     */
    private fun getSavedEndpoint(): Pair<Boolean, String> {
        val prefs = context.getSharedPreferences("smarttasker_config", Context.MODE_PRIVATE)
        val host = prefs.getString("adb_host", "")?.trim().orEmpty()
        val port = prefs.getInt("adb_port", 0)
        val has = host.isNotBlank() && port in 1..65535
        return if (has) Pair(true, "$host:$port") else Pair(false, "")
    }

    /**
     * Check if the saved ADB port is reachable.
     */
    private fun isPortReachable(endpoint: String): Boolean {
        return try {
            val parts = endpoint.split(":")
            val host = parts[0]
            val port = parts[1].toInt()
            Socket().use { sock ->
                sock.connect(InetSocketAddress(host, port), 2000)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Check if root is available.
     * Try multiple su paths (some emulators only have /system/xbin/su).
     */
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
                if (exitCode == 0 && output.contains("uid=0")) return true
            } catch (_: Exception) { /* try next */ }
        }
        return false
    }
}
