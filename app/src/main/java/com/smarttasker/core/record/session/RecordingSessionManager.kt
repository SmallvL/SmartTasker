package com.smarttasker.core.record.session

import android.content.Context
import com.smarttasker.core.adb.AdbShellExecutor
import com.smarttasker.core.record.accessibility.AccessibilityEventBuffer
import com.smarttasker.core.record.adb.AdbStreamClient
import com.smarttasker.core.record.fusion.TargetResolver
import com.smarttasker.core.record.gesture.TouchGestureRecognizer
import com.smarttasker.core.record.model.*
import com.smarttasker.core.record.parser.RawInputParser
import com.smarttasker.core.record.RouteDraftStore
import com.smarttasker.util.DebugLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class RecordingSessionManager(
    private val context: Context,
    private val adbExecutor: AdbShellExecutor? = null
) {

    enum class SessionState { IDLE, RECORDING, PAUSED, POST_PROCESSING }

    data class SessionInfo(
        val state: SessionState = SessionState.IDLE,
        val stepCount: Int = 0,
        val durationMs: Long = 0,
        val startTime: Long = 0
    )

    private val adbStream = AdbStreamClient(adbExecutor)
    private val parser = RawInputParser()
    private val eventBuffer = AccessibilityEventBuffer()
    private val targetResolver = TargetResolver(eventBuffer)
    private val store = RouteDraftStore(context)

    private var gestureRecognizer: TouchGestureRecognizer? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _sessionInfo = MutableStateFlow(SessionInfo())
    val sessionInfo: StateFlow<SessionInfo> = _sessionInfo.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val steps = mutableListOf<RecordedStep>()
    private var stepOrder = 0
    private var sessionStartTime = 0L
    private var lastStepTime = 0L
    private var deviceProfile: DeviceProfile? = null
    private var appContext: AppContextSnapshot? = null
    private var isPaused = false

    /**
     * Start recording. Returns true if started successfully.
     */
    suspend fun startRecording(): Boolean {
        if (_sessionInfo.value.state != SessionState.IDLE) {
            DebugLog.e("RecSession", "Already recording")
            return false
        }

        // Check if we have enough permissions for getevent
        if (!com.smarttasker.core.direct.ShellExecutor.canRecord()) {
            val desc = com.smarttasker.core.direct.ShellExecutor.getCapabilityDescription()
            DebugLog.e("RecSession", "Cannot record: $desc")
            _errorMessage.value = "当前模式不支持录制：$desc\n请开启无线调试并连接后重试"
            return false
        }

        // Collect device info — try shell first, fall back to Android API
        var screenSize = adbStream.getScreenSize()
        var density = adbStream.getDensity()

        if (screenSize == null) {
            // Fallback: use Android DisplayMetrics (works in any mode)
            val dm = context.resources.displayMetrics
            screenSize = Pair(dm.widthPixels, dm.heightPixels)
            if (density == null) density = dm.densityDpi
            DebugLog.i("RecSession", "Using Android API for screen info: ${screenSize.first}x${screenSize.second}")
        }

        val rawRange = adbStream.getRawInputRange()
        val currentApp = adbStream.getCurrentApp()

        if (screenSize == null) {
            DebugLog.e("RecSession", "Cannot get screen size")
            return false
        }

        deviceProfile = DeviceProfile(
            screenWidth = screenSize.first,
            screenHeight = screenSize.second,
            densityDpi = density ?: 440,
            androidVersion = android.os.Build.VERSION.SDK_INT,
            manufacturer = android.os.Build.MANUFACTURER,
            model = android.os.Build.MODEL
        )
        appContext = currentApp

        gestureRecognizer = TouchGestureRecognizer(density ?: 440)
        steps.clear()
        stepOrder = 0
        sessionStartTime = System.currentTimeMillis()
        lastStepTime = sessionStartTime
        isPaused = false

        _sessionInfo.value = SessionInfo(
            state = SessionState.RECORDING,
            stepCount = 0,
            durationMs = 0,
            startTime = sessionStartTime
        )

        // Start getevent stream processing
        recordingJob = scope.launch {
            try {
                DebugLog.i("RecSession", "Starting getevent collection...")
                adbStream.streamGetevent().collect { line ->
                    if (!isPaused) {
                        processEvent(line)
                    }
                }
                DebugLog.w("RecSession", "getevent collection ended normally")
            } catch (e: Exception) {
                DebugLog.e("RecSession", "Stream error: ${e.message}")
            }
        }

        DebugLog.i("RecSession", "Recording started. Screen: ${screenSize.first}x${screenSize.second}, DPI: $density")
        return true
    }

    /**
     * Pause recording. Steps are not recorded while paused.
     */
    fun pauseRecording() {
        if (_sessionInfo.value.state != SessionState.RECORDING) return
        isPaused = true
        _sessionInfo.value = _sessionInfo.value.copy(state = SessionState.PAUSED)
        DebugLog.i("RecSession", "Recording paused")
    }

    /**
     * Resume recording after pause.
     */
    fun resumeRecording() {
        if (_sessionInfo.value.state != SessionState.PAUSED) return
        isPaused = false
        _sessionInfo.value = _sessionInfo.value.copy(state = SessionState.RECORDING)
        DebugLog.i("RecSession", "Recording resumed")
    }

    /**
     * Stop recording and return the route draft.
     */
    suspend fun stopRecording(): RouteDraft? {
        if (_sessionInfo.value.state == SessionState.IDLE) return null

        _sessionInfo.value = _sessionInfo.value.copy(state = SessionState.POST_PROCESSING)
        recordingJob?.cancel()
        recordingJob = null
        parser.reset()
        gestureRecognizer?.reset()

        val draft = RouteDraft(
            name = "录制 ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(sessionStartTime))}",
            source = RouteSource.MANUAL_RECORDING,
            status = RouteStatus.DRAFT,
            deviceProfile = deviceProfile,
            appScope = appContext,
            steps = steps.toList(),
            createdAt = sessionStartTime,
            updatedAt = System.currentTimeMillis()
        )

        // Auto-save
        try {
            store.save(draft)
            DebugLog.i("RecSession", "Route saved: ${draft.routeId}, ${steps.size} steps")
        } catch (e: Exception) {
            DebugLog.e("RecSession", "Save error: ${e.message}")
        }

        steps.clear()
        stepOrder = 0
        _sessionInfo.value = SessionInfo()
        return draft
    }

    /**
     * Add a wait step manually.
     */
    fun insertWait(durationMs: Long = 3000) {
        if (_sessionInfo.value.state != SessionState.RECORDING && _sessionInfo.value.state != SessionState.PAUSED) return
        val now = System.currentTimeMillis()
        val step = RecordedStep(
            order = stepOrder++,
            type = RecordedStepType.WAIT,
            action = StepAction.Wait(durationMs, "手动插入"),
            recordedAt = now,
            delayFromPreviousMs = if (lastStepTime > 0) now - lastStepTime else 0,
            deviceContext = deviceProfile,
            confidence = 1.0f
        )
        steps.add(step)
        lastStepTime = now
        updateSessionInfo()
        DebugLog.i("RecSession", "Inserted wait: ${durationMs}ms")
    }

    /**
     * Take a screenshot and add as a step.
     */
    suspend fun insertScreenshot() {
        if (_sessionInfo.value.state != SessionState.RECORDING && _sessionInfo.value.state != SessionState.PAUSED) return
        val now = System.currentTimeMillis()
        val step = RecordedStep(
            order = stepOrder++,
            type = RecordedStepType.SCREENSHOT,
            action = StepAction.Screenshot("manual"),
            recordedAt = now,
            delayFromPreviousMs = if (lastStepTime > 0) now - lastStepTime else 0,
            deviceContext = deviceProfile,
            confidence = 1.0f
        )
        steps.add(step)
        lastStepTime = now
        updateSessionInfo()
        DebugLog.i("RecSession", "Inserted screenshot")
    }

    fun getEventBuffer(): AccessibilityEventBuffer = eventBuffer

    private fun processEvent(line: String) {
        val rawEvent = parser.parseLine(line)
        if (rawEvent == null) {
            // Only log first few nulls to avoid spam
            return
        }
        
        DebugLog.d("RecSession", "RawEvent: $rawEvent")
        
        val gesture = gestureRecognizer?.feed(rawEvent)
        if (gesture == null) {
            // Event consumed, waiting for more
            return
        }

        DebugLog.i("RecSession", "Gesture recognized: $gesture")

        val now = System.currentTimeMillis()
        val delay = if (lastStepTime > 0) now - lastStepTime else 0
        val dp = deviceProfile ?: return

        val step = when (gesture) {
            is TouchGestureRecognizer.RecognizedGesture.Tap -> {
                val target = targetResolver.resolveTap(gesture.x, gesture.y, gesture.timestamp, dp.screenWidth, dp.screenHeight)
                RecordedStep(
                    order = stepOrder++,
                    type = RecordedStepType.TAP,
                    action = StepAction.Tap(gesture.x, gesture.y, dp.normalizeX(gesture.x), dp.normalizeY(gesture.y)),
                    recordedAt = gesture.timestamp,
                    delayFromPreviousMs = delay,
                    appContext = appContext,
                    deviceContext = dp,
                    target = target,
                    confidence = target.confidence
                )
            }
            is TouchGestureRecognizer.RecognizedGesture.LongPress -> {
                val target = targetResolver.resolveTap(gesture.x, gesture.y, gesture.timestamp, dp.screenWidth, dp.screenHeight)
                RecordedStep(
                    order = stepOrder++,
                    type = RecordedStepType.LONG_PRESS,
                    action = StepAction.LongPress(gesture.x, gesture.y, gesture.durationMs),
                    recordedAt = gesture.timestamp,
                    delayFromPreviousMs = delay,
                    appContext = appContext,
                    deviceContext = dp,
                    target = target,
                    confidence = target.confidence
                )
            }
            is TouchGestureRecognizer.RecognizedGesture.Swipe -> {
                RecordedStep(
                    order = stepOrder++,
                    type = RecordedStepType.SWIPE,
                    action = StepAction.Swipe(gesture.startX, gesture.startY, gesture.endX, gesture.endY, gesture.durationMs),
                    recordedAt = gesture.timestamp,
                    delayFromPreviousMs = delay,
                    appContext = appContext,
                    deviceContext = dp,
                    confidence = 0.8f
                )
            }
            is TouchGestureRecognizer.RecognizedGesture.KeyPress -> {
                val stepType = when (gesture.keyName) {
                    "BACK" -> RecordedStepType.BACK
                    "HOME" -> RecordedStepType.HOME
                    "VOLUME_UP" -> RecordedStepType.VOLUME_UP
                    "VOLUME_DOWN" -> RecordedStepType.VOLUME_DOWN
                    "APP_SWITCH" -> RecordedStepType.RECENTS
                    else -> RecordedStepType.KEY_EVENT
                }
                RecordedStep(
                    order = stepOrder++,
                    type = stepType,
                    action = StepAction.Key(gesture.keyCode, gesture.keyName),
                    recordedAt = gesture.timestamp,
                    delayFromPreviousMs = delay,
                    deviceContext = dp,
                    confidence = 1.0f
                )
            }
            is TouchGestureRecognizer.RecognizedGesture.KeyLongPress -> {
                RecordedStep(
                    order = stepOrder++,
                    type = RecordedStepType.KEY_EVENT,
                    action = StepAction.Key(gesture.keyCode, gesture.keyName, longPress = true),
                    recordedAt = gesture.timestamp,
                    delayFromPreviousMs = delay,
                    deviceContext = dp,
                    confidence = 1.0f
                )
            }
        }

        steps.add(step)
        lastStepTime = now
        updateSessionInfo()
        DebugLog.d("RecSession", "Step ${step.order}: ${step.type} ${formatAction(step.action)}")
    }

    private fun updateSessionInfo() {
        _sessionInfo.value = _sessionInfo.value.copy(
            stepCount = steps.size,
            durationMs = System.currentTimeMillis() - sessionStartTime
        )
    }

    private fun formatAction(action: StepAction): String = when (action) {
        is StepAction.Tap -> "(${action.x}, ${action.y})"
        is StepAction.LongPress -> "(${action.x}, ${action.y}) ${action.durationMs}ms"
        is StepAction.Swipe -> "(${action.startX},${action.startY})→(${action.endX},${action.endY})"
        is StepAction.Key -> action.keyName
        is StepAction.TextInput -> "'${action.text.take(10)}'"
        is StepAction.Wait -> "${action.durationMs}ms"
        is StepAction.Screenshot -> "screenshot"
        is StepAction.AppStart -> action.packageName
    }
}
