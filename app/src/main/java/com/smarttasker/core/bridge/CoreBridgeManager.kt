package com.smarttasker.core.bridge

import android.content.Context
import com.smarttasker.core.adapter.RouteAdapter
import com.smarttasker.core.adapter.TraceAdapter
import com.smarttasker.core.direct.DirectCoreBridge
import com.smarttasker.core.direct.ShellExecutor
import com.smarttasker.core.parser.LlmTaskSpecParser
import com.smarttasker.core.parser.TaskSpecParser
import com.smarttasker.util.DebugLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Singleton manager for CoreBridge lifecycle.
 * Provides reactive state flows for UI consumption.
 *
 * On startup, auto-connects to saved ADB endpoint if available.
 */
class CoreBridgeManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Device status checker
    val deviceStatusChecker = DeviceStatusChecker(context)

    // Core bridge instance - default to direct (standalone)
    private var _bridge: CoreBridge = DirectCoreBridge(context)
    val bridge: CoreBridge get() = _bridge

    // Current mode
    private var _isDirectMode = MutableStateFlow(true)
    val isDirectMode: StateFlow<Boolean> = _isDirectMode.asStateFlow()

    // Shell mode (root/adb/none)
    private var _shellMode = MutableStateFlow(ShellExecutor.ShellMode.NONE)
    val shellMode: StateFlow<ShellExecutor.ShellMode> = _shellMode.asStateFlow()

    // Whether we're auto-reconnecting
    private var _isReconnecting = MutableStateFlow(false)
    val isReconnecting: StateFlow<Boolean> = _isReconnecting.asStateFlow()

    // Parsers
    private val llmParser = LlmTaskSpecParser(
        apiUrl = "https://api.openai.com/v1",
        apiKey = ""  // Will be loaded from settings
    )

    // ===== Reactive State =====

    private val _coreStatus = MutableStateFlow<CoreStatus>(CoreStatus.Unknown)
    val coreStatus: StateFlow<CoreStatus> = _coreStatus.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    // Status polling
    private var statusPollJob: Job? = null

    init {
        scope.launch {
            // Full device status check
            deviceStatusChecker.checkAll()
            // Try auto-connect to saved ADB endpoint first
            autoConnectSavedAdb()
            val mode = ShellExecutor.detectMode()
            _shellMode.value = mode
            startStatusPolling()
        }
    }

    /**
     * Try to connect to saved ADB endpoint (from previous pairing).
     * If saved port is 0, uses NSD autoConnect discovery.
     * If saved port is stale (connect fails), also falls back to autoConnect.
     */
    private suspend fun autoConnectSavedAdb() {
        val prefs = context.getSharedPreferences("smarttasker_config", Context.MODE_PRIVATE)
        val host = prefs.getString("adb_host", "")?.trim().orEmpty()
        val port = prefs.getInt("adb_port", 0)
        if (host.isBlank()) {
            DebugLog.d("CoreMgr", "No saved ADB host found")
            return
        }
        // port=0 means "use autoConnect"; port in 1..65535 means try direct first
        if (port !in 0..65535) {
            DebugLog.d("CoreMgr", "Invalid saved ADB port: $port")
            return
        }

        DebugLog.i("CoreMgr", "Auto-connecting to saved ADB: $host:$port (port=0 means autoConnect)")
        _isReconnecting.value = true
        try {
            ShellExecutor.resetCache()
            val connected = ShellExecutor.connectAdb(host, port)
            DebugLog.i("CoreMgr", "Auto-connect result: $connected")
            if (connected) {
                _shellMode.value = ShellExecutor.ShellMode.ADB
            } else if (port > 0) {
                // Direct port failed — try NSD autoConnect as fallback
                DebugLog.i("CoreMgr", "Direct port $port failed, trying autoConnect fallback...")
                val autoConnected = ShellExecutor.connectAdb(host, 0)
                DebugLog.i("CoreMgr", "AutoConnect fallback result: $autoConnected")
                if (autoConnected) {
                    _shellMode.value = ShellExecutor.ShellMode.ADB
                }
            }
        } catch (e: Exception) {
            DebugLog.e("CoreMgr", "Auto-connect failed: ${e.message}")
        } finally {
            _isReconnecting.value = false
        }
    }

    /**
     * Switch to direct mode (standalone, uses root/adb shell).
     * Does NOT call resetCache() — preserves any existing ADB/root connection.
     */
    fun useDirectBridge() {
        _bridge = DirectCoreBridge(context)
        _isDirectMode.value = true
        scope.launch {
            _shellMode.value = ShellExecutor.detectMode()
            checkStatus()
        }
    }

    /**
     * Force reset and re-detect shell mode. Use after pairing changes.
     * Unlike useDirectBridge(), this actually resets the connection cache.
     */
    fun forceResetAndRefresh() {
        scope.launch {
            ShellExecutor.resetCache()
            _shellMode.value = ShellExecutor.detectMode()
            checkStatus()
        }
    }

    /**
     * Switch to TCP bridge (connects to external lxb-core process).
     */
    fun useTcpBridge(host: String = "127.0.0.1", port: Int = 12345) {
        _bridge = AutoLxbCoreBridge(host, port)
        _isDirectMode.value = false
        scope.launch { checkStatus() }
    }

    /**
     * Switch to mock bridge (for UI development only).
     */
    fun useMockBridge() {
        _bridge = MockCoreBridge()
        _isDirectMode.value = false
        scope.launch { checkStatus() }
    }

    /**
     * Update LLM configuration for AI task parsing.
     */
    fun configureLlm(apiKey: String, baseUrl: String, model: String = "gpt-4o-mini") {
        // Will be implemented with DataStore integration
    }

    /**
     * Get the rule-based task spec parser.
     */
    fun getTaskSpecParser(): TaskSpecParser = TaskSpecParser

    /**
     * Get the LLM-enhanced task spec parser.
     */
    fun getLlmParser(): LlmTaskSpecParser = llmParser

    // ===== Status Polling =====

    private fun startStatusPolling() {
        statusPollJob?.cancel()
        statusPollJob = scope.launch {
            while (isActive) {
                checkStatus()
                delay(5000) // Poll every 5 seconds
            }
        }
    }

    private suspend fun checkStatus() {
        _isChecking.value = true
        try {
            // Refresh device status
            deviceStatusChecker.quickCheck()

            // If shell mode is NONE, try to auto-reconnect
            if (_shellMode.value == ShellExecutor.ShellMode.NONE) {
                autoConnectSavedAdb()
                val newMode = ShellExecutor.detectMode()
                _shellMode.value = newMode
                // If still NONE, detectMode() will try local ADB automatically
            } else {
                _shellMode.value = ShellExecutor.detectMode()
            }

            when (val result = bridge.getCoreStatus()) {
                is CoreStatusResult.Running -> {
                    _coreStatus.value = CoreStatus.Running(
                        port = result.port,
                        pid = result.pid
                    )
                }
                is CoreStatusResult.Stopped -> {
                    _coreStatus.value = CoreStatus.Stopped(result.reason)
                }
                is CoreStatusResult.Error -> {
                    _coreStatus.value = CoreStatus.Error(result.message)
                }
            }
        } catch (e: Exception) {
            _coreStatus.value = CoreStatus.Error(e.message ?: "连接失败")
        } finally {
            _isChecking.value = false
        }
    }

    /**
     * Force an immediate status check.
     */
    suspend fun refreshStatus() {
        checkStatus()
    }

    fun destroy() {
        statusPollJob?.cancel()
        scope.cancel()
    }

    companion object {
        @Volatile
        private var instance: CoreBridgeManager? = null

        fun getInstance(context: Context): CoreBridgeManager {
            return instance ?: synchronized(this) {
                instance ?: CoreBridgeManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

/**
 * UI-friendly core status.
 */
sealed class CoreStatus {
    object Unknown : CoreStatus()
    data class Running(val port: Int, val pid: Int) : CoreStatus()
    data class Stopped(val reason: String) : CoreStatus()
    data class Error(val message: String) : CoreStatus()

    val isRunning: Boolean get() = this is Running
    val displayText: String get() = when (this) {
        is Unknown -> "检查中..."
        is Running -> "Core 运行中"
        is Stopped -> "Core 未运行"
        is Error -> "连接异常"
    }
    val statusColor: String get() = when (this) {
        is Running -> "success"
        is Stopped -> "warning"
        is Error -> "danger"
        is Unknown -> "neutral"
    }
}
