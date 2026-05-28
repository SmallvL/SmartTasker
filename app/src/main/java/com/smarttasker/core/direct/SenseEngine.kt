package com.smarttasker.core.direct

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.smarttasker.core.bridge.CoreErrorCode
import com.smarttasker.core.bridge.HierarchyResult
import com.smarttasker.core.bridge.ScreenshotResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Sense engine - captures device state via shell.
 * Handles screenshots, UI hierarchy dumps, and app management.
 */
class SenseEngine(private val context: Context) {

    /**
     * Take a screenshot via screencap command.
     * Returns PNG bytes.
     * Uses /data/local/tmp/ for screencap output (writable by shell/root/sh).
     * App-internal cache dir is NOT writable by the shell user in ADB mode.
     */
    suspend fun screenshot(): ScreenshotResult = withContext(Dispatchers.IO) {
        try {
            // screencap 需要一个 shell 用户可写的路径
            // /data/local/tmp/ 对 shell、root、sh 均可写
            val tmpDir = File("/data/local/tmp")
            if (!tmpDir.exists()) tmpDir.mkdirs()
            val tmpFile = File(tmpDir, "smarttasker_screenshot_tmp.png")

            // Take screenshot to shared tmp path
            val result = ShellExecutor.exec("screencap -p ${tmpFile.absolutePath}")
            if (result is ShellResult.Error) {
                return@withContext ScreenshotResult.Error(
                    CoreErrorCode.CORE_NOT_RUNNING,
                    "截图失败: ${result.message}"
                )
            }

            // 读取截图文件（通过 shell 命令读取，因为 app 进程可能无权直接访问 /data/local/tmp/）
            if (!tmpFile.exists() || tmpFile.length() == 0L) {
                // Fallback: try reading via shell + base64
                val catResult = ShellExecutor.exec("cat ${tmpFile.absolutePath} | base64")
                if (catResult !is ShellResult.Success) {
                    return@withContext ScreenshotResult.Error(
                        CoreErrorCode.CORE_NOT_RUNNING,
                        "读取截图失败: ${(catResult as? ShellResult.Error)?.message ?: "未知错误"}"
                    )
                }
                val base64 = catResult.output.replace("\n", "").replace("\r", "")
                val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                tmpFile.delete()
                if (bytes.isEmpty()) {
                    return@withContext ScreenshotResult.Error(
                        CoreErrorCode.CORE_NOT_RUNNING,
                        "截图数据为空"
                    )
                }
                return@withContext ScreenshotResult.Success(bytes)
            }

            // Direct file read (faster, no base64 overhead)
            val bytes = tmpFile.readBytes()
            tmpFile.delete()

            if (bytes.isEmpty()) {
                return@withContext ScreenshotResult.Error(
                    CoreErrorCode.CORE_NOT_RUNNING,
                    "截图数据为空"
                )
            }

            ScreenshotResult.Success(bytes)
        } catch (e: Exception) {
            ScreenshotResult.Error(CoreErrorCode.UNKNOWN_ERROR, "截图异常: ${e.message}")
        }
    }

    /**
     * Dump UI hierarchy via uiautomator.
     * Uses /data/local/tmp/ for dump output (writable by shell/root/sh).
     * Returns XML string.
     */
    suspend fun dumpHierarchy(): HierarchyResult = withContext(Dispatchers.IO) {
        try {
            // uiautomator dump 同样需要 shell 用户可写的路径
            val tmpDir = File("/data/local/tmp")
            if (!tmpDir.exists()) tmpDir.mkdirs()
            val tmpFile = File(tmpDir, "smarttasker_hierarchy_tmp.xml")

            // Dump hierarchy
            val result = ShellExecutor.exec("uiautomator dump ${tmpFile.absolutePath}")
            if (result is ShellResult.Error) {
                return@withContext HierarchyResult.Error(
                    CoreErrorCode.CORE_NOT_RUNNING,
                    "UI dump 失败: ${result.message}"
                )
            }

            // Read the XML file directly
            if (!tmpFile.exists()) {
                return@withContext HierarchyResult.Error(
                    CoreErrorCode.CORE_NOT_RUNNING,
                    "UI dump 文件不存在"
                )
            }

            val xml = tmpFile.readText()
            tmpFile.delete()

            if (xml.isBlank()) {
                return@withContext HierarchyResult.Error(
                    CoreErrorCode.CORE_NOT_RUNNING,
                    "UI dump 为空"
                )
            }

            HierarchyResult.Success(xml)
        } catch (e: Exception) {
            HierarchyResult.Error(CoreErrorCode.UNKNOWN_ERROR, "UI dump 异常: ${e.message}")
        }
    }

    /**
     * Get current foreground activity.
     */
    suspend fun getCurrentActivity(): String? {
        val result = ShellExecutor.exec("dumpsys activity activities | grep mResumedActivity")
        return when (result) {
            is ShellResult.Success -> {
                val line = result.output.lines().firstOrNull() ?: return null
                // Parse: mResumedActivity: ActivityRecord{... com.example/.MainActivity ...}
                val regex = Regex("([a-zA-Z0-9_.]+)/([a-zA-Z0-9_.]+)")
                regex.find(line)?.value
            }
            else -> null
        }
    }

    /**
     * Get screen size.
     */
    suspend fun getScreenSize(): Pair<Int, Int>? {
        val result = ShellExecutor.exec("wm size")
        return when (result) {
            is ShellResult.Success -> {
                // Parse: Physical size: 1080x2400
                val regex = Regex("(\\d+)x(\\d+)")
                val match = regex.find(result.output)
                if (match != null) {
                    Pair(match.groupValues[1].toInt(), match.groupValues[2].toInt())
                } else null
            }
            else -> null
        }
    }

    /**
     * Launch an app by package name.
     * Uses am start (works in SH mode) instead of monkey.
     */
    suspend fun launchApp(packageName: String): Boolean {
        val result = ShellExecutor.exec("am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER $packageName")
        return result is ShellResult.Success
    }

    /**
     * Force stop an app.
     */
    suspend fun stopApp(packageName: String): Boolean {
        val result = ShellExecutor.exec("am force-stop $packageName")
        return result is ShellResult.Success
    }

    /**
     * List installed packages.
     */
    suspend fun listInstalledPackages(): List<String> {
        val result = ShellExecutor.exec("pm list packages -3")
        return when (result) {
            is ShellResult.Success -> {
                result.output.lines()
                    .filter { it.startsWith("package:") }
                    .map { it.removePrefix("package:").trim() }
            }
            else -> emptyList()
        }
    }

    /**
     * Check if a specific app is installed.
     */
    suspend fun isAppInstalled(packageName: String): Boolean {
        val result = ShellExecutor.exec("pm path $packageName")
        return result is ShellResult.Success && result.output.isNotBlank()
    }
}
