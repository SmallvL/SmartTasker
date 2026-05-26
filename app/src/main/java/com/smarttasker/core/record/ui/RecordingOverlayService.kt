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

        private const val CHANNEL_ID = "smarttasker_recording"
        private const val NOTIFICATION_ID = 1004
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var sessionManager: RecordingSessionManager? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null
    private var startTime = 0L

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
            ACTION_START -> startRecording()
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
            stopSelf()
            return
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
        scope.launch {
            val ok = sessionManager!!.startRecording()
            if (ok) {
                startTime = System.currentTimeMillis()
                showOverlay()
                startTimer()
                DebugLog.i("RecOverlay", "Recording started via overlay")
            } else {
                val error = sessionManager?.errorMessage?.value ?: "Unknown error"
                DebugLog.e("RecOverlay", "Failed to start recording: $error")
                android.widget.Toast.makeText(applicationContext, error, android.widget.Toast.LENGTH_LONG).show()
                stopSelf()
            }
        }
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

        // Make draggable
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        overlayView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(overlayView, params)
                    true
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
