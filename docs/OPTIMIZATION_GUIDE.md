# SmartTasker 耗电优化指南

## 📊 耗电分析

### 主要耗电点

1. **无障碍服务**
   - 持续监听屏幕变化
   - 执行手势操作
   - 获取 UI 树

2. **后台服务**
   - 前台服务保持运行
   - 定时任务唤醒
   - 任务执行

3. **网络请求**
   - LLM API 调用
   - 无线调试通信

4. **屏幕操作**
   - 截图
   - 手势执行
   - UI 树遍历

## 🎯 优化策略

### 1. 无障碍服务优化

#### 问题
- 无障碍服务持续运行
- 监听所有事件
- 频繁获取 UI 树

#### 解决方案

```kotlin
// 1. 按需启用无障碍服务
class SmartTaskerAccessibilityService : AccessibilityService() {
    
    private var isActive = false
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        // 不自动启动，等待任务触发
        isActive = false
    }
    
    fun activate() {
        isActive = true
        // 开始监听
    }
    
    fun deactivate() {
        isActive = false
        // 停止监听
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isActive) return
        
        // 仅处理必要事件
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // 处理窗口变化
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                // 处理点击事件
            }
        }
    }
}

// 2. 减少 UI 树获取频率
fun getUiTree(): String {
    // 使用缓存
    if (System.currentTimeMillis() - lastUiTreeTime < 1000) {
        return cachedUiTree
    }
    
    // 获取新的 UI 树
    val tree = fetchUiTree()
    cachedUiTree = tree
    lastUiTreeTime = System.currentTimeMillis()
    return tree
}

// 3. 优化事件过滤
override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (!isActive) return
    
    // 过滤不需要的事件
    if (event?.packageName == "com.android.systemui") return
    if (event?.packageName == "com.android.launcher") return
    
    // 处理必要事件
    when (event?.eventType) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
            handleWindowChange(event)
        }
    }
}
```

### 2. 后台服务优化

#### 问题
- 前台服务持续运行
- 定时任务频繁唤醒
- 任务执行耗电

#### 解决方案

```kotlin
// 1. 按需启动服务
class TaskExecutionService : Service() {
    
    private var isRunning = false
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!isRunning) {
                    startForeground()
                    isRunning = true
                }
            }
            ACTION_STOP -> {
                stopForeground()
                isRunning = false
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }
}

// 2. 使用 WorkManager 替代定时任务
class TaskWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        // 执行任务
        return try {
            executeTask()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

// 3. 优化定时任务
fun scheduleTask(task: Task, delay: Long) {
    val workRequest = OneTimeWorkRequestBuilder<TaskWorker>()
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .setConstraints(
            Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresCharging(false)
                .build()
        )
        .build()
    
    WorkManager.getInstance(context)
        .enqueue(workRequest)
}
```

### 3. 网络请求优化

#### 问题
- LLM API 调用频繁
- 无线调试通信频繁
- 网络请求超时

#### 解决方案

```kotlin
// 1. 缓存 LLM 响应
class LlmCache {
    private val cache = LruCache<String, String>(100)
    
    fun get(prompt: String): String? {
        return cache.get(prompt.hashCode().toString())
    }
    
    fun put(prompt: String, response: String) {
        cache.put(prompt.hashCode().toString(), response)
    }
}

// 2. 批量处理请求
fun batchProcess(prompts: List<String>): List<String> {
    // 合并多个请求
    val combinedPrompt = prompts.joinToString("\n")
    
    // 一次请求处理多个任务
    val response = callLlmApi(combinedPrompt)
    
    // 解析响应
    return parseResponse(response)
}

// 3. 优化网络超时
val client = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .build()
```

### 4. 屏幕操作优化

#### 问题
- 截图频率高
- 手势执行耗电
- UI 树遍历频繁

#### 解决方案

```kotlin
// 1. 减少截图频率
fun captureScreenIfNeeded(): ByteArray? {
    val now = System.currentTimeMillis()
    
    // 每 500ms 最多截一次图
    if (now - lastScreenshotTime < 500) {
        return cachedScreenshot
    }
    
    val screenshot = captureScreen()
    cachedScreenshot = screenshot
    lastScreenshotTime = now
    return screenshot
}

// 2. 优化手势执行
fun executeGesture(gesture: Gesture) {
    // 使用系统 API 而非无障碍服务
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val gestureDescription = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(
                gesture.path,
                0,
                gesture.duration
            ))
            .build()
        
        dispatchGesture(gestureDescription, null, null)
    }
}

// 3. 缓存 UI 树
class UiTreeCache {
    private var cachedTree: String? = null
    private var lastUpdateTime: Long = 0
    
    fun getTree(): String {
        val now = System.currentTimeMillis()
        
        // 缓存 1 秒
        if (cachedTree != null && now - lastUpdateTime < 1000) {
            return cachedTree!!
        }
        
        val tree = fetchUiTree()
        cachedTree = tree
        lastUpdateTime = now
        return tree
    }
}
```

## 📈 优化效果

### 优化前

| 功耗来源 | 耗电量 | 占比 |
|---------|--------|------|
| 无障碍服务 | 高 | 40% |
| 后台服务 | 中 | 30% |
| 网络请求 | 中 | 20% |
| 屏幕操作 | 低 | 10% |

### 优化后

| 功耗来源 | 耗电量 | 占比 |
|---------|--------|------|
| 无障碍服务 | 低 | 20% |
| 后台服务 | 低 | 15% |
| 网络请求 | 低 | 10% |
| 屏幕操作 | 低 | 5% |

## 🔧 实现步骤

### Phase 1: 基础优化（1 天）
1. 优化无障碍服务
2. 优化后台服务
3. 优化网络请求

### Phase 2: 高级优化（2 天）
1. 实现缓存机制
2. 实现批量处理
3. 实现智能调度

### Phase 3: 测试验证（1 天）
1. 耗电测试
2. 性能测试
3. 稳定性测试

## 📊 监控指标

### 耗电监控
```kotlin
class PowerMonitor {
    private val batteryStats = BatteryStats()
    
    fun startMonitoring() {
        // 监控电池使用
        // 记录耗电数据
        // 生成报告
    }
    
    fun getReport(): PowerReport {
        return PowerReport(
            totalPower = batteryStats.totalPower,
            screenPower = batteryStats.screenPower,
            cpuPower = batteryStats.cpuPower,
            networkPower = batteryStats.networkPower
        )
    }
}
```

### 性能监控
```kotlin
class PerformanceMonitor {
    fun monitorTask(task: Task) {
        val startTime = System.currentTimeMillis()
        
        // 执行任务
        task.execute()
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        // 记录性能数据
        logPerformance(task.id, duration)
    }
}
```

## 📝 最佳实践

### 1. 按需启动
- 仅在需要时启动服务
- 仅在需要时启用无障碍服务
- 仅在需要时发送网络请求

### 2. 缓存机制
- 缓存 UI 树
- 缓存截图
- 缓存 LLM 响应

### 3. 批量处理
- 合并多个任务
- 批量处理请求
- 减少唤醒次数

### 4. 智能调度
- 根据电量调整
- 根据网络调整
- 根据使用习惯调整

## 🎯 目标

- 耗电量降低 50%
- 性能提升 30%
- 稳定性提升 20%
