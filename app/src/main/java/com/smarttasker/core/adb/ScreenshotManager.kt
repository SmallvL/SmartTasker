package com.smarttasker.core.adb

import android.content.Context
import com.smarttasker.core.direct.SenseEngine
import com.smarttasker.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages screenshots taken via shell, saves to app internal storage.
 * Used by Route Studio to display current screen state when editing steps.
 */
class ScreenshotManager(private val context: Context) {

    private val screenshotDir: File
        get() = File(context.filesDir, "screenshots").also { it.mkdirs() }

    private val senseEngine = SenseEngine(context)

    /**
     * Take a screenshot via shell and save to internal storage.
     * @param stepId Optional step ID to name the file
     * @return Path to saved PNG file, or null on failure
     */
    suspend fun capture(stepId: String? = null): String? = withContext(Dispatchers.IO) {
        try {
            val result = senseEngine.screenshot()
            if (result !is com.smarttasker.core.bridge.ScreenshotResult.Success) {
                DebugLog.e("Screenshot", "Capture failed: ${result}")
                return@withContext null
            }

            val filename = if (stepId != null) "step_${stepId}.png" 
                           else "screenshot_${System.currentTimeMillis()}.png"
            val file = File(screenshotDir, filename)
            file.writeBytes(result.pngBytes)
            DebugLog.i("Screenshot", "Saved: ${file.absolutePath} (${result.pngBytes.size} bytes)")
            file.absolutePath
        } catch (e: Exception) {
            DebugLog.e("Screenshot", "Capture error: ${e.message}")
            null
        }
    }

    /**
     * Get the screenshot file for a step. Returns null if not captured yet.
     */
    fun getScreenshotFile(stepId: String): File? {
        val file = File(screenshotDir, "step_${stepId}.png")
        return if (file.exists()) file else null
    }

    /**
     * Delete all screenshots.
     */
    fun clearAll() {
        screenshotDir.listFiles()?.forEach { it.delete() }
        DebugLog.i("Screenshot", "Cleared all screenshots")
    }
}
