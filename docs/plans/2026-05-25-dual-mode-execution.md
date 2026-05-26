# SmartTasker 双模式执行引擎设计

## 总览

SmartTasker 试跑支持两种执行模式：

| | LLM 模式 | 手动录制模式 |
|---|---|---|
| **原理** | AI 截图→理解→操作 | 用户真实触摸→录制 |
| **适用场景** | 复杂/未知流程 | 简单/固定流程 |
| **输出** | 结构化 Route（含截图+定位策略） | 坐标级 Route（精确回放） |
| **成本** | 每步 1-2 次 VLM 调用 | 零成本 |
| **可靠性** | 高（有视觉理解） | 中（坐标依赖分辨率） |

---

## 模式一：LLM 自主执行模式

### 流程

```
┌─────────────┐
│  启动目标 App  │
└──────┬──────┘
       ▼
┌─────────────┐
│   截图(screencap) │
└──────┬──────┘
       ▼
┌─────────────────────┐
│  发送给 VLM（截图+任务描述）  │
│  "当前屏幕是什么状态？        │
│   要完成'{任务}'需要做什么？" │
└──────┬──────────────┘
       ▼
┌─────────────────────┐
│  解析 VLM 返回的 Action     │
│  (tap/swipe/input/back/...) │
└──────┬──────────────┘
       ▼
┌─────────────┐
│  执行 Action  │
│  (InputEngine) │
└──────┬──────┘
       ▼
┌─────────────┐
│  等待 UI 稳定  │
│  (1-2s delay)  │
└──────┬──────┘
       ▼
┌─────────────┐
│  记录 Route Step │
└──────┬──────┘
       ▼
  ┌────┴────┐
  │ 完成？   │──否──→ 回到截图
  │ (最多20步) │
  └────┬────┘
       │是
       ▼
  ┌─────────┐
  │ 保存 Route  │
  └─────────┘
```

### 核心数据结构

```kotlin
// VLM 返回的 Action
data class AiAction(
    val op: String,           // TAP, SWIPE, INPUT, BACK, WAIT, DONE
    val args: List<Any>,      // [x, y] for TAP; [x1,y1,x2,y2,duration] for SWIPE
    val text: String = "",    // INPUT text
    val reason: String = "",  // AI 的推理过程
    val confidence: Float = 0.9f
)

// VLM 请求上下文
data class VlmContext(
    val taskDescription: String,
    val targetApp: String,
    val screenshot: ByteArray,      // PNG bytes
    val previousActions: List<String>, // 之前做了什么
    val stepIndex: Int,
    val maxSteps: Int = 20
)
```

### VLM Prompt 设计

```
你是一个手机操作助手。用户需要完成任务："{taskDescription}"

当前屏幕截图已附上。请分析屏幕内容，决定下一步操作。

回复 JSON 格式：
{
  "screen_analysis": "当前屏幕是...",
  "action": {
    "op": "TAP",          // TAP|SWIPE|INPUT|BACK|WAIT|DONE
    "args": [540, 1200],   // 坐标参数
    "text": "",            // INPUT 时的文本
    "reason": "点击搜索框"
  },
  "task_progress": "进行中", // 进行中|已完成|无法完成
  "confidence": 0.9
}
```

### 关键类

```
core/ai/
├── LlmExecutionEngine.kt    — 主循环引擎
├── VlmClient.kt             — VLM API 客户端（截图+prompt → action）
├── ActionParser.kt           — 解析 VLM JSON 响应
└── VlmPromptBuilder.kt      — 构建 prompt
```

### LlmExecutionEngine 核心逻辑

```kotlin
class LlmExecutionEngine(
    private val senseEngine: SenseEngine,
    private val inputEngine: InputEngine,
    private val vlmClient: VlmClient
) {
    suspend fun execute(
        taskDescription: String,
        targetPackage: String,
        onStep: (Int, AiAction, ByteArray) -> Unit,  // 回调每一步
        onDone: (List<RecordedStep>) -> Unit,
        onError: (String) -> Unit
    ) {
        // 1. 启动 App
        senseEngine.launchApp(targetPackage)
        delay(2000)

        val steps = mutableListOf<RecordedStep>()
        val previousActions = mutableListOf<String>()

        for (i in 0 until MAX_STEPS) {
            // 2. 截图
            val screenshot = senseEngine.screenshot()
            if (screenshot is ScreenshotResult.Error) { ... }

            // 3. 发给 VLM
            val context = VlmContext(
                taskDescription = taskDescription,
                targetApp = targetPackage,
                screenshot = screenshot.bytes,
                previousActions = previousActions,
                stepIndex = i
            )
            val response = vlmClient.analyze(context)

            // 4. 检查是否完成
            if (response.action.op == "DONE" || response.taskProgress == "已完成") {
                onDone(steps)
                return
            }

            // 5. 执行 Action
            executeAction(response.action)

            // 6. 记录步骤
            val step = RecordedStep(
                index = i,
                op = response.action.op,
                args = response.action.args,
                text = response.action.text,
                screenshot = screenshot,
                reason = response.action.reason,
                locatorStrategy = inferLocator(response),
                locatorValue = inferLocatorValue(response)
            )
            steps.add(step)
            onStep(i, response.action, screenshot)

            // 7. 等待 UI 稳定
            delay(1500)
        }
        onDone(steps)
    }
}
```

---

## 模式二：手动录制模式

### 流程

```
┌──────────────┐
│  用户点击"开始录制" │
└──────┬───────┘
       ▼
┌──────────────────┐
│  启动前台 Service +  │
│  全屏透明 Overlay    │
│  (显示录制控制条)     │
└──────┬───────────┘
       ▼
┌──────────────────┐
│  用户在 Overlay 上操作 │
│  每个触摸事件被拦截     │
│  记录为 Route Step     │
└──────┬───────────┘
       ▼
┌──────────────┐
│  用户点击"停止录制" │
└──────┬───────┘
       ▼
┌──────────────┐
│  保存 Route     │
│  (可选: 截图标注)  │
└──────────────┘
```

### 技术方案：透明 Overlay + MotionEvent 捕获

**不需要 AccessibilityService**，用 `WindowManager` + `TYPE_APPLICATION_OVERLAY` 即可。

```kotlin
// 录制 Overlay Service
class RecordingOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private val recordedSteps = mutableListOf<RecordedStep>()

    override fun onStartCommand(intent: Intent?, ...) {
        when (intent?.action) {
            "START" -> showOverlay()
            "STOP" -> { saveRoute(); hideOverlay() }
        }
    }

    private fun showOverlay() {
        val params = WindowManager.LayoutParams(
            MATCH_PARENT, MATCH_PARENT,
            TYPE_APPLICATION_OVERLAY,
            FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )

        overlayView = RecordingOverlayView(context).apply {
            onTouchRecorded = { event ->
                recordTouch(event)
            }
            onStopClicked = { stopRecording() }
        }

        windowManager.addView(overlayView, params)
    }

    private fun recordTouch(event: MotionEvent) {
        when (event.action) {
            ACTION_DOWN -> { /* 记录起点 */ }
            ACTION_UP -> {
                val step = if (isTap(event)) {
                    RecordedStep(op = "TAP", args = [event.x, event.y])
                } else {
                    RecordedStep(op = "SWIPE", args = [startX, startY, endX, endY, duration])
                }
                recordedSteps.add(step)
            }
        }
    }
}
```

### 录制 Overlay View

```
┌─────────────────────────────────┐
│  ┌───────────────────────────┐  │
│  │ ● REC   步骤 3   ⏱ 00:15 │  │  ← 顶部控制条（可拖拽）
│  │           [⏸ 暂停] [⏹ 停止] │  │
│  └───────────────────────────┘  │
│                                 │
│                                 │
│      (透明区域，触摸穿透到 App)      │
│                                 │
│                                 │
│                                 │
└─────────────────────────────────┘
```

关键：控制条区域 `FLAG_NOT_TOUCHABLE`（接收点击），其余区域 **触摸事件穿透到底层 App**，同时通过 `FLAG_WATCH_OUTSIDE_TOUCH` 或 `dispatchTouchEvent` 记录坐标。

**但有一个问题**：`TYPE_APPLICATION_OVERLAY` 的触摸事件默认不会穿透到下面的 App。

**解决方案**：使用 `FLAG_NOT_TOUCH_MODAL` + `FLAG_NOT_FOCUSABLE`，让不在 Overlay View 范围内的触摸事件穿透到下面。控制条只占顶部一小块区域，其余区域不设置 View，触摸自然穿透。

### 关键类

```
core/record/
├── RecordingOverlayService.kt   — 前台 Service + Overlay 管理
├── RecordingOverlayView.kt      — 顶部控制条 UI
├── TouchRecorder.kt             — 触摸事件 → 结构化 Step
└── RecordingState.kt            — 录制状态管理
```

### TouchRecorder 核心逻辑

```kotlin
class TouchRecorder {
    private var isRecording = false
    private var touchDownTime = 0L
    private var touchDownX = 0f
    private var touchDownY = 0f
    private val steps = mutableListOf<RecordedStep>()
    private var stepIndex = 0

    fun onTouchDown(x: Float, y: Float) {
        touchDownTime = System.currentTimeMillis()
        touchDownX = x
        touchDownY = y
    }

    fun onTouchUp(x: Float, y: Float): RecordedStep? {
        if (!isRecording) return null
        val duration = System.currentTimeMillis() - touchDownTime
        val distance = sqrt((x - touchDownX).pow(2) + (y - touchDownY).pow(2))

        val step = if (distance < 20f) {
            // 点击
            RecordedStep(
                index = stepIndex++,
                op = "TAP",
                args = listOf(touchDownX.toInt(), touchDownY.toInt()),
                summary = "点击 (${touchDownX.toInt()}, ${touchDownY.toInt()})"
            )
        } else if (duration > 500 && distance < 20f) {
            // 长按
            RecordedStep(
                index = stepIndex++,
                op = "LONG_PRESS",
                args = listOf(touchDownX.toInt(), touchDownY.toInt(), duration.toInt()),
                summary = "长按 (${touchDownX.toInt()}, ${touchDownY.toInt()}) ${duration}ms"
            )
        } else {
            // 滑动
            RecordedStep(
                index = stepIndex++,
                op = "SWIPE",
                args = listOf(touchDownX.toInt(), touchDownY.toInt(), x.toInt(), y.toInt(), duration.toInt()),
                summary = "滑动 (${touchDownX.toInt()},${touchDownY.toInt()}) → (${x.toInt()},${y.toInt()})"
            )
        }
        steps.add(step)
        return step
    }
}
```

---

## 共用基础设施

### RecordedStep（两种模式共用）

```kotlin
data class RecordedStep(
    val index: Int,
    val op: String,              // TAP, SWIPE, LONG_PRESS, INPUT, BACK, WAIT, DONE, OPEN_APP
    val args: List<Int> = emptyList(),
    val text: String = "",
    val summary: String = "",
    val screenshot: ByteArray? = null,
    val reason: String = "",        // LLM 模式的推理
    val locatorStrategy: String = "coordinate",  // coordinate/text/resource_id
    val locatorValue: String = "",                // 定位值
    val confidence: Float = 1.0f,
    val waitMs: Long = 0
)
```

### Route 保存（共用）

两种模式都输出 `List<RecordedStep>`，通过 `RouteAdapter.toRouteJson()` 转为 AutoLXB 格式保存到 Room DB。

---

## UI 改动

### TrialRunScreen 增加模式选择

```
┌──────────────────────┐
│  首次试跑               │
├──────────────────────┤
│                      │
│  ┌────────────────┐  │
│  │ 🤖 AI 自动执行   │  │  ← LLM 模式
│  │ AI 分析屏幕并操作 │  │
│  │ 需要配置 VLM API │  │
│  └────────────────┘  │
│                      │
│  ┌────────────────┐  │
│  │ ✋ 手动录制      │  │  ← 手动模式
│  │ 你操作，我学习   │  │
│  │ 无需 API       │  │
│  └────────────────┘  │
│                      │
└──────────────────────┘
```

### LLM 模式执行 UI

```
┌──────────────────────┐
│  AI 执行中 (3/20步)    │
├──────────────────────┤
│  ┌────────────────┐  │
│  │  [屏幕截图预览]   │  │
│  │                 │  │
│  │    ● (tap 540,  │  │  ← 标注操作位置
│  │       1200)     │  │
│  └────────────────┘  │
│                      │
│  AI: "看到搜索框，    │
│       点击它开始搜索"  │
│                      │
│  [⏸ 暂停] [⏹ 停止]   │
└──────────────────────┘
```

### 手动录制模式 UI

录制开始后，屏幕顶部出现悬浮控制条，用户正常操作手机，所有触摸被录制。

---

## 实现优先级

### Phase 1: 手动录制（零成本，可立即使用）
1. `TouchRecorder` — 触摸事件转 Step
2. `RecordingOverlayService` — 前台 Service + Overlay
3. `RecordingOverlayView` — 控制条 UI
4. TrialRunScreen 模式选择 UI
5. Route 保存集成

### Phase 2: LLM 模式（需要 VLM API）
1. `VlmClient` — VLM API 客户端
2. `ActionParser` — 解析 VLM 响应
3. `VlmPromptBuilder` — Prompt 构建
4. `LlmExecutionEngine` — 主循环
5. 执行 UI（截图预览 + 操作标注）
6. Settings 中 VLM API 配置

---

## 文件清单

### 新增文件
```
core/record/RecordingOverlayService.kt
core/record/RecordingOverlayView.kt
core/record/TouchRecorder.kt
core/record/RecordingState.kt
core/ai/LlmExecutionEngine.kt
core/ai/VlmClient.kt
core/ai/ActionParser.kt
core/ai/VlmPromptBuilder.kt
ui/trialrun/ModeSelectScreen.kt
ui/trialrun/LlmExecutionScreen.kt
ui/trialrun/ManualRecordingScreen.kt
```

### 修改文件
```
ui/trialrun/TrialRunScreen.kt — 拆分为模式选择 + 两种执行 Screen
service/TaskExecutionService.kt — 增加 executeWithLlm / executeWithRecording
core/direct/DirectCoreBridge.kt — 路由执行支持新格式
AndroidManifest.xml — RecordingOverlayService + SYSTEM_ALERT_WINDOW
ui/settings/SettingsScreen.kt — 增加 VLM API 配置入口
```
