package com.smarttasker.core.direct

/**
 * Input engine - performs touch/input operations via shell.
 * Uses root shell (su) for input commands.
 *
 * Android input commands:
 *   input tap <x> <y>
 *   input swipe <x1> <y1> <x2> <y2> [duration_ms]
 *   input text <string>
 *   input keyevent <code>
 */
class InputEngine {

    /**
     * Tap at screen coordinates.
     */
    suspend fun tap(x: Int, y: Int): Boolean {
        val result = ShellExecutor.exec("input tap $x $y")
        return result is ShellResult.Success
    }

    /**
     * Swipe from (x1,y1) to (x2,y2) over duration_ms.
     */
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int = 300): Boolean {
        val result = ShellExecutor.exec("input swipe $x1 $y1 $x2 $y2 $durationMs")
        return result is ShellResult.Success
    }

    /**
     * Long press at coordinates.
     */
    suspend fun longPress(x: Int, y: Int, durationMs: Int = 1000): Boolean {
        // Long press is a slow swipe to same point
        val result = ShellExecutor.exec("input swipe $x $y $x $y $durationMs")
        return result is ShellResult.Success
    }

    /**
     * Input text. Splits on spaces and sends each word with KEYCODE_SPACE between.
     * This avoids the %s format specifier issue with Android's `input text`.
     */
    suspend fun inputText(text: String): Boolean {
        if (text.isBlank()) return true
        val words = text.split(" ")
        for ((i, word) in words.withIndex()) {
            if (word.isNotEmpty()) {
                val result = ShellExecutor.exec("input text ${word}")
                if (result is ShellResult.Error) return false
            }
            if (i < words.size - 1) {
                pressKey(62) // KEYCODE_SPACE
            }
        }
        return true
    }

    /**
     * Press a key by Android keycode.
     * Common codes: 3=HOME, 4=BACK, 26=POWER, 82=MENU, 187=APP_SWITCH
     */
    suspend fun pressKey(keyCode: Int): Boolean {
        val result = ShellExecutor.exec("input keyevent $keyCode")
        return result is ShellResult.Success
    }

    /**
     * Press Back button.
     */
    suspend fun pressBack(): Boolean = pressKey(4)

    /**
     * Press Home button.
     */
    suspend fun pressHome(): Boolean = pressKey(3)

    /**
     * Press Recent Apps button.
     */
    suspend fun pressRecent(): Boolean = pressKey(187)

    /**
     * Clear text field: select all + delete.
     * Uses a single shell call to avoid 50 round-trips.
     */
    suspend fun clearText(): Boolean {
        // Move to beginning, select all to end, delete
        val result = ShellExecutor.exec("input keyevent KEYCODE_MOVE_HOME KEYCODE_MOVE_END KEYCODE_DEL")
        return result is ShellResult.Success
    }
}
