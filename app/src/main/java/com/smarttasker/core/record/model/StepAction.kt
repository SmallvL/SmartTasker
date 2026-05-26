package com.smarttasker.core.record.model

sealed class StepAction {
    data class Tap(val x: Int, val y: Int, val normalizedX: Float = 0f, val normalizedY: Float = 0f) : StepAction()
    data class LongPress(val x: Int, val y: Int, val durationMs: Long) : StepAction()
    data class Swipe(val startX: Int, val startY: Int, val endX: Int, val endY: Int, val durationMs: Long) : StepAction()
    data class Key(val keyCode: Int, val keyName: String, val longPress: Boolean = false) : StepAction()
    data class TextInput(val text: String, val sensitive: Boolean = false, val variableName: String? = null) : StepAction()
    data class Wait(val durationMs: Long, val reason: String? = null) : StepAction()
    data class Screenshot(val purpose: String = "manual") : StepAction()
    data class AppStart(val packageName: String) : StepAction()
}
