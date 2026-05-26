# 更新日志

本文件记录 SmartTasker 所有重要变更。

版本号遵循语义化版本规范：
- **Major.Minor.Patch** (X.Y.Z)
- Patch: bug 修复、小改动 (0.0.1→0.0.2)
- Minor: 新功能、大改动 (0.1.0→0.2.0)
- Major: 架构变更、重大重构 (1.0.0→2.0.0)

编号不限制十进制，允许 `1.101.65` 等形式。

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
