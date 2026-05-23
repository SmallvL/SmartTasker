package com.smarttasker.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay

/**
 * 设备控制器接口
 * 
 * 封装设备操作，支持多种控制方式：
 * 1. 无障碍服务（默认）
 * 2. ADB
 * 3. Root
 */
interface DeviceController {
    /**
     * 点击坐标
     */
    suspend fun tap(x: Int, y: Int): Boolean
    
    /**
     * 长按坐标
     */
    suspend fun longPress(x: Int, y: Int, durationMs: Long = 1000): Boolean
    
    /**
     * 滑动
     */
    suspend fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMs: Long = 300
    ): Boolean
    
    /**
     * 输入文本
     */
    suspend fun inputText(text: String): Boolean
    
    /**
     * 按返回键
     */
    suspend fun pressBack(): Boolean
    
    /**
     * 按 Home 键
     */
    suspend fun pressHome(): Boolean
    
    /**
     * 等待
     */
    suspend fun wait(ms: Long)
    
    /**
     * 获取屏幕尺寸
     */
    suspend fun getScreenSize(): Pair<Int, Int>
    
    /**
     * 获取当前应用包名
     */
    suspend fun getCurrentPackage(): String
    
    /**
     * 获取 UI 树
     */
    suspend fun getUiTree(): String
    
    /**
     * 查找元素
     */
    suspend fun findElement(
        resourceId: String? = null,
        text: String? = null,
        contentDescription: String? = null,
        className: String? = null
    ): ElementInfo?
    
    /**
     * 点击元素
     */
    suspend fun clickElement(
        resourceId: String? = null,
        text: String? = null,
        contentDescription: String? = null
    ): Boolean
    
    /**
     * 输入文本到元素
     */
    suspend fun inputToElement(
        text: String,
        resourceId: String? = null,
        textMatch: String? = null
    ): Boolean
    
    /**
     * 启动应用
     */
    suspend fun launchApp(packageName: String): Boolean
    
    /**
     * 检查应用是否安装
     */
    suspend fun isAppInstalled(packageName: String): Boolean
}

/**
 * 元素信息
 */
data class ElementInfo(
    val resourceId: String?,
    val text: String?,
    val contentDescription: String?,
    val className: String?,
    val packageName: String?,
    val bounds: Rect?,
    val isClickable: Boolean,
    val isEnabled: Boolean,
    val isVisible: Boolean
)

/**
 * 无障碍服务设备控制器实现
 */
class AccessibilityDeviceController : DeviceController {
    
    private val service: SmartTaskerAccessibilityService?
        get() = SmartTaskerAccessibilityService.getInstance()
    
    override suspend fun tap(x: Int, y: Int): Boolean {
        return service?.tap(x, y) ?: false
    }
    
    override suspend fun longPress(x: Int, y: Int, durationMs: Long): Boolean {
        return service?.longPress(x, y, durationMs) ?: false
    }
    
    override suspend fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMs: Long
    ): Boolean {
        return service?.swipe(startX, startY, endX, endY, durationMs) ?: false
    }
    
    override suspend fun inputText(text: String): Boolean {
        return service?.inputText(text) ?: false
    }
    
    override suspend fun pressBack(): Boolean {
        return service?.pressBack() ?: false
    }
    
    override suspend fun pressHome(): Boolean {
        return service?.pressHome() ?: false
    }
    
    override suspend fun wait(ms: Long) {
        delay(ms)
    }
    
    override suspend fun getScreenSize(): Pair<Int, Int> {
        // 默认返回 1080x1920，实际应该从系统获取
        return Pair(1080, 1920)
    }
    
    override suspend fun getCurrentPackage(): String {
        return SmartTaskerAccessibilityService.currentPackage.value
    }
    
    override suspend fun getUiTree(): String {
        return service?.getUiTree() ?: ""
    }
    
    override suspend fun findElement(
        resourceId: String?,
        text: String?,
        contentDescription: String?,
        className: String?
    ): ElementInfo? {
        val node = service?.findNode(resourceId, text, contentDescription, className)
        return node?.toElementInfo()
    }
    
    override suspend fun clickElement(
        resourceId: String?,
        text: String?,
        contentDescription: String?
    ): Boolean {
        val node = service?.findNode(resourceId, text, contentDescription)
        return if (node != null) {
            service?.clickNode(node) ?: false
        } else {
            false
        }
    }
    
    override suspend fun inputToElement(
        text: String,
        resourceId: String?,
        textMatch: String?
    ): Boolean {
        val node = service?.findNode(resourceId, textMatch)
        return if (node != null) {
            service?.inputText(text, node) ?: false
        } else {
            false
        }
    }
    
    override suspend fun launchApp(packageName: String): Boolean {
        // 需要通过 Intent 启动应用
        // 这里简化实现，实际应该使用 Context
        return false
    }
    
    override suspend fun isAppInstalled(packageName: String): Boolean {
        // 需要通过 PackageManager 检查
        // 这里简化实现，实际应该使用 Context
        return false
    }
    
    /**
     * 扩展函数：将 AccessibilityNodeInfo 转换为 ElementInfo
     */
    private fun AccessibilityNodeInfo.toElementInfo(): ElementInfo {
        val bounds = Rect()
        getBoundsInScreen(bounds)
        
        return ElementInfo(
            resourceId = viewIdResourceName,
            text = text?.toString(),
            contentDescription = contentDescription?.toString(),
            className = className?.toString(),
            packageName = packageName?.toString(),
            bounds = bounds,
            isClickable = isClickable,
            isEnabled = isEnabled,
            isVisible = isVisibleToUser
        )
    }
}
