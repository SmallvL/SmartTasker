package com.smarttasker.core.record.parser

/**
 * Parses raw getevent -lt output into structured RawInputEvent objects.
 * Handles EV_ABS (touch coordinates), EV_KEY (buttons/keys), EV_SYN (frame sync).
 * 
 * IMPORTANT: Touch events are emitted on SYN_REPORT, not on ABS_MT_TRACKING_ID,
 * because coordinates arrive AFTER tracking ID in the event stream.
 */
class RawInputParser {

    sealed class RawInputEvent {
        data class TouchDown(val trackingId: Int, val x: Int, val y: Int, val pressure: Int, val timestamp: Long) : RawInputEvent()
        data class TouchMove(val x: Int, val y: Int, val timestamp: Long) : RawInputEvent()
        data class TouchUp(val timestamp: Long) : RawInputEvent()
        data class KeyDown(val keyCode: Int, val keyName: String, val timestamp: Long) : RawInputEvent()
        data class KeyUp(val keyCode: Int, val keyName: String, val timestamp: Long) : RawInputEvent()
        data class SynReport(val timestamp: Long) : RawInputEvent()
        data class Unknown(val line: String, val timestamp: Long) : RawInputEvent()
    }

    // State tracking for multi-line touch events
    private var currentX: Int = 0
    private var currentY: Int = 0
    private var currentPressure: Int = 0
    private var currentTrackingId: Int = -1
    private var isTouching: Boolean = false
    private var touchJustStarted: Boolean = false  // True between TRACKING_ID and first SYN_REPORT

    // Key state
    private var pendingKeyDown: RawInputEvent.KeyDown? = null

    /**
     * Parse a single getevent -lt line.
     * Returns null if the line is not a valid event.
     */
    fun parseLine(line: String): RawInputEvent? {
        // Format: [  12345.678901] /dev/input/eventN: EV_TYPE     CODE     VALUE
        val trimmed = line.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("[")) return null

        val timestamp = extractTimestamp(trimmed) ?: return null
        val parts = trimmed.substringAfter(":").trim().split(Regex("\\s+"))
        if (parts.size < 3) return null

        val evType = parts[0]
        val code = parts[1]
        val value = parts[2]

        return when (evType) {
            "EV_ABS" -> parseAbs(code, value, timestamp)
            "EV_KEY" -> parseKey(code, value, timestamp)
            "EV_SYN" -> parseSyn(code, timestamp)
            else -> null
        }
    }

    private fun extractTimestamp(line: String): Long? {
        val match = Regex("\\[\\s*(\\d+\\.\\d+)\\]").find(line) ?: return null
        val seconds = match.groupValues[1].toDouble()
        return (seconds * 1000).toLong()
    }

    private fun parseAbs(code: String, value: String, timestamp: Long): RawInputEvent? {
        val intValue = value.toLongOrNull(16)?.toInt() ?: return null
        when (code) {
            "ABS_MT_POSITION_X" -> currentX = intValue
            "ABS_MT_POSITION_Y" -> currentY = intValue
            "ABS_MT_PRESSURE" -> currentPressure = intValue
            "ABS_MT_TRACKING_ID" -> {
                currentTrackingId = intValue
                if (intValue >= 0) {
                    // Touch started - but don't emit yet, wait for SYN_REPORT
                    // after coordinates are updated
                    isTouching = true
                    touchJustStarted = true
                } else {
                    // tracking_id = -1 means touch up
                    isTouching = false
                    touchJustStarted = false
                    return RawInputEvent.TouchUp(timestamp)
                }
            }
        }
        // Position updates are accumulated, reported on SYN_REPORT
        return null
    }

    private fun parseKey(code: String, value: String, timestamp: Long): RawInputEvent? {
        val isDown = value == "DOWN" || value == "00000001"
        val isUp = value == "UP" || value == "00000000"

        val keyCode = when (code) {
            "BTN_TOUCH" -> return null // Touch events handled by EV_ABS
            "KEY_BACK", "0000009e" -> 158
            "KEY_HOME", "00000066" -> 102
            "KEY_VOLUMEUP", "00000073" -> 115
            "KEY_VOLUMEDOWN", "00000072" -> 114
            "KEY_POWER", "00000074" -> 116
            "KEY_APP_SWITCH", "00000061" -> 97
            else -> code.removePrefix("000000").toIntOrNull(16) ?: code.removePrefix("KEY_").hashCode()
        }

        val keyName = when (keyCode) {
            158 -> "BACK"
            102 -> "HOME"
            115 -> "VOLUME_UP"
            114 -> "VOLUME_DOWN"
            116 -> "POWER"
            97 -> "APP_SWITCH"
            else -> "KEY_$keyCode"
        }

        return if (isDown) {
            RawInputEvent.KeyDown(keyCode, keyName, timestamp)
        } else if (isUp) {
            RawInputEvent.KeyUp(keyCode, keyName, timestamp)
        } else null
    }

    private fun parseSyn(code: String, timestamp: Long): RawInputEvent? {
        return when (code) {
            "SYN_REPORT" -> {
                if (isTouching) {
                    if (touchJustStarted) {
                        // First SYN_REPORT after touch start = TouchDown with correct coordinates
                        touchJustStarted = false
                        RawInputEvent.TouchDown(currentTrackingId, currentX, currentY, currentPressure, timestamp)
                    } else {
                        // Subsequent SYN_REPORTs while touching = TouchMove
                        RawInputEvent.TouchMove(currentX, currentY, timestamp)
                    }
                } else {
                    RawInputEvent.SynReport(timestamp)
                }
            }
            else -> null
        }
    }

    fun reset() {
        currentX = 0; currentY = 0; currentPressure = 0
        currentTrackingId = -1; isTouching = false
        touchJustStarted = false
        pendingKeyDown = null
    }
}
