package com.smarttasker.core.record.gesture

import com.smarttasker.core.record.parser.RawInputParser.RawInputEvent
import kotlin.math.abs
import kotlin.math.sqrt

class TouchGestureRecognizer(private val screenDpi: Int = 440) {

    sealed class RecognizedGesture {
        data class Tap(val x: Int, val y: Int, val timestamp: Long) : RecognizedGesture()
        data class LongPress(val x: Int, val y: Int, val durationMs: Long, val timestamp: Long) : RecognizedGesture()
        data class Swipe(val startX: Int, val startY: Int, val endX: Int, val endY: Int, val durationMs: Long, val timestamp: Long) : RecognizedGesture()
        data class KeyPress(val keyCode: Int, val keyName: String, val timestamp: Long) : RecognizedGesture()
        data class KeyLongPress(val keyCode: Int, val keyName: String, val durationMs: Long, val timestamp: Long) : RecognizedGesture()
    }

    // Touch tracking state
    private var touchStartTime: Long = 0
    private var touchStartX: Int = 0
    private var touchStartY: Int = 0
    private var touchCurrentX: Int = 0
    private var touchCurrentY: Int = 0
    private var isTouching: Boolean = false

    // Key tracking state
    private var keyDownTime: Long = 0
    private var keyDownCode: Int = 0
    private var keyDownName: String = ""
    private var isKeyDown: Boolean = false

    // Thresholds in pixels (based on dpi)
    private val tapThresholdPx: Int get() = (16 * screenDpi / 160f).toInt() // 16dp
    private val swipeMinPx: Int get() = (32 * screenDpi / 160f).toInt() // 32dp

    /**
     * Feed a raw event and return a recognized gesture if complete.
     * Returns null if the event is part of an ongoing gesture.
     */
    fun feed(event: RawInputEvent): RecognizedGesture? {
        return when (event) {
            is RawInputEvent.TouchDown -> {
                if (!isTouching) {
                    isTouching = true
                    touchStartTime = event.timestamp
                    touchStartX = event.x
                    touchStartY = event.y
                    touchCurrentX = event.x
                    touchCurrentY = event.y
                } else {
                    touchCurrentX = event.x
                    touchCurrentY = event.y
                }
                null
            }
            is RawInputEvent.TouchMove -> {
                touchCurrentX = event.x
                touchCurrentY = event.y
                null
            }
            is RawInputEvent.TouchUp -> {
                if (!isTouching) return null
                isTouching = false
                val duration = event.timestamp - touchStartTime
                val distance = distance(touchStartX, touchStartY, touchCurrentX, touchCurrentY)

                when {
                    distance < tapThresholdPx && duration < 200 -> {
                        // Short press, small movement = Tap
                        RecognizedGesture.Tap(touchStartX, touchStartY, touchStartTime)
                    }
                    distance < tapThresholdPx && duration >= 200 -> {
                        // Long press (>= 200ms), small movement = LongPress
                        RecognizedGesture.LongPress(touchStartX, touchStartY, duration, touchStartTime)
                    }
                    distance >= swipeMinPx -> {
                        // Large movement = Swipe
                        RecognizedGesture.Swipe(touchStartX, touchStartY, touchCurrentX, touchCurrentY, duration, touchStartTime)
                    }
                    else -> {
                        // Small movement, short duration = Tap
                        RecognizedGesture.Tap(touchStartX, touchStartY, touchStartTime)
                    }
                }
            }
            is RawInputEvent.KeyDown -> {
                if (!isKeyDown) {
                    isKeyDown = true
                    keyDownTime = event.timestamp
                    keyDownCode = event.keyCode
                    keyDownName = event.keyName
                }
                null
            }
            is RawInputEvent.KeyUp -> {
                if (!isKeyDown) return null
                isKeyDown = false
                val duration = event.timestamp - keyDownTime
                if (duration >= 500) {
                    RecognizedGesture.KeyLongPress(keyDownCode, keyDownName, duration, keyDownTime)
                } else {
                    RecognizedGesture.KeyPress(keyDownCode, keyDownName, keyDownTime)
                }
            }
            else -> null
        }
    }

    fun reset() {
        isTouching = false; isKeyDown = false
    }

    private fun distance(x1: Int, y1: Int, x2: Int, y2: Int): Float {
        val dx = (x2 - x1).toFloat()
        val dy = (y2 - y1).toFloat()
        return sqrt(dx * dx + dy * dy)
    }
}
