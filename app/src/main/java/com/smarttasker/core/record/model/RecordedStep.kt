package com.smarttasker.core.record.model

data class RecordedStep(
    val id: String = java.util.UUID.randomUUID().toString().take(8),
    val order: Int,
    val type: RecordedStepType,
    val action: StepAction,
    val recordedAt: Long = System.currentTimeMillis(),
    val delayFromPreviousMs: Long = 0,
    val appContext: AppContextSnapshot? = null,
    val deviceContext: DeviceProfile? = null,
    val target: TargetSnapshot? = null,
    val beforeScreenshotRef: String? = null,
    val afterScreenshotRef: String? = null,
    val confidence: Float = 1.0f,
    val notes: String? = null
)

enum class RecordedStepType {
    TAP, LONG_PRESS, SWIPE, DRAG, SCROLL,
    KEY_EVENT, VOLUME_UP, VOLUME_DOWN, BACK, HOME, RECENTS,
    TEXT_INPUT, CLEAR_TEXT,
    WAIT, SCREENSHOT, ASSERT_SCREEN,
    APP_START, APP_SWITCH,
    UNKNOWN
}
