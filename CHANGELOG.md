# 更新日志

本文件记录 SmartTasker 所有重要变更。

版本号遵循语义化版本规范：
- **Major.Minor.Patch** (X.Y.Z)
- Patch: bug 修复、小改动 (0.0.1→0.0.2)
- Minor: 新功能、大改动 (0.1.0→0.2.0)
- Major: 架构变更、重大重构 (1.0.0→2.0.0)

编号不限制十进制，允许 `1.101.65` 等形式。

---

## [0.3.0] — 2026-05-27

### Added
- **ShellAdbClient** — 通过 `nc` 管道传输 ADB 二进制协议，绕过 Android `untrusted_app` 对 Java Socket 的 ECONNREFUSED 限制
- **ADB_LOCAL 模式** — 模拟器中 App 可通过 IPv6 (`::1:5555`) 连接 adbd，获得完整 shell 执行能力（截图、录制、input）
- 模拟器 root adb 支持验证通过

### Fixed
- ADB OKAY 响应 arg0 不匹配问题（接受任何 OKAY 而非严格匹配 localId）
- ShellExecutor 本地 ADB 检测切换到 ShellAdbClient

## [0.1.1] — 2026-05-26

### Added
- **截图点击点高亮** (#E02): 步骤截图叠加十字准星标记，显示点击坐标 (x,y)
- **定位编辑器** (#E05): 支持修改步骤定位策略（坐标/文本/资源ID/内容描述）和定位值

### Changed
- `StepDetailPanel` 重构：截图使用 Box 叠加 Canvas 绘制标记圈+准星+坐标标签
- 定位方式改为可编辑状态（locatorStrategy/locatorValue 本地状态 + FilterChip 选择器）

---

## [0.1.2] — 2026-05-26

### Fixed
- **保存并启用**：现在会先 `publishRoute()` 再激活任务（之前只激活 task 未发布路线）
- **放弃路线**：离开学习结果页时清理 DB（`deleteRoute()` 删除版本+步骤）
- **删除步骤重排序**：删除后自动 `reindexSteps()`，stepIndex 保持连续
- **删除已选中步骤**：自动关闭详情面板，避免显示已删除步骤

### Changed
- **发布按钮状态**：发布后按钮变为「✅ 已发布」绿色禁用态
- **路线状态指示**：TopAppBar 副标题显示「草稿」/「已发布」
- `RouteRepository` 新增 `deleteRoute()`, `reindexSteps()` 方法
- `RouteDao` 新增 `deleteRouteVersion()`, `reindexStep()` 查询

---

## [0.2.0] — 2026-05-27

### Added — Epic C: AI 任务创建
- **LLM 解析器接入**：CreateTaskScreen 自动检测 API Key 配置，有则用 LLM 解析，无则用规则解析
- **解析器状态标签**：创建任务页右上角显示 `AI: gpt-4o-mini`（LLM 模式）或 `规则解析`（规则模式）
- **CoreBridgeManager.configureLlm()** 实现：运行时更新 LLM 解析器配置
- **设置联动**：模型配置页保存的 API Key/URL/Model 自动同步到任务解析

### Changed
- `CreateTaskScreen` 新增 `settingsRepo` 参数，实时读取 LLM 配置
- `Navigation` 传递 `settingsRepo` 到 `CreateTaskScreen`

---

## [0.1.5] — 2026-05-27

### Changed
- **SH 模式状态区分** 🔴：不再显示"Core 运行中"，改为"基础模式"黄色状态
- **功能徽章**：首页显示 `✅ 路线执行` 和 `⚠️ 需无线调试` 两个能力徽章
- **SH 模式说明卡片**：显示"基础模式 · 路线执行可用"，明确说明录制需要无线调试或 Root
- **CoreStatus 新增 `ShellOnly` 状态**：区分 SH 模式（基础执行）和 ADB/Root（完全控制）
- **DeviceStatusChecker**：新增 `canRecord`/`canExecute`/`shellAvailable` 字段
- **CoreControlScreen**：SH 模式显示"SH 模式 · 执行可用·录制不可用"
- **PermissionDoctor**：SH 模式显示为 WARN 状态
- **DeviceInfoScreen**：SH 模式显示"基础模式"，UI dump 按钮在 SH 模式下可用

---

## [0.1.4] — 2026-05-26

### Fixed
- **截图路径 SH 模式不兼容** 🔴：`SenseEngine.screenshot()` 和 `dumpHierarchy()` 改用 app 内部缓存目录，SH 模式下不再因 `/sdcard/` 写权限失败
- **启动应用命令** 🔴：`launchApp()` 从 `monkey` 改为 `am start`，SH 模式兼容
- **文本输入过度转义** 🔴：`InputEngine.inputText()` 简化转义逻辑，不再错误转义 shell 特殊字符导致输入异常
- **clearText 无效** 🔴：从发送无用的 CTRL keycode 改为 HOME+END+DEL 组合键
- **key 类型步骤无法执行** 🟡：`executeStepAction` 处理 `key` 类型时新增 key name → keycode 映射（BACK→4, HOME→3 等）
- **无效包名检查** 🟡：移除 `executeRoute()` 中无意义的 `com.android.shell` 包名检查
- **Local ADB 端口错误** 🟡：移除对 ADB server 端口 5037 的无效尝试

---

## [0.1.3] — 2026-05-26

### Fixed
- **StepDetailPanel 状态串数据** 🔴：切步骤时 `remember` 不重置导致旧数据覆盖新步骤。所有关键状态改用 `remember(step.stepId)`
- **saveFromDraft 无事务保护** 🔴：先删后插改为 `@Transaction` 原子操作，防止崩溃时数据全丢
- **taskName 传 taskId** 🔴：TaskDetailScreen 打开 RouteStudio 时标题显示 taskId 而非任务名
- **AI 试跑步骤全部失败** 🟡：locatorValue 为空+type=summary。改为推断步骤类型
- **录制加载竞态** 🟡：固定 1 秒 delay 改为轮询最长 10 秒，低端设备不再丢数据
- **编辑按钮空操作**：显示"编辑功能即将上线"提示
- **截图 IO 线程设 State**：改用 `withContext(IO)` + 主线程设值
- **放弃路线文件残留**：同时清理 Room DB + RouteDraftStore 文件

---

## [0.1.0] — 2026-05-26

### Added
- **路线学习结果页** (#D02): 录制完成后展示真实路线步骤摘要，支持查看步骤类型、定位方式、风险等级
- **路线持久化到 Room DB** (#D04): `RouteRepository.saveFromDraft()` 将录制产出的 RouteDraft 转为 RouteVersionEntity + RouteStepEntity 存入数据库
- **路线复用执行** (#D05): Route Studio 支持完整路线测试和单步测试，通过 InputEngine/SenseEngine 真实执行 tap/swipe/input/back/home 等操作
- **步骤截图** (#D03): `ScreenshotManager` 通过 ADB screencap 捕获屏幕，Route Studio 步骤详情面板展示真实截图
- **AI 试跑路线保存**: TrialRunScreen 完成后自动保存 AI 执行步骤到 Room DB
- `RouteDao.getAllRoutes()` 用于首页路线列表

### Changed
- `RouteLearningResultScreen` 改为从 Room DB 加载路线数据（routeId 驱动），支持类型/定位/风险 Pill 展示
- `RouteRepository` 新增 `saveFromDraft()`, `saveFromTrialSteps()` 方法
- `RouteStudioScreen` 重构：截图自动捕获、真实路线测试、发布按钮可用
- Navigation 链路修复：录制/试跑→DB 保存→携带 routeId 跳转学习结果页

### Fixed
- 修复路线学习结果页 `steps` 永远为 `emptyList()` 的断链 bug
- 修复 Navigation 中 TrialStepStatus 未导入的编译错误

---

## [0.0.2] — 2026-05-26

### Fixed
- **Core ADB 重连死循环** (#4): 修复 `useDirectBridge()` 中 `resetCache()` 断开刚建立的 ADB 连接导致重连失败
- **诊断项状态不一致** (#5): `DeviceStatusChecker` 不再将 SH 模式错误标记为"ADB Shell 已连接"
- **试跑闪退** (#6): 移除 `TrialRunScreen` 中未使用的 `org.json.JSONObject` 导入。修复 `buildTaskPayload()` 中 `execution.mode` 空安全导致 NPE

### Changed
- `CoreBridgeManager.useDirectBridge()` 不再调用 `resetCache()`，保留已建立的连接
- 新增 `CoreBridgeManager.forceResetAndRefresh()` 用于需要真正重置的场景
- `DeviceStatusChecker` 新增 `sh` 模式支持，`adbConnected` 仅表示真正 ADB 连接

---

## [0.0.1] — 2026-05-25

### Added
- Core 引擎：支持 ADB TLS / Root / SH 三种 Shell 模式
- 手动录制功能：通过 getevent 捕获触摸事件
- 手势识别：Tap、LongPress、Swipe、Key
- 路线保存与加载（RouteDraftStore）
- SafeJson：无 org.json 依赖的 JSON 构建工具
- Debug 日志系统（CrashLog 持久化）
- 试跑模式（TrialRunScreen）
- 录制 UI（ManualRecordingScreen + RecordingOverlayService）

### Fixed
- org.json 导致部分真机 Native Crash → 替换为 StringBuilder JSON
- SH 模式无法录制时无提示 → 添加警告提示和 Toast 错误消息
- RawInputParser 触摸事件解析时序错误 → 改为 SYN_REPORT 时发出事件
