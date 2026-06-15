package com.smarttasker.core.record.ui

import android.app.*
import android.provider.Settings
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import com.smarttasker.MainActivity
import com.smarttasker.R
import com.smarttasker.core.record.session.RecordingSessionManager
import com.smarttasker.util.DebugLog
import kotlinx.coroutines.*

class RecordingOverlayService : Service() {

    companion object {
        const val ACTION_START = "com.smarttasker.action.RECORD_START"
        const val ACTION_STOP = "com.smarttasker.action.RECORD_STOP"
        const val ACTION_PAUSE = "com.smarttasker.action.RECORD_PAUSE"
        const val ACTION_RESUME = "com.smarttasker.action.RECORD_RESUME"
        const val ACTION_INSERT_WAIT = "com.smarttasker.action.RECORD_INSERT_WAIT"
        const val ACTION_INSERT_SCREENSHOT = "com.smarttasker.action.RECORD_INSERT_SCREENSHOT"

        /**
         * Broadcast action emitted when the service refuses to start (e.g. SH mode without
         * ADB/root). UI screens can subscribe to it to roll back their optimistic
         * "isRecording=true" state and surface the error.
         */
        const val ACTION_RECORD_START_FAILED = "com.smarttasker.action.RECORD_START_FAILED"
        const val EXTRA_FAIL_REASON = "fail_reason"

        /** Intent extra: target app package name to auto-launch after recording starts */
        const val EXTRA_TARGET_PACKAGE = "target_package"
        const val EXTRA_TARGET_APP_NAME = "target_app_name"

        private const val CHANNEL_ID = "smarttasker_recording"
        private const val NOTIFICATION_ID = 1004
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var sessionManager: RecordingSessionManager? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null
    private var startTime = 0L
    private var targetPackage: String? = null

    // UI elements
    private var statusText: TextView? = null
    private var stepCountText: TextView? = null
    private var timerText: TextView? = null
    private var pauseBtn: TextView? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE)
                startRecording()
            }
            ACTION_STOP -> stopRecording()
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_INSERT_WAIT -> insertWait()
            ACTION_INSERT_SCREENSHOT -> insertScreenshot()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        hideOverlay()
        timerJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startRecording() {
        if (!Settings.canDrawOverlays(this)) {
            DebugLog.e("RecOverlay", "Cannot draw overlays: permission not granted")
            notifyStartFailed("未授予悬浮窗权限，无法开始录制")
            stopSelf()
            return
        }
        // [BugFix #1] Pre-flight capability check before promoting to foreground.
        // SH mode is not sufficient for getevent streaming; without this guard the service
        // would still startForeground + show a notification, leaving the user with no
        // actionable feedback when recording silently fails.
        scope.launch {
            val capability = com.smarttasker.core.direct.ShellExecutor.getCapabilityDescription()
            val canRecord = com.smarttasker.core.direct.ShellExecutor.canRecord()
            if (!canRecord) {
                val reason = "当前 Shell 模式不支持录制：$capability\n请开启无线调试并连接后重试"
                DebugLog.e("RecOverlay", "Refusing to start: $reason")
                notifyStartFailed(reason)
                stopSelf()
                return@launch
            }
            val notification = buildNotification("SmartTask 正在录制")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            // Pass ADB executor for TLS streaming (getevent needs shell access to /dev/input)
            val adbExecutor = com.smarttasker.core.direct.ShellExecutor.getAdbExecutor()
            sessionManager = RecordingSessionManager(applicationContext, adbExecutor)
            val manager = sessionManager ?: run {
                DebugLog.e("RecOverlay", "Session manager is null")
                notifyStartFailed("录制管理器初始化失败")
                stopSelf()
                return@launch
            }
            val ok = manager.startRecording()
            if (ok) {
                startTime = System.currentTimeMillis()
                showOverlay()
                startTimer()
                // BUG1 fix: Auto-launch target app after recording starts
                launchTargetApp()
                DebugLog.i("RecOverlay", "Recording started via overlay")
            } else {
                val error = sessionManager?.errorMessage?.value ?: "Unknown error"
                DebugLog.e("RecOverlay", "Failed to start recording: $error")
                android.widget.Toast.makeText(applicationContext, error, android.widget.Toast.LENGTH_LONG).show()
                stopSelf()
            }
        }
    }

    private fun notifyStartFailed(reason: String) {
        // Toast so the user sees something immediately even if the UI screen is not visible.
        android.widget.Toast.makeText(applicationContext, reason, android.widget.Toast.LENGTH_LONG).show()
        // Broadcast so the UI screen can roll back its optimistic "isRecording=true".
        val broadcast = Intent(ACTION_RECORD_START_FAILED).apply {
            setPackage(packageName)
            putExtra(EXTRA_FAIL_REASON, reason)
        }
        sendBroadcast(broadcast)
    }

    private fun stopRecording() {
        scope.launch {
            sessionManager?.stopRecording()
            hideOverlay()
            timerJob?.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            DebugLog.i("RecOverlay", "Recording stopped")
        }
    }

    private fun pauseRecording() {
        sessionManager?.pauseRecording()
        statusText?.text = "⏸ 已暂停"
        pauseBtn?.text = "继续"
        pauseBtn?.setOnClickListener { resumeRecording() }
    }

    private fun resumeRecording() {
        sessionManager?.resumeRecording()
        statusText?.text = "● REC"
        pauseBtn?.text = "暂停"
        pauseBtn?.setOnClickListener { pauseRecording() }
    }

    private fun insertWait() {
        sessionManager?.insertWait(3000)
        Toast.makeText(applicationContext, "已插入 3 秒等待", Toast.LENGTH_SHORT).show()
    }

    private fun insertScreenshot() {
        scope.launch {
            sessionManager?.insertScreenshot()
            Toast.makeText(applicationContext, "已插入截图", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Auto-launch the target app after recording starts.
     * If targetPackage is empty, try to resolve from the task's targetAppName.
     */
    private fun launchTargetApp() {
        val pkg = targetPackage
        if (pkg.isNullOrEmpty()) {
            DebugLog.i("RecOverlay", "No target package specified, skipping auto-launch")
            return
        }
        // Delay slightly to let recording stabilize before switching apps
        scope.launch {
            delay(500)
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    startActivity(launchIntent)
                    DebugLog.i("RecOverlay", "Auto-launched target app: $pkg")
                } else {
                    // Fallback: try monkey command to launch the app
                    DebugLog.w("RecOverlay", "No launch intent for $pkg, trying monkey command")
                    try {
                        val cmd = "monkey -p $pkg -c android.intent.category.LAUNCHER 1"
                        Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
                        DebugLog.i("RecOverlay", "Launched via monkey: $pkg")
                    } catch (e: Exception) {
                        DebugLog.e("RecOverlay", "Monkey launch also failed: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                DebugLog.e("RecOverlay", "Failed to launch target app: ${e.message}")
            }
        }
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.recording_overlay, null)

        statusText = overlayView?.findViewById(R.id.rec_status)
        stepCountText = overlayView?.findViewById(R.id.rec_step_count)
        timerText = overlayView?.findViewById(R.id.rec_timer)
        pauseBtn = overlayView?.findViewById(R.id.rec_pause)

        overlayView?.findViewById<TextView>(R.id.rec_stop)?.setOnClickListener { stopRecording() }
        overlayView?.findViewById<TextView>(R.id.rec_wait)?.setOnClickListener { insertWait() }
        overlayView?.findViewById<TextView>(R.id.rec_screenshot)?.setOnClickListener { insertScreenshot() }
        pauseBtn?.setOnClickListener { pauseRecording() }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }

        // Make draggable — but only when dragging, not when clicking buttons
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        val dragThreshold = 10 // pixels — only start dragging after this much movement
        overlayView?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    false // Don't consume — let child views (buttons) receive the event
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (!isDragging && (Math.abs(dx) > dragThreshold || Math.abs(dy) > dragThreshold)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager?.updateViewLayout(overlayView, params)
                    }
                    isDragging
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        true // Consumed by drag
                    } else {
                        false // Not a drag — let child views handle the click
                    }
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            DebugLog.e("RecOverlay", "Failed to show overlay: ${e.message}")
        }
    }

    private fun hideOverlay() {
        try {
            windowManager?.removeView(overlayView)
        } catch (_: Exception) {}
        overlayView = null
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startTime
                val sec = (elapsed / 1000) % 60
                val min = (elapsed / 60000) % 60
                timerText?.text = String.format("%02d:%02d", min, sec)
                stepCountText?.text = "${sessionManager?.sessionInfo?.value?.stepCount ?: 0} 步"
                delay(1000)
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "SmartTask 录制", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(this, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("SmartTask 录制中")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pending)
            .build()
    }
}
