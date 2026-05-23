package com.smarttasker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * SmartTasker 无障碍服务
 * 
 * 核心功能：
 * 1. 监听屏幕事件
 * 2. 执行 UI 操作（点击、滑动、输入等）
 * 3. 获取 UI 树
 * 4. 截图
 */
class SmartTaskerAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "SmartTaskerACC"
        
        // 服务实例
        private var instance: SmartTaskerAccessibilityService? = null
        
        // 服务状态
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning
        
        // 当前屏幕信息
        private val _currentPackage = MutableStateFlow("")
        val currentPackage: StateFlow<String> = _currentPackage
        
        /**
         * 获取服务实例
         */
        fun getInstance(): SmartTaskerAccessibilityService? = instance
        
        /**
         * 服务是否运行中
         */
        fun isServiceRunning(): Boolean = instance != null
    }
    
    // 事件监听器
    private var eventListener: AccessibilityEventListener? = null
    
    // UI 树缓存
    private var cachedUiTree: String? = null
    private var lastUiTreeTime: Long = 0
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isRunning.value = true
        Log.d(TAG, "无障碍服务已连接")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // 更新当前包名
        val packageName = event.packageName?.toString() ?: ""
        if (packageName.isNotEmpty() && packageName != "com.smarttasker") {
            _currentPackage.value = packageName
        }
        
        // 处理事件
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowStateChanged(event)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                handleWindowContentChanged(event)
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                handleViewClicked(event)
            }
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                handleViewLongClicked(event)
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                handleViewScrolled(event)
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                handleViewTextChanged(event)
            }
        }
        
        // 通知监听器
        eventListener?.onEvent(event)
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务被中断")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isRunning.value = false
        Log.d(TAG, "无障碍服务已销毁")
    }
    
    // ============================================================
    // 事件处理
    // ============================================================
    
    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: ""
        val className = event.className?.toString() ?: ""
        Log.d(TAG, "窗口变化: $packageName/$className")
    }
    
    private fun handleWindowContentChanged(event: AccessibilityEvent) {
        // 清除 UI 树缓存
        cachedUiTree = null
    }
    
    private fun handleViewClicked(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: ""
        val className = event.className?.toString() ?: ""
        Log.d(TAG, "点击: $packageName/$className")
    }
    
    private fun handleViewLongClicked(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: ""
        Log.d(TAG, "长按: $packageName")
    }
    
    private fun handleViewScrolled(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: ""
        Log.d(TAG, "滚动: $packageName")
    }
    
    private fun handleViewTextChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: ""
        val text = event.text?.joinToString("") ?: ""
        Log.d(TAG, "文本变化: $packageName - $text")
    }
    
    // ============================================================
    // UI 操作
    // ============================================================
    
    /**
     * 点击坐标
     */
    fun tap(x: Int, y: Int): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val path = Path()
                path.moveTo(x.toFloat(), y.toFloat())
                
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
                    .build()
                
                dispatchGesture(gesture, null, null)
                Log.d(TAG, "点击: ($x, $y)")
                true
            } else {
                Log.e(TAG, "API 版本不支持手势")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "点击失败", e)
            false
        }
    }
    
    /**
     * 长按
     */
    fun longPress(x: Int, y: Int, durationMs: Long = 1000): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val path = Path()
                path.moveTo(x.toFloat(), y.toFloat())
                
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                    .build()
                
                dispatchGesture(gesture, null, null)
                Log.d(TAG, "长按: ($x, $y), 时长: ${durationMs}ms")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "长按失败", e)
            false
        }
    }
    
    /**
     * 滑动
     */
    fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMs: Long = 300
    ): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val path = Path()
                path.moveTo(startX.toFloat(), startY.toFloat())
                path.lineTo(endX.toFloat(), endY.toFloat())
                
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                    .build()
                
                dispatchGesture(gesture, null, null)
                Log.d(TAG, "滑动: ($startX, $startY) -> ($endX, $endY)")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "滑动失败", e)
            false
        }
    }
    
    /**
     * 输入文本
     */
    fun inputText(text: String, nodeInfo: AccessibilityNodeInfo? = null): Boolean {
        return try {
            val node = nodeInfo ?: findFocusedNode()
            if (node != null) {
                val arguments = Bundle()
                arguments.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                Log.d(TAG, "输入文本: $text")
                true
            } else {
                Log.e(TAG, "未找到焦点节点")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "输入文本失败", e)
            false
        }
    }
    
    /**
     * 按返回键
     */
    fun pressBack(): Boolean {
        return try {
            performGlobalAction(GLOBAL_ACTION_BACK)
            Log.d(TAG, "按返回键")
            true
        } catch (e: Exception) {
            Log.e(TAG, "按返回键失败", e)
            false
        }
    }
    
    /**
     * 按 Home 键
     */
    fun pressHome(): Boolean {
        return try {
            performGlobalAction(GLOBAL_ACTION_HOME)
            Log.d(TAG, "按 Home 键")
            true
        } catch (e: Exception) {
            Log.e(TAG, "按 Home 键失败", e)
            false
        }
    }
    
    /**
     * 最近任务
     */
    fun pressRecentApps(): Boolean {
        return try {
            performGlobalAction(GLOBAL_ACTION_RECENTS)
            Log.d(TAG, "按最近任务键")
            true
        } catch (e: Exception) {
            Log.e(TAG, "按最近任务键失败", e)
            false
        }
    }
    
    /**
     * 截图
     */
    fun takeScreenshot(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                takeScreenshot(
                    DISPLAY_DEFAULT,
                    mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(result: ScreenshotResult) {
                            Log.d(TAG, "截图成功")
                        }
                        override fun onFailure(errorCode: Int) {
                            Log.e(TAG, "截图失败: $errorCode")
                        }
                    }
                )
                true
            } else {
                Log.e(TAG, "API 版本不支持截图")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "截图失败", e)
            false
        }
    }
    
    // ============================================================
    // UI 树操作
    // ============================================================
    
    /**
     * 获取 UI 树
     */
    fun getUiTree(): String {
        val now = System.currentTimeMillis()
        
        // 使用缓存（1秒内）
        if (cachedUiTree != null && now - lastUiTreeTime < 1000) {
            return cachedUiTree!!
        }
        
        val tree = dumpNode(rootInActiveWindow, 0)
        cachedUiTree = tree
        lastUiTreeTime = now
        return tree
    }
    
    /**
     * 递归转储节点
     */
    private fun dumpNode(node: AccessibilityNodeInfo?, depth: Int): String {
        if (node == null) return ""
        
        val indent = "  ".repeat(depth)
        val sb = StringBuilder()
        
        sb.appendLine("$indent<node")
        sb.appendLine("$indent  class=\"${node.className}\"")
        sb.appendLine("$indent  text=\"${node.text}\"")
        sb.appendLine("$indent  resource-id=\"${node.viewIdResourceName}\"")
        sb.appendLine("$indent  content-desc=\"${node.contentDescription}\"")
        sb.appendLine("$indent  package=\"${node.packageName}\"")
        
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        sb.appendLine("$indent  bounds=\"[${bounds.left},${bounds.top}][${bounds.right},${bounds.bottom}]\"")
        
        sb.appendLine("$indent  clickable=\"${node.isClickable}\"")
        sb.appendLine("$indent  long-clickable=\"${node.isLongClickable}\"")
        sb.appendLine("$indent  scrollable=\"${node.isScrollable}\"")
        sb.appendLine("$indent  editable=\"${node.isEditable}\"")
        sb.appendLine("$indent  enabled=\"${node.isEnabled}\"")
        sb.appendLine("$indent  focused=\"${node.isFocused}\"")
        sb.appendLine("$indent  visible=\"${node.isVisibleToUser}\"")
        sb.appendLine("$indent>")
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            sb.append(dumpNode(child, depth + 1))
        }
        
        sb.appendLine("$indent</node>")
        return sb.toString()
    }
    
    /**
     * 查找节点
     */
    fun findNode(
        resourceId: String? = null,
        text: String? = null,
        contentDescription: String? = null,
        className: String? = null
    ): AccessibilityNodeInfo? {
        val rootNode = rootInActiveWindow ?: return null
        return findNodeRecursive(rootNode, resourceId, text, contentDescription, className)
    }
    
    private fun findNodeRecursive(
        node: AccessibilityNodeInfo,
        resourceId: String?,
        text: String?,
        contentDescription: String?,
        className: String?
    ): AccessibilityNodeInfo? {
        // 检查当前节点
        if (matchesNode(node, resourceId, text, contentDescription, className)) {
            return node
        }
        
        // 递归查找子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeRecursive(child, resourceId, text, contentDescription, className)
            if (result != null) return result
        }
        
        return null
    }
    
    private fun matchesNode(
        node: AccessibilityNodeInfo,
        resourceId: String?,
        text: String?,
        contentDescription: String?,
        className: String?
    ): Boolean {
        if (resourceId != null && node.viewIdResourceName != resourceId) return false
        if (text != null && node.text?.toString() != text) return false
        if (contentDescription != null && node.contentDescription?.toString() != contentDescription) return false
        if (className != null && node.className?.toString() != className) return false
        return true
    }
    
    /**
     * 查找焦点节点
     */
    private fun findFocusedNode(): AccessibilityNodeInfo? {
        val rootNode = rootInActiveWindow ?: return null
        return findFocusedNodeRecursive(rootNode)
    }
    
    private fun findFocusedNodeRecursive(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused) return node
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFocusedNodeRecursive(child)
            if (result != null) return result
        }
        
        return null
    }
    
    /**
     * 点击节点
     */
    fun clickNode(node: AccessibilityNodeInfo): Boolean {
        return try {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "点击节点: ${node.viewIdResourceName}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "点击节点失败", e)
            false
        }
    }
    
    /**
     * 长按节点
     */
    fun longClickNode(node: AccessibilityNodeInfo): Boolean {
        return try {
            node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            Log.d(TAG, "长按节点: ${node.viewIdResourceName}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "长按节点失败", e)
            false
        }
    }
    
    /**
     * 滚动节点
     */
    fun scrollNode(node: AccessibilityNodeInfo, direction: Int): Boolean {
        return try {
            val action = when (direction) {
                0 -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                1 -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                else -> return false
            }
            node.performAction(action)
            Log.d(TAG, "滚动节点: ${node.viewIdResourceName}, 方向: $direction")
            true
        } catch (e: Exception) {
            Log.e(TAG, "滚动节点失败", e)
            false
        }
    }
    
    // ============================================================
    // 监听器
    // ============================================================
    
    /**
     * 设置事件监听器
     */
    fun setEventListener(listener: AccessibilityEventListener?) {
        this.eventListener = listener
    }
    
    /**
     * 清除 UI 树缓存
     */
    fun clearUiTreeCache() {
        cachedUiTree = null
    }
}

/**
 * 无障碍事件监听器
 */
interface AccessibilityEventListener {
    fun onEvent(event: AccessibilityEvent)
}
