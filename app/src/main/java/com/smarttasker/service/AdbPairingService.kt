package com.smarttasker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.smarttasker.MainActivity
import com.smarttasker.R
import com.smarttasker.core.adb.WirelessAdbConnectionManager
import io.github.muntashirakon.adb.AdbPairingRequiredException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket
import java.security.Security
import java.util.concurrent.TimeUnit
import com.smarttasker.util.DebugLog

/**
 * Foreground service that handles wireless ADB pairing and auto-connect.
 *
 * Uses NSD to discover `_adb-tls-pairing._tcp.` and `_adb-tls-connect._tcp.` services,
 * accepts a pairing code, performs TLS pairing via [WirelessAdbConnectionManager],
 * and persists the ADB connect endpoint to SharedPreferences.
 */
class AdbPairingService : Service() {

    companion object {
        const val ACTION_START_GUIDE =
            "com.smarttasker.action.ADB_PAIRING_START_GUIDE"
        const val ACTION_SUBMIT_PAIRING =
            "com.smarttasker.action.ADB_PAIRING_SUBMIT"
        const val ACTION_CONNECT =
            "com.smarttasker.action.ADB_PAIRING_CONNECT"
        const val ACTION_STOP =
            "com.smarttasker.action.ADB_PAIRING_STOP"

        const val ACTION_STATUS =
            "com.smarttasker.action.ADB_PAIRING_STATUS"
        const val EXTRA_STATE = "state"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_RUNNING = "running"
        const val EXTRA_PAIR_CODE = "pair_code"

        private const val REMOTE_INPUT_PAIR_CODE = "adb_pair_code"

        private const val CHANNEL_ID = "smarttasker_adb_bootstrap"
        private const val CHANNEL_NAME = "SmartTasker ADB Bootstrap"
        private const val NOTIFICATION_ID = 1003

        private const val PREFS_NAME = "smarttasker_config"
        private const val KEY_ADB_HOST = "adb_host"
        private const val KEY_ADB_PORT = "adb_port"

        private const val PAIRING_SERVICE_TYPE = "_adb-tls-pairing._tcp."
        private const val CONNECT_SERVICE_TYPE = "_adb-tls-connect._tcp."

        private const val CONNECT_ENDPOINT_WAIT_MS = 8_000L
        private const val AUTO_CONNECT_TIMEOUT_MS = 10_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private data class Endpoint(val host: String, val port: Int) {
        fun asText(): String = "$host:$port"
    }

    private var nsdManager: NsdManager? = null
    private var pairingDiscoveryListener: NsdManager.DiscoveryListener? = null
    private var connectDiscoveryListener: NsdManager.DiscoveryListener? = null
    private var pairingResolveListener: NsdManager.ResolveListener? = null
    private var connectResolveListener: NsdManager.ResolveListener? = null
    private var latestPairingEndpoint: Endpoint? = null
    private var latestConnectEndpoint: Endpoint? = null

    private var running = false
    private var currentState = "IDLE"
    private var currentMessage = "Idle"

    override fun onCreate() {
        super.onCreate()
        createChannel()
        nsdManager = getSystemService(Context.NSD_SERVICE) as? NsdManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_GUIDE -> startGuide()
            ACTION_SUBMIT_PAIRING -> {
                val pairCode = readPairCodeFromIntent(intent)
                submitPairing(pairCode)
            }
            ACTION_CONNECT -> performAutoConnect()
            ACTION_STOP -> stopService()
            else -> {
                if (!running) startGuide() else updateNotification()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopDiscovery()
        scope.cancel()
        super.onDestroy()
    }

    // -----------------------------------------------------------------------
    // State machine
    // -----------------------------------------------------------------------

    private fun startGuide() {
        DebugLog.i("AdbPair", "Starting ADB pairing guide")
        if (!running) {
            running = true
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
        setState("GUIDE_SETTINGS", "Open Developer Options and enable Wireless debugging.")
        startDiscovery()
        setState("WAIT_INPUT", "Waiting for pairing code. Pair/connect endpoint auto-detected.")
        updateNotification()
    }

    private fun submitPairing(pairCodeRaw: String) {
        DebugLog.i("AdbPair", "Submitting pairing code: ${pairCodeRaw.take(2)}****")
        val pairCode = normalizePairCode(pairCodeRaw)
        if (pairCode.isEmpty()) {
            setState("WAIT_INPUT", "Invalid input. Please provide a pairing code.")
            updateNotification()
            return
        }

        val pairing = latestPairingEndpoint
        if (pairing == null) {
            setState("WAIT_INPUT", "Pairing endpoint not detected yet. Keep Wireless debugging pairing page open and retry.")
            updateNotification()
            return
        }
        val connect = latestConnectEndpoint

        scope.launch {
            setState("PAIRING", "Checking pairing endpoint reachability...")
            updateNotification()

            val reachable = checkTcpReachable(pairing.host, pairing.port, 3000)
            if (!reachable) {
                finish("FAILED", "Pairing endpoint not reachable: ${pairing.host}:${pairing.port}")
                return@launch
            }

            val tlsPrep = prepareConscryptProvider()
            setState("PAIRING", "TLS provider prepared: $tlsPrep")
            updateNotification()

            val manager = WirelessAdbConnectionManager(applicationContext)
            manager.setTimeout(12, TimeUnit.SECONDS)
            manager.setThrowOnUnauthorised(true)

            try {
                manager.setHostAddress(pairing.host)
                val pairOk = runCatching { manager.pair(pairing.port, pairCode) }.getOrElse { e ->
                    val reason = e.message.orEmpty()
                    if (isLikelyTlsRsaError(reason)) {
                        runCatching { manager.rotateKeyMaterial() }
                        val retry = runCatching { manager.pair(pairing.port, pairCode) }.getOrElse { e3 ->
                            DebugLog.e("AdbPair", "TLS/RSA error: ${formatExceptionChain(e3)}")
                finish("FAILED", "ADB pair failed (TLS/RSA): ${formatExceptionChain(e3)}. Reopen pairing dialog and retry.")
                            return@launch
                        }
                        if (retry) return@getOrElse true
                    }
                    // Refresh discovery and retry once
                    startDiscovery()
                    Thread.sleep(500L)
                    val retryEndpoint = latestPairingEndpoint
                    if (retryEndpoint != null) {
                        manager.setHostAddress(retryEndpoint.host)
                        return@getOrElse runCatching { manager.pair(retryEndpoint.port, pairCode) }.getOrElse { e2 ->
                            finish("FAILED", "ADB pair failed (TLS/protocol): ${formatExceptionChain(e2)}. Reopen \"Pair device with pairing code\" and try again.")
                            return@launch
                        }
                    }
                    finish("FAILED", "ADB pair failed (TLS/protocol): ${formatExceptionChain(e)}. Reopen \"Pair device with pairing code\" and try again.")
                    return@launch
                }
                if (!pairOk) {
                    finish("FAILED", "ADB pair returned false. Reopen pairing dialog and retry.")
                    return@launch
                }

                // Refresh connect endpoint after pairing with longer wait
                startDiscovery()
                val connectReady = waitForConnectEndpoint(CONNECT_ENDPOINT_WAIT_MS)
                val persisted = if (connectReady != null) {
                    connectReady
                } else if (connect != null) {
                    connect
                } else {
                    // Fallback: save flag so AdbShellExecutor uses autoConnect
                    DebugLog.i("AdbPair", "No connect endpoint found after ${CONNECT_ENDPOINT_WAIT_MS}ms, will use auto-connect")
                    Endpoint(pairing.host, 0) // port=0 signals "use autoConnect"
                }
                persistAdbEndpoint(persisted)

                DebugLog.i("AdbPair", "Pairing succeeded! Endpoint: ${persisted.asText()}")
                finish("PAIRED", "Pairing succeeded. Endpoint: ${persisted.asText()}")
            } catch (e: AdbPairingRequiredException) {
                finish("FAILED", "ADB reports pairing required: ${formatExceptionChain(e)}")
            } catch (e: Exception) {
                finish("FAILED", "Wireless ADB pairing failed: ${formatExceptionChain(e)}")
            } finally {
                runCatching { manager.close() }
            }
        }
    }

    private fun performAutoConnect() {
        if (!running) {
            running = true
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }

        scope.launch {
            setState("CONNECTING", "Loading saved ADB endpoint...")
            updateNotification()

            val saved = loadAdbEndpoint()
            if (saved == null) {
                finish("FAILED", "No saved ADB endpoint. Please pair first.")
                return@launch
            }

            val manager = WirelessAdbConnectionManager(applicationContext)
            manager.setTimeout(10, TimeUnit.SECONDS)
            manager.setThrowOnUnauthorised(true)

            try {
                manager.setHostAddress(saved.host)
                val direct = runCatching { manager.connect(saved.host, saved.port) }.getOrDefault(false)
                val auto = if (!direct) {
                    runCatching { manager.autoConnect(applicationContext, AUTO_CONNECT_TIMEOUT_MS) }.getOrDefault(false)
                } else false
                val connected = direct || auto || manager.isConnected

                if (connected) {
                    finish("CONNECTED", "Connected to ADB at ${saved.asText()}")
                } else {
                    finish("FAILED", "Could not connect to ADB at ${saved.asText()}. Ensure wireless debugging is enabled.")
                }
            } catch (e: Exception) {
                finish("FAILED", "Auto-connect failed: ${formatExceptionChain(e)}")
            } finally {
                runCatching { manager.close() }
            }
        }
    }

    private fun stopService() {
        stopDiscovery()
        running = false
        setState("IDLE", "Service stopped.")
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    private fun finish(state: String, message: String) {
        setState(state, message)
        if (state == "RUNNING" || state == "CONNECTED" || state == "PAIRED") {
            updateNotification()
            return
        }
        running = false
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    // -----------------------------------------------------------------------
    // NSD discovery
    // -----------------------------------------------------------------------

    private fun startDiscovery() {
        stopDiscovery()
        val mgr = nsdManager ?: return

        pairingResolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host?.hostAddress.orEmpty()
                val port = serviceInfo.port
                if (host.isNotBlank() && port in 1..65535) {
                    latestPairingEndpoint = Endpoint(host, port)
                    if (currentState == "WAIT_INPUT") {
                        setState("WAIT_INPUT", "Detected pairing endpoint: ${latestPairingEndpoint?.asText().orEmpty()}")
                        updateNotification()
                    }
                }
            }
        }

        connectResolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host?.hostAddress.orEmpty()
                val port = serviceInfo.port
                if (host.isNotBlank() && port in 1..65535) {
                    latestConnectEndpoint = Endpoint(host, port)
                    if (currentState == "WAIT_INPUT") {
                        setState("WAIT_INPUT", "Detected connect endpoint: ${latestConnectEndpoint?.asText().orEmpty()}")
                        updateNotification()
                    }
                }
            }
        }

        pairingDiscoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                setState("WAIT_INPUT", "NSD discovery start failed: $errorCode")
                updateNotification()
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val rs = pairingResolveListener ?: return
                runCatching { mgr.resolveService(serviceInfo, rs) }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
        }

        runCatching {
            mgr.discoverServices(PAIRING_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, pairingDiscoveryListener)
        }.onFailure {
            setState("WAIT_INPUT", "NSD discovery unavailable: ${it.message}")
            updateNotification()
        }

        connectDiscoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val rs = connectResolveListener ?: return
                runCatching { mgr.resolveService(serviceInfo, rs) }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
        }

        runCatching {
            mgr.discoverServices(CONNECT_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, connectDiscoveryListener)
        }
    }

    private fun stopDiscovery() {
        val mgr = nsdManager ?: return
        pairingDiscoveryListener?.let { runCatching { mgr.stopServiceDiscovery(it) } }
        connectDiscoveryListener?.let { runCatching { mgr.stopServiceDiscovery(it) } }
        pairingDiscoveryListener = null
        connectDiscoveryListener = null
        pairingResolveListener = null
        connectResolveListener = null
    }

    private fun waitForConnectEndpoint(timeoutMs: Long): Endpoint? {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            val ep = latestConnectEndpoint
            if (ep != null) return ep
            Thread.sleep(100L)
        }
        return latestConnectEndpoint
    }

    // -----------------------------------------------------------------------
    // State & broadcast
    // -----------------------------------------------------------------------

    private fun setState(state: String, message: String) {
        currentState = state
        currentMessage = message
        val broadcast = Intent(ACTION_STATUS).apply {
            setPackage(packageName)
            putExtra(EXTRA_STATE, currentState)
            putExtra(EXTRA_MESSAGE, currentMessage)
            putExtra(EXTRA_RUNNING, running)
        }
        sendBroadcast(broadcast)
    }

    // -----------------------------------------------------------------------
    // SharedPreferences persistence
    // -----------------------------------------------------------------------

    private fun persistAdbEndpoint(endpoint: Endpoint) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ADB_HOST, endpoint.host)
            .putInt(KEY_ADB_PORT, endpoint.port)
            .apply()
    }

    private fun loadAdbEndpoint(): Endpoint? {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val host = prefs.getString(KEY_ADB_HOST, "")?.trim().orEmpty()
        val port = prefs.getInt(KEY_ADB_PORT, 0)
        if (host.isBlank() || port !in 1..65535) return null
        return Endpoint(host, port)
    }

    // -----------------------------------------------------------------------
    // Notification
    // -----------------------------------------------------------------------

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Wireless ADB pairing status"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun updateNotification() {
        val n = buildNotification()
        if (running) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, n)
        }
    }

    private fun buildNotification(): Notification {
        createChannel()
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val launchPending = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val submitPending = PendingIntent.getService(
            this, 1,
            Intent(this, AdbPairingService::class.java).setAction(ACTION_SUBMIT_PAIRING),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val codeInput = RemoteInput.Builder(REMOTE_INPUT_PAIR_CODE)
            .setLabel("输入 6 位配对码")
            .build()

        val submitAction = NotificationCompat.Action.Builder(
            0, "提交配对码", submitPending
        )
            .addRemoteInput(codeInput)
            .setAllowGeneratedReplies(false)
            .build()

        val content = buildNotificationContent()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(buildNotificationTitle())
            .setContentText(content.lines().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(launchPending)
            .addAction(submitAction)
            .build()
    }

    private fun buildNotificationTitle(): String {
        return when (currentState) {
            "PAIRED" -> "配对成功"
            "CONNECTED" -> "ADB 已连接"
            "FAILED" -> "配对失败"
            "PAIRING" -> "正在配对..."
            "CONNECTING" -> "正在连接..."
            else -> "ADB 无线配对"
        }
    }

    private fun buildNotificationContent(): String {
        val lines = ArrayList<String>(4)
        val main = when (currentState) {
            "GUIDE_SETTINGS" -> "Open Developer Options > Wireless debugging"
            "WAIT_INPUT" -> "[1] In wireless debug page, tap 'Pair device with pairing code'. [2] Long-press this notification, tap 'Submit code' to enter 6-digit code."
            "PAIRING" -> "Pairing in progress, keep wireless debugging open..."
            "PAIRED" -> "Pairing succeeded!"
            "CONNECTING" -> "Connecting to ADB..."
            "CONNECTED" -> "ADB connected, you can return to the app."
            "FAILED" -> "Pairing failed: $currentMessage"
            else -> currentMessage
        }
        lines.add(main)
        latestPairingEndpoint?.let {
            lines.add("配对端点: ${it.asText()}")
        }
        latestConnectEndpoint?.let {
            lines.add("连接端点: ${it.asText()}")
        }
        if (currentState == "FAILED") {
            lines.add("Details: $currentMessage")
        }
        return lines.joinToString("\n")
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun readPairCodeFromIntent(intent: Intent): String {
        val fromRemote = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(REMOTE_INPUT_PAIR_CODE)
            ?.toString()
            ?.trim()
            .orEmpty()
        if (fromRemote.isNotEmpty()) return fromRemote
        return intent.getStringExtra(EXTRA_PAIR_CODE).orEmpty()
    }

    private fun normalizePairCode(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty()) return ""
        val digits = buildString {
            for (c in t) {
                if (c.isDigit()) append(c)
            }
        }
        return if (digits.length >= 6) digits else t
    }

    private fun checkTcpReachable(host: String, port: Int, timeoutMs: Int): Boolean {
        return runCatching {
            Socket().use { sock ->
                sock.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        }.getOrDefault(false)
    }

    private fun prepareConscryptProvider(): String {
        return runCatching {
            val clazz = Class.forName("org.conscrypt.OpenSSLProvider")
            val provider = clazz.getDeclaredConstructor().newInstance() as java.security.Provider
            // Always remove old Conscrypt and insert the bundled version at priority 1
            val existing = Security.getProvider(provider.name)
            if (existing != null) {
                Security.removeProvider(provider.name)
            }
            Security.insertProviderAt(provider, 1)
            // Also set as default SSLContext so libadb uses it
            runCatching {
                val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
                sslContext.init(null, null, null)
                javax.net.ssl.SSLContext.setDefault(sslContext)
            }
            provider.name
        }.getOrElse { e ->
            "unavailable(${e.javaClass.simpleName}:${e.message})"
        }
    }

    private fun isLikelyTlsRsaError(msg: String): Boolean {
        val s = msg.lowercase()
        return s.contains("failure in ssl library")
                || s.contains("protocol error")
                || s.contains("rsa")
                || s.contains("conscrypt")
                || s.contains("openssl_internal")
                || s.contains("exportkeyingmaterial")
                || s.contains("nosuchmethodexception")
    }

    private fun formatExceptionChain(e: Throwable): String {
        val parts = ArrayList<String>(4)
        var cur: Throwable? = e
        var depth = 0
        while (cur != null && depth < 4) {
            val msg = cur.message?.trim().orEmpty()
            if (msg.isNotEmpty()) {
                parts.add("${cur.javaClass.simpleName}: $msg")
            } else {
                parts.add(cur.javaClass.simpleName)
            }
            cur = cur.cause
            depth++
        }
        return if (parts.isEmpty()) "unknown error" else parts.joinToString(" <- ")
    }
}
