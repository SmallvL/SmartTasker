# SmartTask AI 手动录制模式 — 宏录制成熟化开发 Plan

> 项目：SmartTask AI / AI 安卓自动化任务产品  
> 模块：手动录制模式 — 宏录制  
> 版本：v0.2  
> 目标：从“可录制脚本”升级为“可编辑、可回放、可发布、可诊断、可规模化维护”的成熟 Route 能力  

---

## 给开发 Agent 的一句话

**先实现**Phase 1（手动录制）**，可参考下面的开发plan进行功能实现**

---

## 1. 模块定位

手动录制模式是 SmartTask AI 的关键基础能力之一。

它的目标不是简单地录制屏幕坐标，而是把用户一次真实操作沉淀成结构化、可编辑、可回放、可发布模板的 `Route`。

最终用户流程：

```text
用户点击“一键录制”
  ↓
系统通过 ADB / Accessibility / UIAutomator / 截图能力采集用户操作
  ↓
将点击、滑动、长按、输入、按键、截图、等待等动作转成 List<RecordedStep>
  ↓
每个 Step 尽量绑定组件语义、文字、图片锚点、坐标 fallback
  ↓
保存为 Route
  ↓
用户在 Route Studio 中编辑、测试、回滚
  ↓
发布为模板
  ↓
其他用户或后续任务复用模板执行
```

---

## 2. 距离成熟产品还缺什么？

当前方案已经覆盖了“录制 → 结构化步骤 → 保存 Route → 编辑 → 回放”的基础闭环，但距离一个成熟产品，还需要重点补齐以下能力。

### 2.1 稳定性不足

只记录坐标的宏录制很容易因为以下变化失效：

- 分辨率变化。
- 横竖屏变化。
- App UI 改版。
- 文案变化。
- 列表滚动位置不同。
- 网络加载慢。
- 弹窗干扰。
- 权限弹窗干扰。
- 输入法遮挡。
- 系统导航栏高度不同。

成熟产品需要把“坐标”降级为 fallback，而不是主定位方式。

推荐定位优先级：

```text
1. resourceId + packageName + className
2. text / contentDescription + className
3. Accessibility bounds + clickable node
4. OCR 文本定位
5. 图片锚点匹配
6. 归一化坐标
7. 原始坐标
```

### 2.2 录制数据不可解释

如果 Step 只展示为：

```text
点击 x=521, y=1840
```

用户很难理解和编辑。

成熟产品应该展示为：

```text
点击「登录」按钮
组件：android.widget.Button
resourceId：com.xxx:id/login
位置：x=521, y=1840
fallback：文本定位 / 图片定位 / 坐标定位
```

这要求录制阶段必须保存：

- 操作类型。
- 操作时间。
- 操作前截图。
- 操作后截图。
- 点击组件信息。
- 当前 Activity / package。
- UI 树快照。
- 图片锚点。
- 置信度。
- fallback 策略。

### 2.3 缺少失败诊断能力

成熟产品不能只是“执行失败”，而要告诉用户为什么失败。

需要补齐：

- Step 执行日志。
- 每步耗时。
- 每步匹配到的目标。
- 每步使用的定位策略。
- 失败时截图。
- 失败时 UI 树。
- 失败原因分类。
- 修复建议。

失败原因示例：

```text
TARGET_NOT_FOUND：目标组件不存在
TEXT_CHANGED：目标文字变化
APP_NOT_IN_EXPECTED_STATE：当前页面不符合预期
TIMEOUT：等待超时
PERMISSION_BLOCKED：权限弹窗阻塞
NETWORK_LOADING：页面加载未完成
INPUT_METHOD_BLOCKED：输入法遮挡目标
COORDINATE_OUT_OF_BOUNDS：坐标超出屏幕范围
```

### 2.4 缺少 Route 版本管理

成熟产品中，用户会多次编辑一条 Route。如果没有版本管理，用户很容易把可用路线改坏。

需要支持：

- Route 草稿。
- Route 保存版本。
- Route 历史版本。
- Route 回滚。
- Route 执行报告绑定版本。
- 模板发布绑定版本。

建议版本模型：

```kotlin
data class RouteVersion(
    val routeId: String,
    val version: Int,
    val changeLog: String?,
    val stepsSnapshot: List<RecordedStep>,
    val assetsSnapshot: RouteAssets,
    val createdAt: Long,
    val createdBy: String?
)
```

### 2.5 缺少模板化和变量能力

成熟模板不能保存用户的真实账号、密码、验证码、收货地址等个人信息。

需要支持：

- 自动识别敏感输入。
- 将输入内容转成变量。
- 发布模板前变量检查。
- 执行模板前填写变量。
- 敏感变量本地加密保存。

示例：

```text
录制时输入：13812345678
模板发布时转换：{{phone_number}}

录制时输入：my_password
模板发布时转换：{{password}}
```

### 2.6 缺少安全边界

宏录制和自动执行具备较高风险，成熟产品必须限制危险行为。

需要增加：

- 录制中常驻提示。
- 执行中悬浮停止按钮。
- 高风险动作二次确认。
- 支付、转账、删除、授权类操作拦截。
- 敏感 App 黑名单或高风险标记。
- 模板发布前安全扫描。
- 执行报告可追溯。

高风险场景：

```text
支付确认
银行转账
删除数据
授权权限
发送消息
发布内容
修改密码
购买商品
```

### 2.7 缺少跨设备适配

成熟产品需要考虑不同设备之间的差异。

需要记录：

- 屏幕宽高。
- density。
- Android 版本。
- 厂商 ROM。
- 导航栏模式。
- 状态栏高度。
- 输入法高度。
- 当前横竖屏。
- App 版本。

并在执行时进行适配：

```text
同设备：优先原始坐标 + 语义定位
不同分辨率：优先语义定位 + 归一化坐标
不同 App 版本：优先 resourceId/text，失败后提示用户修复
不同 ROM：识别系统弹窗差异
```

### 2.8 缺少可测试体系

成熟产品不能只靠人工试。

需要建立：

- Recorder 单元测试。
- Raw event parser 测试。
- 手势识别测试。
- Step fusion 测试。
- Route serialization 测试。
- Playback 执行测试。
- UI 自动化回归测试。
- 真实设备兼容性测试矩阵。

### 2.9 缺少用户体验打磨

录制功能对普通用户来说很抽象，需要降低理解成本。

需要优化：

- 录制前引导。
- 录制中悬浮控制条。
- 暂停 / 继续 / 停止。
- 手动插入等待。
- 手动插入截图。
- 录制后自动生成步骤摘要。
- 低置信度 Step 高亮提醒。
- 一键测试整条路线。
- 一键修复失败步骤。

---

## 3. 优化后的成熟产品架构

### 3.1 总体架构

```text
┌─────────────────────────────────────────────┐
│                Route Studio UI              │
│  录制 / 编辑 / 测试 / 回滚 / 发布模板        │
└─────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────┐
│             Route Domain Layer              │
│  Route / RecordedStep / Template / Version   │
└─────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────┐
│              Recorder Engine                │
│ RawEventParser / GestureRecognizer / Fusion │
└─────────────────────────────────────────────┘
       ↓                  ↓                  ↓
┌─────────────┐   ┌────────────────┐   ┌──────────────┐
│ ADB Channel │   │ Accessibility  │   │ Screen/OCR   │
│ getevent    │   │ Event + Node   │   │ Screenshot   │
└─────────────┘   └────────────────┘   └──────────────┘
                     ↓
┌─────────────────────────────────────────────┐
│             Playback Engine                 │
│ TargetMatcher / StepExecutor / RetryPolicy   │
└─────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────┐
│         Report / Diagnostics / Repair        │
│ 执行报告 / 失败截图 / 修复建议                │
└─────────────────────────────────────────────┘
```

### 3.2 推荐模块结构

```text
smarttask-recorder/
  ├── adb/
  │   ├── AdbShellClient.kt
  │   ├── AdbSessionManager.kt
  │   ├── GetEventRecorder.kt
  │   ├── ScreenCaptureClient.kt
  │   └── UiDumpClient.kt
  │
  ├── accessibility/
  │   ├── SmartTaskAccessibilityService.kt
  │   ├── AccessibilityEventBuffer.kt
  │   ├── NodeTreeSnapshotter.kt
  │   └── NodeLocatorBuilder.kt
  │
  ├── recorder/
  │   ├── RecordingSessionManager.kt
  │   ├── RecordingStateMachine.kt
  │   ├── RecordingOverlayController.kt
  │   └── RecordingPermissionChecker.kt
  │
  ├── fusion/
  │   ├── RawInputParser.kt
  │   ├── TouchGestureRecognizer.kt
  │   ├── KeyEventRecognizer.kt
  │   ├── StepFusionEngine.kt
  │   ├── TargetResolver.kt
  │   └── StepPostProcessor.kt
  │
  ├── model/
  │   ├── Route.kt
  │   ├── RecordedStep.kt
  │   ├── TargetLocator.kt
  │   ├── StepAction.kt
  │   ├── RouteVersion.kt
  │   └── RouteTemplate.kt
  │
  ├── playback/
  │   ├── RoutePlaybackEngine.kt
  │   ├── StepExecutor.kt
  │   ├── TargetMatcher.kt
  │   ├── RetryPolicyRunner.kt
  │   └── PlaybackReport.kt
  │
  ├── studio/
  │   ├── RouteStudioViewModel.kt
  │   ├── StepEditorViewModel.kt
  │   ├── StepTimelineUiState.kt
  │   └── TemplatePublisher.kt
  │
  ├── diagnostics/
  │   ├── StepFailureClassifier.kt
  │   ├── RouteExecutionLogger.kt
  │   ├── RouteRepairAdvisor.kt
  │   └── DiagnosticSnapshotStore.kt
  │
  └── repository/
      ├── RouteRepository.kt
      ├── RouteAssetStore.kt
      ├── RouteVersionRepository.kt
      └── RouteTemplateRepository.kt
```

---

## 4. 核心数据结构

### 4.1 Route

```kotlin
data class Route(
    val id: String,
    val name: String,
    val version: Int,
    val source: RouteSource = RouteSource.MANUAL_RECORDING,
    val status: RouteStatus = RouteStatus.DRAFT,
    val deviceProfile: DeviceProfile,
    val appScope: AppScope?,
    val variables: List<RouteVariable>,
    val steps: List<RecordedStep>,
    val assets: RouteAssets,
    val createdAt: Long,
    val updatedAt: Long
)
```

### 4.2 RecordedStep

```kotlin
data class RecordedStep(
    val id: String,
    val order: Int,
    val type: RecordedStepType,
    val action: StepAction,
    val recordedAt: Long,
    val delayFromPreviousMs: Long,
    val appContext: AppContextSnapshot?,
    val deviceContext: DeviceContextSnapshot,
    val target: TargetSnapshot?,
    val beforeSnapshot: ScreenSnapshot?,
    val afterSnapshot: ScreenSnapshot?,
    val uiTreeSnapshotId: String?,
    val execution: StepExecutionConfig = StepExecutionConfig(),
    val diagnostics: StepDiagnostics = StepDiagnostics(),
    val confidence: Float,
    val source: StepSource = StepSource.RECORDED,
    val editable: Boolean = true,
    val notes: String? = null
)
```

### 4.3 Step 类型

```kotlin
enum class RecordedStepType {
    TAP,
    LONG_PRESS,
    SWIPE,
    DRAG,
    SCROLL,
    MULTI_TOUCH,

    KEY_EVENT,
    VOLUME_UP,
    VOLUME_DOWN,
    BACK,
    HOME,
    RECENTS,
    POWER,

    TEXT_INPUT,
    CLEAR_TEXT,

    WAIT,
    SCREENSHOT,
    ASSERT_SCREEN,
    ASSERT_TEXT,
    APP_START,
    APP_SWITCH,

    UNKNOWN_RAW_EVENT
}
```

### 4.4 StepAction

```kotlin
sealed class StepAction {
    data class Tap(
        val x: Int,
        val y: Int,
        val normalizedX: Float,
        val normalizedY: Float
    ) : StepAction()

    data class LongPress(
        val x: Int,
        val y: Int,
        val durationMs: Long
    ) : StepAction()

    data class Swipe(
        val startX: Int,
        val startY: Int,
        val endX: Int,
        val endY: Int,
        val durationMs: Long
    ) : StepAction()

    data class Key(
        val keyCode: Int,
        val keyName: String,
        val longPress: Boolean = false
    ) : StepAction()

    data class TextInput(
        val text: String,
        val inputMode: InputMode,
        val sensitive: Boolean = false,
        val variableName: String? = null
    ) : StepAction()

    data class Wait(
        val durationMs: Long,
        val reason: String? = null
    ) : StepAction()

    data class Screenshot(
        val assetId: String,
        val purpose: ScreenshotPurpose
    ) : StepAction()
}
```

### 4.5 TargetSnapshot

```kotlin
data class TargetSnapshot(
    val primaryLocator: TargetLocator,
    val fallbackLocators: List<TargetLocator>,
    val rawX: Int,
    val rawY: Int,
    val normalizedX: Float,
    val normalizedY: Float,
    val node: UiNodeSnapshot?,
    val visual: VisualTargetSnapshot?,
    val matchStrategy: MatchStrategy,
    val confidence: Float
)
```

### 4.6 TargetLocator

```kotlin
sealed class TargetLocator {

    data class AccessibilityNode(
        val packageName: String?,
        val className: String?,
        val viewIdResourceName: String?,
        val text: String?,
        val contentDescription: String?,
        val bounds: RectData,
        val clickable: Boolean,
        val enabled: Boolean,
        val selected: Boolean,
        val checked: Boolean?,
        val scrollable: Boolean,
        val depthPath: String,
        val siblingIndexPath: String
    ) : TargetLocator()

    data class Text(
        val text: String,
        val matchMode: TextMatchMode,
        val packageName: String?
    ) : TargetLocator()

    data class Image(
        val assetId: String,
        val cropRect: RectData,
        val perceptualHash: String?,
        val featureEmbeddingId: String?
    ) : TargetLocator()

    data class Coordinate(
        val x: Int,
        val y: Int,
        val normalizedX: Float,
        val normalizedY: Float
    ) : TargetLocator()
}
```

### 4.7 StepExecutionConfig

```kotlin
data class StepExecutionConfig(
    val preDelayMs: Long = 0,
    val postDelayMs: Long = 300,
    val timeoutMs: Long = 8000,
    val retryCount: Int = 2,
    val retryIntervalMs: Long = 500,
    val waitStrategy: WaitStrategy = WaitStrategy.WAIT_UI_IDLE,
    val failurePolicy: FailurePolicy = FailurePolicy.STOP_ROUTE
)
```

---

## 5. 一键录制完整流程

### 5.1 录制状态机

```text
IDLE
  ↓ 点击一键录制
CHECK_PERMISSION
  ↓
CALIBRATING_DEVICE
  ↓
RECORDING
  ├── PAUSED
  ├── INSERT_WAIT
  ├── INSERT_SCREENSHOT
  ├── MARK_VARIABLE
  └── STOPPING
        ↓
POST_PROCESSING
        ↓
ROUTE_REVIEW
        ↓
SAVED / PUBLISHED
```

### 5.2 开始录制前检查

必须检查：

```text
1. ADB 无线调试是否连接成功
2. ADB shell 是否可执行命令
3. AccessibilityService 是否开启
4. 悬浮窗权限是否开启
5. 当前屏幕尺寸、density、rotation 是否可读取
6. getevent 是否可读取输入流
7. screencap 是否可截图
8. uiautomator dump 是否可获取 UI 树
9. 当前 App package / activity 是否可读取
10. 是否处于禁止录制的敏感页面
```

权限检查输出：

```kotlin
data class RecordingPrecheckResult(
    val adbReady: Boolean,
    val accessibilityReady: Boolean,
    val overlayReady: Boolean,
    val screenshotReady: Boolean,
    val uiDumpReady: Boolean,
    val deviceProfileReady: Boolean,
    val blockers: List<RecordingBlocker>
)
```

---

## 6. ADB 录制实现细节

### 6.1 AdbShellClient

```kotlin
interface AdbShellClient {
    suspend fun exec(command: String, timeoutMs: Long = 5000): ShellResult
    fun stream(command: String): Flow<String>
    suspend fun kill(processId: String)
}
```

常用命令：

```bash
wm size
wm density
dumpsys window
dumpsys activity top
getevent -lp
getevent -lt
uiautomator dump /dev/tty
screencap -p
input tap x y
input swipe x1 y1 x2 y2 duration
input keyevent KEYCODE_BACK
input keyevent KEYCODE_VOLUME_UP
input keyevent KEYCODE_VOLUME_DOWN
```

### 6.2 Raw Input Parser

录制开始后执行：

```bash
getevent -lt
```

解析以下事件：

```text
EV_ABS：触摸坐标、触摸槽位、多指信息
EV_KEY：按键、触摸按下/抬起、音量键、返回键
EV_SYN：一帧输入事件结束
```

需要先执行：

```bash
getevent -lp
```

用于获取触摸设备的 raw 坐标范围：

```text
ABS_MT_POSITION_X min / max
ABS_MT_POSITION_Y min / max
```

坐标映射：

```kotlin
fun mapRawToScreenX(rawX: Int): Int {
    return ((rawX - minRawX).toFloat() / (maxRawX - minRawX) * screenWidth).roundToInt()
}

fun mapRawToScreenY(rawY: Int): Int {
    return ((rawY - minRawY).toFloat() / (maxRawY - minRawY) * screenHeight).roundToInt()
}
```

### 6.3 手势识别规则

```text
Tap:
  duration < 250ms
  moveDistance < 16dp

LongPress:
  duration >= 500ms
  moveDistance < 16dp

Swipe:
  moveDistance >= 32dp
  duration < 1200ms

Drag:
  moveDistance >= 32dp
  duration >= 1200ms

MultiTouch:
  同一时间存在多个 active pointer slot
```

MVP 优先支持：

```text
Tap
LongPress
Swipe
Back
Home
VolumeUp
VolumeDown
TextInput
Wait
Screenshot
```

---

## 7. Accessibility / UIAutomator 语义补全

### 7.1 监听 AccessibilityEvent

重点监听：

```text
TYPE_VIEW_CLICKED
TYPE_VIEW_LONG_CLICKED
TYPE_VIEW_SCROLLED
TYPE_VIEW_TEXT_CHANGED
TYPE_WINDOW_STATE_CHANGED
TYPE_WINDOW_CONTENT_CHANGED
TYPE_VIEW_FOCUSED
```

事件进入缓冲区：

```kotlin
class AccessibilityEventBuffer {
    fun add(event: AccessibilityEventSnapshot)
    fun findNearest(
        type: AccessibilityEventType,
        time: Long,
        toleranceMs: Long
    ): AccessibilityEventSnapshot?
}
```

### 7.2 节点树快照

每次发生关键操作时获取：

```kotlin
val root = rootInActiveWindow
```

如果 Accessibility root 不可用，则 fallback：

```bash
uiautomator dump /dev/tty
```

### 7.3 点击目标解析

```text
1. ADB raw event 识别出 Tap(x, y, t)
2. 在 AccessibilityEventBuffer 查找 t ± 300ms 的 TYPE_VIEW_CLICKED
3. 若找到 event.source，提取节点信息
4. 若没有找到，遍历当前 UI 树
5. 找 bounds 包含 x/y 的最深 clickable 节点
6. 仍找不到，则找 bounds 包含 x/y 的最深 visible 节点
7. 截取点击区域 crop，生成图片锚点
8. 生成 TargetSnapshot
```

---

## 8. 截图和图片锚点

### 8.1 截图策略

不要高频全量截图，采用事件触发式截图：

```text
1. 录制开始时截图
2. 每个关键 step 前截图 before
3. 每个关键 step 后截图 after
4. 用户手动插入截图时截图
5. 页面切换时截图
6. 失败诊断时截图
```

### 8.2 图片锚点

每次点击目标生成：

```text
cropRect：点击点周围 96dp ~ 160dp
asset：WebP 压缩保存
phash：感知哈希
ocrText：OCR 识别文本，可选
embedding：视觉特征，可选
```

用于回放 fallback：

```text
Accessibility 找不到
  ↓
OCR 找文字
  ↓
图片锚点匹配
  ↓
归一化坐标
  ↓
原始坐标
```

---

## 9. StepFusionEngine

### 9.1 输入

```kotlin
data class FusionInput(
    val rawInputActions: Flow<RecognizedInputAction>,
    val accessibilityEvents: Flow<AccessibilityEventSnapshot>,
    val uiTreeSnapshots: Flow<UiTreeSnapshot>,
    val screenSnapshots: Flow<ScreenSnapshot>
)
```

### 9.2 输出

```kotlin
Flow<RecordedStep>
```

### 9.3 融合规则

#### Tap

```text
Raw Tap(x, y, t)
  ↓
find AccessibilityEvent(TYPE_VIEW_CLICKED, t ± 300ms)
  ↓
resolve node
  ↓
capture before / after screenshot
  ↓
emit RecordedStep(TAP)
```

#### Swipe

```text
Raw Swipe(start, end, duration)
  ↓
find TYPE_VIEW_SCROLLED 或窗口内容变化
  ↓
resolve scroll container
  ↓
emit RecordedStep(SWIPE / SCROLL)
```

#### Volume Key

```text
Raw EV_KEY KEYCODE_VOLUME_UP / DOWN
  ↓
emit RecordedStep(VOLUME_UP / VOLUME_DOWN)
```

#### TextInput

```text
TYPE_VIEW_TEXT_CHANGED
  ↓
diff oldText / newText
  ↓
合并短时间连续输入
  ↓
emit RecordedStep(TEXT_INPUT)
```

密码输入处理：

```text
1. 不保存明文
2. sensitive = true
3. 转成变量占位符，如 {{password}}
4. 发布模板时强制变量化
```

---

## 10. Route Studio 编辑能力

### 10.1 Step 列表

每一步展示：

```text
序号
动作类型
截图缩略图
目标组件描述
延时
定位策略
置信度
是否有 fallback
执行状态
```

示例：

```text
1. 点击「登录」按钮
   target: Button text="登录"
   locator: resourceId > text > image > coordinate
   delay: 850ms
   confidence: 0.93

2. 输入文本 {{phone}}
   target: EditText hint="手机号"
   sensitive: false

3. 点击音量上键
   key: KEYCODE_VOLUME_UP

4. 等待页面加载
   waitStrategy: WAIT_UI_IDLE
```

### 10.2 单步编辑能力

每个 step 支持：

```text
1. 修改动作类型
2. 修改坐标
3. 重新选择屏幕组件
4. 修改目标文字
5. 修改图片锚点
6. 修改延时
7. 修改超时时间
8. 修改重试次数
9. 修改 fallback 优先级
10. 删除 step
11. 插入 step
12. 单步测试
13. 查看执行日志
14. 查看失败截图
```

### 10.3 延时编辑

支持三种模式：

```text
真实延时：保持录制时 delay
固定延时：用户手动设置 500ms / 1000ms / 3000ms
智能等待：等待目标出现 / 等待文本出现 / 等待页面稳定
```

建议将长时间固定 delay 自动建议转换为智能等待：

```text
录制 delay = 5000ms
系统建议：改为“等待目标按钮出现，最多 8 秒”
```

---

## 11. 回放执行逻辑

### 11.1 RoutePlaybackEngine

```kotlin
class RoutePlaybackEngine(
    private val adb: AdbShellClient,
    private val accessibility: AccessibilityBridge,
    private val targetMatcher: TargetMatcher,
    private val logger: RouteExecutionLogger
) {
    suspend fun execute(route: Route): PlaybackReport {
        for (step in route.steps) {
            executeStep(step)
        }
    }
}
```

### 11.2 点击类 Step 执行优先级

```text
1. 等待 app / activity 到达预期上下文
2. 查找 Accessibility Node
3. 找到后优先 performAction(ACTION_CLICK)
4. 无法 performAction 时使用 dispatchGesture
5. 无法 dispatchGesture 时使用 adb input tap
6. 节点找不到时使用 OCR / 图片匹配
7. 最后 fallback 到坐标点击
```

### 11.3 TargetMatcher

```kotlin
class TargetMatcher {
    suspend fun findTarget(step: RecordedStep): MatchedTarget? {
        return findByResourceId(step)
            ?: findByTextOrContentDesc(step)
            ?: findByNodePath(step)
            ?: findByOcr(step)
            ?: findByImage(step)
            ?: findByCoordinate(step)
    }
}
```

---

## 12. Route 保存格式

建议本地目录结构：

```text
/routes/{routeId}/route.json
/routes/{routeId}/versions/{version}.json
/routes/{routeId}/assets/screenshots/*.webp
/routes/{routeId}/assets/crops/*.webp
/routes/{routeId}/assets/ui_tree/*.json
/routes/{routeId}/reports/*.json
```

Room 表建议：

```text
routes
route_steps
route_assets
route_variables
route_versions
route_execution_reports
route_templates
```

---

## 13. 模板发布能力

### 13.1 发布前检查

```text
1. 检查是否包含敏感文本
2. 检查是否包含账号、手机号、验证码、密码、地址
3. 将可变输入抽成变量
4. 去除或脱敏私人截图
5. 检查目标 App 包名和版本范围
6. 检查是否存在支付、转账、删除等高风险动作
7. 生成模板说明和使用条件
8. 生成模板安全等级
```

### 13.2 RouteTemplate

```kotlin
data class RouteTemplate(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val appPackageName: String?,
    val appVersionRange: String?,
    val route: Route,
    val variables: List<RouteVariable>,
    val safetyLevel: SafetyLevel,
    val authorId: String?,
    val createdAt: Long
)
```

---

## 14. 安全与风控

### 14.1 录制侧

```text
1. 录制期间显示常驻悬浮提示
2. 状态栏通知显示“SmartTask 正在录制”
3. 用户可随时停止录制
4. 不录制 SmartTask 自身控制悬浮窗点击
5. 密码输入默认不保存明文
6. 支付、银行、系统权限页面标记高风险
7. 禁止静默录制
```

### 14.2 执行侧

```text
1. 首次执行模板必须用户确认
2. 高风险步骤执行前二次确认
3. 支付、转账、删除、授权类操作默认不允许后台静默执行
4. 执行过程显示可中断悬浮按钮
5. 保存完整执行报告
6. 失败时自动停止或进入安全等待
```

### 14.3 模板市场侧

```text
1. 模板上传前做静态扫描
2. 敏感变量强制变量化
3. 高风险模板禁止公开发布或需要人工审核
4. 模板显示权限需求和执行范围
5. 支持用户举报和模板下架
```

---

## 15. 开发阶段拆分

## Phase 0：基础能力验证

### 目标

验证当前设备上 ADB + Accessibility + 截图 + UI dump 是否全部可用。

### TODO

```text
1. 实现 ADB Shell Client
2. 执行 wm size / wm density
3. 执行 getevent -lp
4. 执行 getevent -lt 并读取实时事件
5. 执行 screencap -p 获取截图
6. 执行 uiautomator dump 获取 UI XML
7. AccessibilityService 获取 rootInActiveWindow
8. AccessibilityService 监听点击、滚动、文本变化
9. 获取当前 package / activity
10. 建立 DeviceProfile
```

### 完成标准

```text
能在测试页点击按钮，并同时拿到：
1. 原始坐标
2. AccessibilityEvent
3. 节点 bounds
4. 当前截图
5. 当前 UI 树
6. 当前 app context
```

---

## Phase 1：手动录制 MVP

### 目标

实现真正可用的一键手动录制能力，能输出基础 `List<RecordedStep>` 并保存为 Route。

### 范围

Phase 1 只做手动录制，不做复杂模板市场，不做 AI 自动修复，不做高级 OCR，不做复杂多指手势。

必须支持：

```text
Tap
LongPress
Swipe
Back
Home
VolumeUp
VolumeDown
TextInput
Wait
Screenshot
```

### TODO

#### 1. RecordingSessionManager

```kotlin
class RecordingSessionManager {
    suspend fun startRecording(config: RecordingConfig): RecordingSession
    suspend fun pauseRecording(sessionId: String)
    suspend fun resumeRecording(sessionId: String)
    suspend fun stopRecording(sessionId: String): RouteDraft
}
```

需要管理：

```text
录制状态
开始时间
当前设备信息
当前 App 信息
事件流订阅
截图任务
临时文件目录
Step buffer
```

#### 2. RecordingStateMachine

实现状态：

```text
IDLE
CHECK_PERMISSION
CALIBRATING_DEVICE
RECORDING
PAUSED
STOPPING
POST_PROCESSING
ROUTE_REVIEW
```

#### 3. RawInputParser

从 `getevent -lt` 中解析：

```text
EV_ABS
EV_KEY
EV_SYN
ABS_MT_POSITION_X
ABS_MT_POSITION_Y
ABS_MT_TRACKING_ID
BTN_TOUCH
KEY_BACK
KEY_HOME
KEY_VOLUMEUP
KEY_VOLUMEDOWN
```

输出：

```kotlin
sealed class RawInputEvent {
    data class Abs(...): RawInputEvent()
    data class Key(...): RawInputEvent()
    data class Syn(...): RawInputEvent()
}
```

#### 4. TouchGestureRecognizer

将 raw event 识别为：

```kotlin
sealed class RecognizedInputAction {
    data class Tap(...): RecognizedInputAction()
    data class LongPress(...): RecognizedInputAction()
    data class Swipe(...): RecognizedInputAction()
    data class Key(...): RecognizedInputAction()
}
```

#### 5. AccessibilityEventBuffer

录制期间缓存最近 3~5 秒 AccessibilityEvent。

用于和 raw event 融合：

```text
点击坐标 + 点击事件 + 节点信息
滑动坐标 + 滚动事件 + 滚动容器
文本变化 + 当前焦点输入框
```

#### 6. TargetResolver

对 Tap / LongPress 解析目标：

```text
1. 从 AccessibilityEvent 找 source node
2. 从当前 UI 树按坐标找 node
3. 提取 text / contentDescription / resourceId / className / bounds
4. 生成 coordinate locator
5. 生成 fallback locator
```

Phase 1 可以先不实现 OCR 和复杂图片匹配，但要预留字段。

#### 7. Screenshot Capture

Phase 1 至少支持：

```text
录制开始截图
每个 Step after 截图
用户手动插入截图
录制结束截图
```

如果性能允许，再加 before screenshot。

#### 8. StepFusionEngine

把以下输入融合成 Step：

```text
Raw touch / key action
AccessibilityEvent
UI tree snapshot
Screen snapshot
Device context
App context
```

输出：

```kotlin
List<RecordedStep>
```

#### 9. RouteDraft 生成

停止录制后生成：

```kotlin
data class RouteDraft(
    val routeId: String,
    val name: String,
    val deviceProfile: DeviceProfile,
    val appScope: AppScope?,
    val steps: List<RecordedStep>,
    val assets: RouteAssets
)
```

#### 10. 简版录制 UI

需要包含：

```text
开始录制
暂停录制
继续录制
插入等待
插入截图
停止录制
当前已录制步数
当前录制状态
```

建议使用悬浮窗控制条。

### Phase 1 完成标准

```text
1. 用户点击“一键录制”后，系统进入录制状态
2. 用户点击、长按、滑动、返回、Home、音量键，均能被记录
3. 用户输入文本，能被记录为 TEXT_INPUT
4. 每个 Step 至少包含 type、action、time、delay、deviceContext
5. Tap / LongPress 尽量包含 target node 信息
6. 每个 Step 至少有坐标 fallback
7. 停止录制后能生成 List<RecordedStep>
8. List<RecordedStep> 能保存为 RouteDraft
9. RouteDraft 能在本地重新读取
10. 录制过程可暂停、继续、停止
```

### Phase 1 测试完成标准

```text
测试 1：连续点击 3 个按钮，生成 3 个 TAP step
测试 2：滑动列表，生成 SWIPE 或 SCROLL step
测试 3：长按元素，生成 LONG_PRESS step
测试 4：按返回键，生成 BACK step
测试 5：按音量上键，生成 VOLUME_UP step
测试 6：按音量下键，生成 VOLUME_DOWN step
测试 7：输入普通文本，生成 TEXT_INPUT step
测试 8：录制中暂停，再继续，暂停期间操作不应进入 Route
测试 9：停止录制后生成 route.json
测试 10：重启 App 后能读取 route.json 并展示步骤列表
```

---

## Phase 2：组件语义绑定增强

### 目标

让 Step 不只是坐标，而是可解释、可编辑、可跨场景复用。

### TODO

```text
1. 完善 UI 树解析
2. 完善坐标命中节点算法
3. 实现 AccessibilityEvent 与 Raw Tap 时间关联
4. 提取 packageName / className / text / contentDesc / resourceId / bounds
5. 为每个 Tap 生成 primaryLocator + fallbackLocators
6. 生成点击区域 crop 图片
7. 增加 confidence 评分
8. 低置信度 Step 标记
```

### 完成标准

```text
点击“登录”按钮后，Step 显示：
Button text="登录"
resourceId="xxx:id/login"
bounds=[...]
coordinate=[x,y]
confidence > 0.8
```

---

## Phase 3：Route Studio 简版

### 目标

录完后可以人工编辑。

### TODO

```text
1. Step 列表 UI
2. Step 详情页
3. 修改 delay
4. 修改坐标
5. 修改文本输入内容
6. 删除 step
7. 插入 wait step
8. 插入 screenshot step
9. 单步测试
10. 保存 Route
```

### 完成标准

```text
用户可以把录制出来的 Route 修改后保存，并重新打开继续编辑。
```

---

## Phase 4：回放执行

### 目标

录制路线可以复用执行。

### TODO

```text
1. 实现 RoutePlaybackEngine
2. 实现 StepExecutor
3. 实现 TargetMatcher
4. 支持点击、滑动、长按、按键、文本输入
5. 支持 locator 优先级匹配
6. 支持坐标 fallback
7. 支持失败重试
8. 生成执行报告
```

### 完成标准

```text
录制一条 5~10 步 Route，退出 App 后重新执行，能稳定完成原任务。
```

---

## Phase 5：智能等待与稳定性优化

### 目标

减少固定 delay，提高回放成功率。

### TODO

```text
1. UI idle 检测
2. 等待节点出现
3. 等待文本出现
4. 等待 Activity 切换
5. 等待截图变化稳定
6. 自动将长 delay 建议转换为 waitUntil
7. 执行失败时截图记录
8. 弹窗干扰识别
```

### 完成标准

```text
Route 在网络波动、页面加载慢、轻微 UI 变化的情况下仍能执行成功。
```

---

## Phase 6：诊断、修复与版本管理

### 目标

让用户能知道为什么失败，并能安全回滚。

### TODO

```text
1. Route 版本管理
2. Route 执行报告
3. Step 失败截图
4. Step 失败原因分类
5. 失败 Step 高亮
6. 一键回滚到历史版本
7. 简单修复建议
```

### 完成标准

```text
执行失败后，用户能看到失败步骤、失败原因、失败截图，并可修改或回滚。
```

---

## Phase 7：模板发布

### 目标

Route 可以发布为模板，并可复用。

### TODO

```text
1. 模板发布表单
2. 变量提取
3. 敏感内容检测
4. 私人截图脱敏
5. 模板本地预览
6. 模板导入
7. 模板执行前变量填写
8. 高风险模板标记
```

### 完成标准

```text
用户录制一条任务 → 把手机号、密码等变成变量 → 保存为模板 → 其他用户导入后填写变量即可执行。
```

---

## Phase 8：高级能力

后续版本再实现：

```text
1. OCR 文字定位
2. 图片模板匹配点击
3. 多指手势录制与回放
4. AI 自动修复失败步骤
5. 跨设备适配优化
6. 云端同步
7. 模板市场审核系统
8. 自动兼容 App 新版本
9. 复杂条件分支
10. 循环和变量表达式
```

---

## 16. Phase 1 推荐提交拆分

为了方便 Agent 开发，Phase 1 建议拆成以下提交：

### Commit 1：新增 recorder 模块和基础 model

包含：

```text
Route
RecordedStep
StepAction
TargetSnapshot
TargetLocator
DeviceProfile
AppContextSnapshot
```

### Commit 2：实现 ADB Shell Client

包含：

```text
exec command
stream command
wm size
wm density
getevent -lp
getevent -lt stream
```

### Commit 3：实现 RawInputParser

包含：

```text
解析 getevent line
识别 EV_ABS / EV_KEY / EV_SYN
输出 RawInputEvent
```

### Commit 4：实现 TouchGestureRecognizer

包含：

```text
Tap
LongPress
Swipe
KeyEvent
VolumeUp
VolumeDown
Back
Home
```

### Commit 5：接入 AccessibilityEventBuffer

包含：

```text
监听点击
监听滚动
监听文本变化
监听窗口变化
缓存最近事件
```

### Commit 6：实现 TargetResolver MVP

包含：

```text
坐标命中节点
提取 text / contentDescription / resourceId / className / bounds
生成 coordinate fallback
```

### Commit 7：实现 RecordingSessionManager

包含：

```text
start
pause
resume
stop
step buffer
route draft output
```

### Commit 8：实现录制悬浮控制条

包含：

```text
开始录制
暂停
继续
插入等待
插入截图
停止
已录制步数显示
```

### Commit 9：实现 route.json 保存与读取

包含：

```text
RouteDraft 序列化
本地文件保存
本地文件读取
步骤列表展示
```

### Commit 10：补齐 Phase 1 测试

包含：

```text
点击录制测试
滑动录制测试
按键录制测试
文本输入录制测试
暂停继续测试
route.json 读写测试
```

---

## 17. Phase 1 不做的内容

为了避免范围失控，Phase 1 明确不做：

```text
1. 模板市场
2. 云同步
3. AI 自动修复
4. OCR 文字定位
5. 图片模板匹配执行
6. 多指复杂手势
7. 条件分支
8. 循环
9. 跨设备高级适配
10. 支付 / 银行类高风险场景自动执行
```

但 Phase 1 的数据结构必须为这些能力预留字段。

---

## 18. 成熟化优先级建议

优先级从高到低：

```text
P0：手动录制基础闭环
P0：Step 结构化保存
P0：Tap / Swipe / Key / TextInput 识别
P0：坐标 + 基础组件信息绑定
P0：RouteDraft 本地保存读取

P1：Route Studio 简版编辑
P1：回放执行
P1：失败截图和执行报告
P1：智能等待
P1：Route 版本管理

P2：模板变量化
P2：模板发布
P2：图片锚点 fallback
P2：OCR 文字定位
P2：安全扫描

P3：AI 修复
P3：跨设备高级适配
P3：模板市场
P3：云同步
```

---

## 19. 最终成熟产品判断标准

一个成熟版本至少需要达到：

```text
1. 普通用户可以一键录制一条路线
2. 路线能被解释为自然语言步骤
3. 用户可以人工编辑每一步
4. 路线能稳定回放
5. 失败时能定位原因
6. 用户可以修复失败步骤
7. 路线可以版本回滚
8. 路线可以变量化为模板
9. 模板执行有安全边界
10. 高风险行为不会静默执行
```

---

## 20. 最小可交付闭环

开发 Agent 当前应该优先交付这个闭环：

```text
一键录制
  ↓
记录 Tap / Swipe / LongPress / Key / TextInput
  ↓
绑定基础组件信息 + 坐标 fallback
  ↓
生成 List<RecordedStep>
  ↓
保存 RouteDraft
  ↓
读取 RouteDraft
  ↓
展示 Step 列表
```

只要这个闭环稳定，后续 Route Studio、回放、模板化和 AI 修复都可以在其上迭代。
