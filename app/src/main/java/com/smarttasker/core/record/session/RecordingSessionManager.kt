package com.smarttasker.core.record.session

import android.view.accessibility.AccessibilityEvent
import com.smarttasker.core.adb.AdbShellExecutor
import com.smarttasker.core.record.accessibility.AccessibilityEventBuffer
import com.smarttasker.core.record.adb.AdbStreamClient
import com.smarttasker.core.record.fusion.TargetResolver
import com.smarttasker.core.record.gesture.TouchGestureRecognizer
import com.smarttasker.core.record.model.*
import com.smarttasker.core.record.parser.RawInputParser
import com.smarttasker.core.record.RouteDraftStore
import com.smarttasker.service.SmartTaskerAccessibilityService
import com.smarttasker.util.DebugLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class RecordingSessionManager(
    private val context: android.content.Context,
    private val adbExecutor: AdbShellExecutor? = null
) {

    enum class SessionState { IDLE, RECORDING, PAUSED, POST_PROCESSING }

    /**
     * Input source mode for recording.
     * GETEVENT: Using getevent -lt (real devices with proper input subsystem)
     * ACCESSIBILITY: Using AccessibilityService events (emulators, devices without getevent touch data)
     */
    enum class InputMode { GETEVENT, ACCESSIBILITY }

    data class SessionInfo(
        val state: SessionState = SessionState.IDLE,
        val stepCount: Int = 0,
        val durationMs: Long = 0,
        val startTime: Long = 0,
        val inputMode: InputMode = InputMode.GETEVENT
    )

    private val adbStream = AdbStreamClient(adbExecutor)
    private val parser = RawInputParser()
    private val eventBuffer = AccessibilityEventBuffer()
    private val targetResolver = TargetResolver(eventBuffer)
    private val store = RouteDraftStore(context)

    private var gestureRecognizer: TouchGestureRecognizer? = null
    private var recordingJob: Job? = null
    private var accessibilityPollJob: Job? = null
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
    private var inputMode = InputMode.GETEVENT

    // Accessibility event tracking
    private var lastProcessedEventTime = 0L
    private var geteventEventCount = 0
    private var geteventTimeoutChecked = false

    // WINDOW_CONTENT_CHANGED burst detection
    private var lastContentChangeBurstTime = 0L
    private var contentChangeBurstCount = 0
    private val CONTENT_CHANGE_BURST_THRESHOLD = 5  // 5+ events in short time = user action
    private val CONTENT_CHANGE_BURST_WINDOW = 800L  // 800ms window
    private val MIN_STEP_INTERVAL = 1500L  // Minimum 1.5s between steps to avoid false positives

    // Throttle for content burst debug log
    private var lastContentBurstLogTime = 0L
    private val CONTENT_BURST_LOG_THROTTLE_MS = 3000L  // Max once per 3 seconds

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

        // Always verify with Android API to get correct orientation-aware size
        try {
            val windowManager = context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
            val displayMetrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(displayMetrics)
            val realWidth = displayMetrics.widthPixels
            val realHeight = displayMetrics.heightPixels
            // Use the real metrics which account for current rotation
            if (realWidth > 0 && realHeight > 0) {
                DebugLog.i("RecSession", "Real display metrics: ${realWidth}x${realHeight} (adb: ${screenSize.first}x${screenSize.second})")
                screenSize = Pair(realWidth, realHeight)
            }
            if (density == null || density == 1) density = displayMetrics.densityDpi
        } catch (e: Exception) {
            DebugLog.w("RecSession", "Failed to get real display metrics: ${e.message}")
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
        inputMode = InputMode.GETEVENT
        geteventEventCount = 0
        geteventTimeoutChecked = false
        lastProcessedEventTime = 0L

        _sessionInfo.value = SessionInfo(
            state = SessionState.RECORDING,
            stepCount = 0,
            durationMs = 0,
            startTime = sessionStartTime,
            inputMode = InputMode.GETEVENT
        )

        // Start getevent stream processing
        recordingJob = scope.launch {
            try {
                DebugLog.i("RecSession", "Starting getevent collection...")
                adbStream.streamGetevent().collect { line ->
                    if (!isPaused) {
                        // Only count lines that look like actual getevent events (start with '[')
                        // Device listing lines like "add device" are NOT real events
                        if (line.trim().startsWith("[")) {
                            geteventEventCount++
                        }
                        processEvent(line)
                    }
                }
                DebugLog.w("RecSession", "getevent collection ended normally, eventCount=$geteventEventCount")
                // If getevent stream ended with no real events, switch to accessibility mode immediately
                if (geteventEventCount == 0 && SmartTaskerAccessibilityService.isEnabled()) {
                    inputMode = InputMode.ACCESSIBILITY
                    _sessionInfo.value = _sessionInfo.value.copy(inputMode = InputMode.ACCESSIBILITY)
                    DebugLog.i("RecSession", "Switched to ACCESSIBILITY mode (getevent stream ended with no events)")
                }
            } catch (e: Exception) {
                DebugLog.e("RecSession", "Stream error: ${e.message}")
            }
        }

        // BUG2 fix: Start accessibility event polling as parallel input source.
        // If getevent produces no events after 3 seconds, switch to accessibility mode.
        startAccessibilityPolling()

        // Register content change burst callback for real-time detection
        SmartTaskerAccessibilityService.onContentChangeBurst = { timestamp ->
            handleContentChangeBurst(timestamp)
        }

        DebugLog.i("RecSession", "Recording started. Screen: ${screenSize.first}x${screenSize.second}, DPI: $density")
        return true
    }

    /**
     * Handle WINDOW_CONTENT_CHANGED burst detection.
     * On MuMu emulator, touch events only produce WINDOW_CONTENT_CHANGED events.
     * When 3+ events arrive within 500ms, we infer a user action occurred.
     */
    @Synchronized
    private fun handleContentChangeBurst(timestamp: Long) {
        if (_sessionInfo.value.state != SessionState.RECORDING || isPaused) return

        // Auto-switch to accessibility mode if service becomes available
        if (inputMode != InputMode.ACCESSIBILITY && SmartTaskerAccessibilityService.isEnabled()) {
            inputMode = InputMode.ACCESSIBILITY
            _sessionInfo.value = _sessionInfo.value.copy(inputMode = InputMode.ACCESSIBILITY)
            DebugLog.i("RecSession", "Switched to ACCESSIBILITY mode (service became available)")
        }

        if (inputMode != InputMode.ACCESSIBILITY) return

        val now = System.currentTimeMillis()
        if (now - lastContentChangeBurstTime > CONTENT_CHANGE_BURST_WINDOW) {
            // New burst window
            contentChangeBurstCount = 1
            lastContentChangeBurstTime = now
        } else {
            // Continue existing burst
            contentChangeBurstCount++
        }

        // Throttled content burst debug log
        val nowLog = System.currentTimeMillis()
        if (nowLog - lastContentBurstLogTime >= CONTENT_BURST_LOG_THROTTLE_MS) {
            lastContentBurstLogTime = nowLog
            DebugLog.d("RecSession", "Content burst: count=$contentChangeBurstCount, lastStep=$lastStepTime, lastProcessed=$lastProcessedEventTime")
        }

        if (contentChangeBurstCount >= CONTENT_CHANGE_BURST_THRESHOLD) {
            // Burst detected! This likely indicates a user action.
            // Only record once per burst, and ensure minimum interval between steps
            if (now - lastStepTime >= MIN_STEP_INTERVAL) {
                lastProcessedEventTime = now
                contentChangeBurstCount = 0  // Reset for next burst

                val dp = deviceProfile ?: return
                val delay = if (lastStepTime > 0) now - lastStepTime else 0

                // Try to find the most recent event with meaningful bounds from a11y buffer
                val a11yBuffer = SmartTaskerAccessibilityService.eventBuffer
                val recentEvent = a11yBuffer.findSmallestRecentBounds(now, toleranceMs = 1500)

                var centerX: Int
                var centerY: Int
                var conf: Float

                if (recentEvent != null &&
                    (recentEvent.boundsRight - recentEvent.boundsLeft > 0) &&
                    (recentEvent.boundsBottom - recentEvent.boundsTop > 0) &&
                    // Exclude fullscreen bounds (area > 50% of screen)
                    ((recentEvent.boundsRight - recentEvent.boundsLeft).toLong() *
                     (recentEvent.boundsBottom - recentEvent.boundsTop)) < (dp.screenWidth.toLong() * dp.screenHeight / 2)
                ) {
                    centerX = (recentEvent.boundsLeft + recentEvent.boundsRight) / 2
                    centerY = (recentEvent.boundsTop + recentEvent.boundsBottom) / 2
                    conf = 0.5f
                } else {
                    // Fallback: use screen center
                    centerX = dp.screenWidth / 2
                    centerY = dp.screenHeight / 2
                    conf = 0.3f
                }

                // Record step immediately with a11y-based coordinates
                val target = targetResolver.resolveTap(centerX, centerY, now, dp.screenWidth, dp.screenHeight)
                val stepIndex = stepOrder++
                val step = RecordedStep(
                    order = stepIndex,
                    type = RecordedStepType.TAP,
                    action = StepAction.Tap(centerX, centerY, dp.normalizeX(centerX), dp.normalizeY(centerY)),
                    recordedAt = now,
                    delayFromPreviousMs = delay,
                    appContext = appContext,
                    deviceContext = dp,
                    target = target,
                    confidence = conf,
                    notes = "Inferred from content change burst"
                )

                steps.add(step)
                lastStepTime = now
                updateSessionInfo()
                DebugLog.i("RecSession", "[A11y] Step $stepIndex: TAP (content burst) at ($centerX, $centerY)")

                // Auto-capture screenshot of the target app for this step
                captureStepScreenshot(stepIndex)

                // Sync: try to get better coordinates via uiautomator and UPDATE the step
                scope.launch {
                    val uiFocus = getFocusedElementBounds()
                    if (uiFocus != null) {
                        val betterX = (uiFocus.left + uiFocus.right) / 2
                        val betterY = (uiFocus.top + uiFocus.bottom) / 2
                        // Only update if uiautomator found a more specific element (smaller area)
                        val uiArea = (uiFocus.right - uiFocus.left).toLong() * (uiFocus.bottom - uiFocus.top)
                        val a11yArea = (centerX * 2L) * (centerY * 2L) // rough comparison
                        if (uiArea < dp.screenWidth.toLong() * dp.screenHeight / 2) {
                            DebugLog.i("RecSession", "uiautomator focus bounds: ($betterX, $betterY) area=$uiArea — updating step $stepIndex")
                            // Find and update the step in the list
                            val stepToUpdate = steps.find { it.order == stepIndex }
                            if (stepToUpdate != null) {
                                val updatedAction = StepAction.Tap(betterX, betterY, dp.normalizeX(betterX), dp.normalizeY(betterY))
                                val updatedTarget = targetResolver.resolveTap(betterX, betterY, now, dp.screenWidth, dp.screenHeight)
                                val idx = steps.indexOf(stepToUpdate)
                                steps[idx] = stepToUpdate.copy(
                                    action = updatedAction,
                                    target = updatedTarget,
                                    confidence = 0.8f,
                                    notes = "Content burst (uiautomator refined)"
                                )
                                DebugLog.i("RecSession", "Step $stepIndex coordinates updated: ($centerX,$centerY) -> ($betterX,$betterY)")
                            }
                        } else {
                            DebugLog.i("RecSession", "uiautomator focus bounds too large: ($betterX, $betterY) area=$uiArea — keeping a11y coords")
                        }
                    } else {
                        DebugLog.i("RecSession", "uiautomator focus: no focused element found")
                    }
                }
            }
        }
    }

    /**
     * BUG2 fix: Poll accessibility events as a fallback input source.
     * On emulators like MuMu, getevent cannot capture touch events because
     * the virtual input device doesn't go through the Linux input subsystem.
     * AccessibilityService events provide an alternative way to detect user interactions.
     */
    private fun startAccessibilityPolling() {
        accessibilityPollJob?.cancel()
        accessibilityPollJob = scope.launch {
            // Wait 3 seconds to check if getevent is producing events
            delay(3000)

            if (geteventEventCount == 0 && SmartTaskerAccessibilityService.isEnabled()) {
                // getevent produced no events, switch to accessibility mode
                inputMode = InputMode.ACCESSIBILITY
                _sessionInfo.value = _sessionInfo.value.copy(inputMode = InputMode.ACCESSIBILITY)
                DebugLog.i("RecSession", "Switched to ACCESSIBILITY input mode (getevent produced no events)")
            } else if (geteventEventCount == 0 && !SmartTaskerAccessibilityService.isEnabled()) {
                DebugLog.w("RecSession", "getevent produced no events and AccessibilityService is not enabled. " +
                    "Please enable SmartTasker accessibility service for better recording support.")
            }

            // Poll accessibility events
            while (isActive && _sessionInfo.value.state == SessionState.RECORDING) {
                if (!isPaused && inputMode == InputMode.ACCESSIBILITY) {
                    pollAccessibilityEvents()
                }
                delay(200) // Poll every 200ms
            }
        }
    }

    /**
     * Poll recent accessibility events from the shared buffer and convert to RecordedSteps.
     * Strategy: On emulators, VIEW_CLICKED events may not be fired. We infer taps from:
     * 1. VIEW_CLICKED / VIEW_LONG_CLICKED (preferred, real devices)
     * 2. VIEW_FOCUSED (fallback - user tapped on a focusable view)
     * 3. WINDOW_STATE_CHANGED (fallback - user navigated to new screen)
     * Click events take priority over scroll events.
     */
    private suspend fun pollAccessibilityEvents() {
        val a11yBuffer = SmartTaskerAccessibilityService.eventBuffer
        val dp = deviceProfile ?: return

        // Auto-switch to accessibility mode if service becomes available
        if (inputMode != InputMode.ACCESSIBILITY && SmartTaskerAccessibilityService.isEnabled()) {
            inputMode = InputMode.ACCESSIBILITY
            _sessionInfo.value = _sessionInfo.value.copy(inputMode = InputMode.ACCESSIBILITY)
            DebugLog.i("RecSession", "Switched to ACCESSIBILITY mode (service became available during poll)")
        }

        if (inputMode != InputMode.ACCESSIBILITY) return

        val now = System.currentTimeMillis()

        // Skip if a step was recorded very recently (avoid duplicates with burst detection)
        if (now - lastStepTime < MIN_STEP_INTERVAL) return

        // Strategy 1: Find explicit click events
        val clickEvent = a11yBuffer.findRecentClick(now, toleranceMs = 500)

        if (clickEvent != null && clickEvent.timestamp > lastProcessedEventTime) {
            lastProcessedEventTime = clickEvent.timestamp
            val centerX = (clickEvent.boundsLeft + clickEvent.boundsRight) / 2
            val centerY = (clickEvent.boundsTop + clickEvent.boundsBottom) / 2
            val delay = if (lastStepTime > 0) now - lastStepTime else 0
            val isLongClick = clickEvent.type == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
            val target = targetResolver.resolveTap(centerX, centerY, now, dp.screenWidth, dp.screenHeight)

            val step = if (isLongClick) {
                RecordedStep(order = stepOrder++, type = RecordedStepType.LONG_PRESS,
                    action = StepAction.LongPress(centerX, centerY, 500),
                    recordedAt = now, delayFromPreviousMs = delay,
                    appContext = appContext, deviceContext = dp, target = target,
                    confidence = target.confidence * 0.8f)
            } else {
                RecordedStep(order = stepOrder++, type = RecordedStepType.TAP,
                    action = StepAction.Tap(centerX, centerY, dp.normalizeX(centerX), dp.normalizeY(centerY)),
                    recordedAt = now, delayFromPreviousMs = delay,
                    appContext = appContext, deviceContext = dp, target = target,
                    confidence = target.confidence * 0.8f)
            }
            steps.add(step)
            lastStepTime = now
            updateSessionInfo()
            DebugLog.i("RecSession", "[A11y] Step ${step.order}: ${step.type} at ($centerX, $centerY) pkg=${clickEvent.packageName}")
            captureStepScreenshot(step.order)
            return
        }

        // Strategy 2: Find VIEW_FOCUSED events (fallback for emulators without VIEW_CLICKED)
        val focusEvent = a11yBuffer.findNearest(AccessibilityEvent.TYPE_VIEW_FOCUSED, now, toleranceMs = 500)
        if (focusEvent != null && focusEvent.timestamp > lastProcessedEventTime) {
            lastProcessedEventTime = focusEvent.timestamp
            var centerX = (focusEvent.boundsLeft + focusEvent.boundsRight) / 2
            var centerY = (focusEvent.boundsTop + focusEvent.boundsBottom) / 2
            val delay = if (lastStepTime > 0) now - lastStepTime else 0

            // Try uiautomator for more precise coordinates
            val uiFocus = getFocusedElementBoundsSync()
            if (uiFocus != null) {
                val uiX = (uiFocus.left + uiFocus.right) / 2
                val uiY = (uiFocus.top + uiFocus.bottom) / 2
                val uiArea = (uiFocus.right - uiFocus.left).toLong() * (uiFocus.bottom - uiFocus.top)
                if (uiArea < dp.screenWidth.toLong() * dp.screenHeight / 2 && uiArea > 0) {
                    DebugLog.i("RecSession", "VIEW_FOCUSED: uiautomator refined ($centerX,$centerY) -> ($uiX,$uiY)")
                    centerX = uiX
                    centerY = uiY
                }
            }

            val target = targetResolver.resolveTap(centerX, centerY, now, dp.screenWidth, dp.screenHeight)

            val step = RecordedStep(order = stepOrder++, type = RecordedStepType.TAP,
                action = StepAction.Tap(centerX, centerY, dp.normalizeX(centerX), dp.normalizeY(centerY)),
                recordedAt = now, delayFromPreviousMs = delay,
                appContext = appContext, deviceContext = dp, target = target,
                confidence = if (uiFocus != null) 0.8f else 0.6f,
                notes = if (uiFocus != null) "TAP (focus + uiautomator refined)" else "TAP (inferred from FOCUS)")
            steps.add(step)
            lastStepTime = now
            updateSessionInfo()
            DebugLog.i("RecSession", "[A11y] Step ${step.order}: TAP (inferred from FOCUS) at ($centerX, $centerY) pkg=${focusEvent.packageName}")
            captureStepScreenshot(step.order)
            return
        }

        // Strategy 3: Find WINDOW_STATE_CHANGED events (screen transitions)
        val windowEvent = a11yBuffer.findNearest(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, now, toleranceMs = 500)
        if (windowEvent != null && windowEvent.timestamp > lastProcessedEventTime) {
            lastProcessedEventTime = windowEvent.timestamp
            val delay = if (lastStepTime > 0) now - lastStepTime else 0
            val pkg = windowEvent.packageName ?: ""
            val cls = windowEvent.className ?: ""

            val step = RecordedStep(order = stepOrder++, type = RecordedStepType.TAP,
                action = StepAction.Tap(dp.screenWidth / 2, dp.screenHeight / 2,
                    dp.normalizeX(dp.screenWidth / 2), dp.normalizeY(dp.screenHeight / 2)),
                recordedAt = now, delayFromPreviousMs = delay,
                appContext = appContext, deviceContext = dp,
                confidence = 0.4f, // Low confidence - approximate
                notes = "Screen transition: $cls")
            steps.add(step)
            lastStepTime = now
            updateSessionInfo()
            DebugLog.i("RecSession", "[A11y] Step ${step.order}: TAP (inferred from WINDOW_STATE) pkg=$pkg cls=$cls")
            captureStepScreenshot(step.order)
            return
        }

        // Strategy 4: Find text change events (user typed text)
        val textEvent = a11yBuffer.findNearestTextChange(now, toleranceMs = 500)
        if (textEvent != null && textEvent.timestamp > lastProcessedEventTime) {
            lastProcessedEventTime = textEvent.timestamp
            val delay = if (lastStepTime > 0) now - lastStepTime else 0
            val text = textEvent.text ?: ""

            val step = RecordedStep(order = stepOrder++, type = RecordedStepType.TEXT_INPUT,
                action = StepAction.TextInput(text),
                recordedAt = now, delayFromPreviousMs = delay,
                appContext = appContext, deviceContext = dp, confidence = 0.8f,
                notes = "Text input: '${text.take(30)}'")
            steps.add(step)
            lastStepTime = now
            updateSessionInfo()
            DebugLog.i("RecSession", "[A11y] Step ${step.order}: TEXT_INPUT '${text.take(20)}' pkg=${textEvent.packageName}")
            captureStepScreenshot(step.order)
            return
        }

        // Strategy 5: Find scroll events (only TYPE_VIEW_SCROLLED)
        val scrollEvent = a11yBuffer.findNearestScroll(now, toleranceMs = 500)
        if (scrollEvent != null && scrollEvent.timestamp > lastProcessedEventTime) {
            lastProcessedEventTime = scrollEvent.timestamp
            val delay = if (lastStepTime > 0) now - lastStepTime else 0
            val centerX = dp.screenWidth / 2
            val centerY = dp.screenHeight / 2
            val swipeDistance = dp.screenHeight / 3

            val step = RecordedStep(order = stepOrder++, type = RecordedStepType.SWIPE,
                action = StepAction.Swipe(centerX, centerY + swipeDistance / 2, centerX, centerY - swipeDistance / 2, 300),
                recordedAt = now, delayFromPreviousMs = delay,
                appContext = appContext, deviceContext = dp, confidence = 0.5f)
            steps.add(step)
            lastStepTime = now
            updateSessionInfo()
            DebugLog.i("RecSession", "[A11y] Step ${step.order}: SWIPE (approximate)")
        }
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
        accessibilityPollJob?.cancel()
        accessibilityPollJob = null
        SmartTaskerAccessibilityService.onContentChangeBurst = null
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
            DebugLog.i("RecSession", "Route saved: ${draft.routeId}, ${steps.size} steps, mode=$inputMode")
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

        // Capture screenshot and save to file
        val screenshotRef = captureScreenshot("manual_${stepOrder}")

        val step = RecordedStep(
            order = stepOrder++,
            type = RecordedStepType.SCREENSHOT,
            action = StepAction.Screenshot("manual"),
            recordedAt = now,
            delayFromPreviousMs = if (lastStepTime > 0) now - lastStepTime else 0,
            deviceContext = deviceProfile,
            beforeScreenshotRef = screenshotRef,
            confidence = 1.0f
        )
        steps.add(step)
        lastStepTime = now
        updateSessionInfo()
        DebugLog.i("RecSession", "Inserted screenshot, ref=$screenshotRef")
    }

    fun getEventBuffer(): AccessibilityEventBuffer = eventBuffer

    private fun processEvent(line: String) {
        val rawEvent = parser.parseLine(line)
        if (rawEvent == null) {
            // Only log first few nulls to avoid spam
            return
        }
        
        // Removed per-event DebugLog.d to avoid log spam; gesture recognition still logged below
        
        val gesture = gestureRecognizer?.feed(rawEvent)
        if (gesture == null) {
            // Event consumed, waiting for more
            return
        }

        DebugLog.i("RecSession", "Gesture recognized: $gesture")
        val step = createStepFromGesture(gesture) ?: return

        steps.add(step)
        lastStepTime = System.currentTimeMillis()
        updateSessionInfo()
        DebugLog.d("RecSession", "Step ${step.order}: ${step.type} ${formatAction(step.action)}")

        // Auto-capture screenshot of the target app for this step
        captureStepScreenshot(step.order)
    }

    private fun createStepFromGesture(gesture: TouchGestureRecognizer.RecognizedGesture): RecordedStep? {
        val now = System.currentTimeMillis()
        val delay = if (lastStepTime > 0) now - lastStepTime else 0
        val dp = deviceProfile ?: return null

        return when (gesture) {
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
    }

    private data class ElementBounds(val left: Int, val right: Int, val top: Int, val bottom: Int)

    /**
     * Get the bounds of the currently focused element via ADB uiautomator.
     * Returns ElementBounds or null on failure.
     * Used as fallback when accessibility events don't provide accurate bounds.
     */
    private suspend fun getFocusedElementBounds(): ElementBounds? {
        return getFocusedElementBoundsInternal()
    }

    /**
     * Synchronous version for use within coroutines (like pollAccessibilityEvents).
     * Same implementation as getFocusedElementBounds but named differently for clarity.
     */
    private suspend fun getFocusedElementBoundsSync(): ElementBounds? {
        return getFocusedElementBoundsInternal()
    }

    private suspend fun getFocusedElementBoundsInternal(): ElementBounds? {
        return try {
            withContext(Dispatchers.IO) {
                val output = adbStream.execOutput("uiautomator dump /dev/tty 2>/dev/null")
                if (output != null && output.contains("bounds=\"[")) {
                    // Find focused element: look for focused="true" and extract bounds
                    val focusedRegex = Regex("""focused="true"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"""")
                    val match = focusedRegex.find(output)
                    if (match != null) {
                        ElementBounds(
                            match.groupValues[1].toInt(),
                            match.groupValues[3].toInt(),
                            match.groupValues[2].toInt(),
                            match.groupValues[4].toInt()
                        )
                    } else {
                        // No focused element, find the smallest non-fullscreen element
                        val boundsRegex = Regex("""bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"""")
                        val allBounds = boundsRegex.findAll(output).map { m ->
                            val l = m.groupValues[1].toInt()
                            val t = m.groupValues[2].toInt()
                            val r = m.groupValues[3].toInt()
                            val b = m.groupValues[4].toInt()
                            ElementBounds(l, r, t, b) to ((r - l).toLong() * (b - t))
                        }.filter { it.second > 0 && it.second < 200000 }
                            .sortedBy { it.second }.toList()

                        if (allBounds.isNotEmpty()) allBounds.first().first else null
                    }
                } else null
            }
        } catch (e: Exception) {
            DebugLog.d("RecSession", "uiautomator dump failed: ${e.message}")
            null
        }
    }

    /**
     * Capture a screenshot and save to internal storage.
     * Returns the file path, or null on failure.
     */
    private suspend fun captureScreenshot(tag: String): String? {
        return try {
            val bytes = adbStream.screenshot()
            if (bytes != null && bytes.isNotEmpty()) {
                val dir = java.io.File(context.filesDir, "recording_screenshots").apply { mkdirs() }
                val file = java.io.File(dir, "step_${tag}_${System.currentTimeMillis()}.png")
                file.writeBytes(bytes)
                DebugLog.d("RecSession", "Screenshot saved: ${file.absolutePath} (${bytes.size} bytes)")
                file.absolutePath
            } else {
                // Fallback: try screencap command directly
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "screencap -p /dev/tty"))
                val fallbackBytes = process.inputStream.readBytes()
                if (fallbackBytes.isNotEmpty()) {
                    val dir = java.io.File(context.filesDir, "recording_screenshots").apply { mkdirs() }
                    val file = java.io.File(dir, "step_${tag}_${System.currentTimeMillis()}.png")
                    file.writeBytes(fallbackBytes)
                    file.absolutePath
                } else null
            }
        } catch (e: Exception) {
            DebugLog.e("RecSession", "Screenshot capture error: ${e.message}")
            null
        }
    }

    private fun updateSessionInfo() {
        _sessionInfo.value = _sessionInfo.value.copy(
            stepCount = steps.size,
            durationMs = System.currentTimeMillis() - sessionStartTime
        )
    }

    /**
     * Auto-capture a screenshot for a recorded step.
     * This captures the current screen (which should be the target app during recording)
     * and saves the path to the step's beforeScreenshotRef.
     */
    private fun captureStepScreenshot(stepIndex: Int) {
        scope.launch {
            val screenshotRef = captureScreenshot("step_${stepIndex}")
            if (screenshotRef != null) {
                val stepToUpdate = steps.find { it.order == stepIndex }
                if (stepToUpdate != null) {
                    val idx = steps.indexOf(stepToUpdate)
                    steps[idx] = stepToUpdate.copy(beforeScreenshotRef = screenshotRef)
                    DebugLog.d("RecSession", "Screenshot captured for step $stepIndex: $screenshotRef")
                }
            }
        }
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
