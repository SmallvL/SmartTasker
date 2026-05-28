package com.smarttasker.core.screenshot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * 屏幕截图管理器
 * 负责截图、保存、验证
 */
class ScreenshotManager(private val context: Context) {

    companion object {
        private const val TAG = "ScreenshotManager"
        private const val SCREENSHOT_DIR = "screenshots"
        private const val MAX_SCREENSHOTS = 100 // 最大保存数量
    }

    private val screenshotDir: File by lazy {
        File(context.filesDir, SCREENSHOT_DIR).apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * 截图结果
     */
    sealed class ScreenshotResult {
        data class Success(val path: String, val timestamp: Long) : ScreenshotResult()
        data class Error(val message: String) : ScreenshotResult()
    }

    /**
     * 截取当前屏幕
     * @param tag 标签（用于文件名）
     * @return 截图结果
     */
    suspend fun captureScreen(tag: String = ""): ScreenshotResult = withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault())
            val dateStr = dateFormat.format(Date(timestamp))
            val filename = if (tag.isNotEmpty()) {
                "${tag}_${dateStr}.png"
            } else {
                "screenshot_${dateStr}.png"
            }

            val file = File(screenshotDir, filename)

            // 使用 ADB screencap 命令截图
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "screencap -p ${file.absolutePath}"))
            val exitCode = process.waitFor()

            if (exitCode == 0 && file.exists() && file.length() > 0) {
                Log.i(TAG, "Screenshot saved: ${file.absolutePath} (${file.length()} bytes)")
                cleanupOldScreenshots()
                ScreenshotResult.Success(file.absolutePath, timestamp)
            } else {
                Log.e(TAG, "Screenshot failed: exitCode=$exitCode, fileExists=${file.exists()}, fileSize=${file.length()}")
                ScreenshotResult.Error("截图失败: exitCode=$exitCode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Screenshot exception: ${e.message}", e)
            ScreenshotResult.Error("截图异常: ${e.message}")
        }
    }

    /**
     * 验证截图是否存在
     * @param path 截图路径
     * @return 是否存在且有效
     */
    fun validateScreenshot(path: String): Boolean {
        val file = File(path)
        return file.exists() && file.length() > 0
    }

    /**
     * 获取截图 Bitmap
     * @param path 截图路径
     * @return Bitmap 或 null
     */
    fun getScreenshotBitmap(path: String): Bitmap? {
        return try {
            val file = File(path)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode screenshot: ${e.message}", e)
            null
        }
    }

    /**
     * 获取所有截图
     * @return 截图文件列表（按时间倒序）
     */
    fun getAllScreenshots(): List<File> {
        return screenshotDir.listFiles()
            ?.filter { it.extension == "png" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /**
     * 获取指定任务的截图
     * @param taskTag 任务标签
     * @return 截图文件列表
     */
    fun getScreenshotsForTask(taskTag: String): List<File> {
        return screenshotDir.listFiles()
            ?.filter { it.name.startsWith(taskTag) && it.extension == "png" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /**
     * 删除截图
     * @param path 截图路径
     * @return 是否删除成功
     */
    fun deleteScreenshot(path: String): Boolean {
        return try {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete screenshot: ${e.message}", e)
            false
        }
    }

    /**
     * 清除所有截图
     */
    fun clearAllScreenshots() {
        try {
            screenshotDir.listFiles()?.forEach { it.delete() }
            Log.i(TAG, "All screenshots cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear screenshots: ${e.message}", e)
        }
    }

    /**
     * 清理旧截图，保留最新的 MAX_SCREENSHOTS 个
     */
    private fun cleanupOldScreenshots() {
        try {
            val files = screenshotDir.listFiles()
                ?.filter { it.extension == "png" }
                ?.sortedByDescending { it.lastModified() }
                ?: return

            if (files.size > MAX_SCREENSHOTS) {
                files.drop(MAX_SCREENSHOTS).forEach { it.delete() }
                Log.i(TAG, "Cleaned up ${files.size - MAX_SCREENSHOTS} old screenshots")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup screenshots: ${e.message}", e)
        }
    }

    /**
     * 获取截图目录大小
     * @return 大小（字节）
     */
    fun getDirectorySize(): Long {
        return screenshotDir.listFiles()?.sumOf { it.length() } ?: 0
    }

    /**
     * 获取截图数量
     * @return 截图数量
     */
    fun getScreenshotCount(): Int {
        return screenshotDir.listFiles()?.count { it.extension == "png" } ?: 0
    }
}
