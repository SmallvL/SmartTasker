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
 */
class SmartTaskerAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "SmartTaskerACC"
        
        private var instance: SmartTaskerAccessibilityService? = null
        
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning
        
        private val _currentPackage = MutableStateFlow("")
        val currentPackage: StateFlow<String> = _currentPackage
        
        fun getInstance(): SmartTaskerAccessibilityService? = instance
        fun isServiceRunning(): Boolean = instance != null
    }
    
    private var eventListener: AccessibilityEventListener? = null
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
        
        val packageName = event.packageName?.toString() ?: ""
        if (packageName.isNotEmpty() && packageName != "com.smarttasker") {
            _currentPackage.value = packageName
        }
        
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                Log.d(TAG, "窗口变化: $packageName")
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                Log.d(TAG, "点击: $packageName")
            }
        }
        
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
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "点击失败", e)
            false
        }
    }
    
    fun longPress(x: Int, y: Int, durationMs: Long = 1000): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val path = Path()
                path.moveTo(x.toFloat(), y.toFloat())
                
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                    .build()
                
                dispatchGesture(gesture, null, null)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "长按失败", e)
            false
        }
    }
    
    fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long = 300): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val path = Path()
                path.moveTo(startX.toFloat(), startY.toFloat())
                path.lineTo(endX.toFloat(), endY.toFloat())
                
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                    .build()
                
                dispatchGesture(gesture, null, null)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "滑动失败", e)
            false
        }
    }
    
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
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "输入文本失败", e)
            false
        }
    }
    
    fun pressBack(): Boolean {
        return try {
            performGlobalAction(GLOBAL_ACTION_BACK)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun pressHome(): Boolean {
        return try {
            performGlobalAction(GLOBAL_ACTION_HOME)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun getUiTree(): String {
        val now = System.currentTimeMillis()
        if (cachedUiTree != null && now - lastUiTreeTime < 1000) {
            return cachedUiTree!!
        }
        
        val tree = dumpNode(rootInActiveWindow, 0)
        cachedUiTree = tree
        lastUiTreeTime = now
        return tree
    }
    
    private fun dumpNode(node: AccessibilityNodeInfo?, depth: Int): String {
        if (node == null) return ""
        
        val indent = "  ".repeat(depth)
        val sb = StringBuilder()
        
        sb.appendLine("$indent<node")
        sb.appendLine("$indent  class=\"${node.className}\"")
        sb.appendLine("$indent  text=\"${node.text}\"")
        sb.appendLine("$indent  resource-id=\"${node.viewIdResourceName}\"")
        sb.appendLine("$indent  content-desc=\"${node.contentDescription}\"")
        
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        sb.appendLine("$indent  bounds=\"[${bounds.left},${bounds.top}][${bounds.right},${bounds.bottom}]\"")
        sb.appendLine("$indent>")
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            sb.append(dumpNode(child, depth + 1))
        }
        
        sb.appendLine("$indent</node>")
        return sb.toString()
    }
    
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
        if (matchesNode(node, resourceId, text, contentDescription, className)) {
            return node
        }
        
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
    
    fun clickNode(node: AccessibilityNodeInfo): Boolean {
        return try {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun setEventListener(listener: AccessibilityEventListener?) {
        this.eventListener = listener
    }
    
    fun clearUiTreeCache() {
        cachedUiTree = null
    }
}

interface AccessibilityEventListener {
    fun onEvent(event: AccessibilityEvent)
}
