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
     * Input text. Special characters need escaping.
     */
    suspend fun inputText(text: String): Boolean {
        // Escape special characters for shell
        val escaped = text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("'", "\\'")
            .replace(" ", "%s")
            .replace("&", "\\&")
            .replace("|", "\\|")
            .replace("<", "\\<")
            .replace(">", "\\>")
            .replace("(", "\\(")
            .replace(")", "\\)")
        val result = ShellExecutor.exec("input text '$escaped'")
        return result is ShellResult.Success
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
     * Clear text field (select all + delete).
     */
    suspend fun clearText(): Boolean {
        // Select all then delete
        pressKey(29) // CTRL
        return true
    }
}
