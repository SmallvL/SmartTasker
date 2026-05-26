# 更新日志

本文件记录 SmartTasker 所有重要变更。

版本号遵循语义化版本规范：
- **Major.Minor.Patch** (X.Y.Z)
- Patch: bug 修复、小改动 (0.0.1→0.0.2)
- Minor: 新功能、大改动 (0.1.0→0.2.0)
- Major: 架构变更、重大重构 (1.0.0→2.0.0)

编号不限制十进制，允许 `1.101.65` 等形式。

---

## [0.1.1] — 2026-05-26

### Added
- **截图点击点高亮** (#E02): 步骤截图叠加十字准星标记，显示点击坐标 (x,y)
- **定位编辑器** (#E05): 支持修改步骤定位策略（坐标/文本/资源ID/内容描述）和定位值

### Changed
- `StepDetailPanel` 重构：截图使用 Box 叠加 Canvas 绘制标记圈+准星+坐标标签
- 定位方式改为可编辑状态（locatorStrategy/locatorValue 本地状态 + FilterChip 选择器）

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
