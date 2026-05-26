# 08. 开发计划与进度管理

> 项目：SmartTasker / AI 安卓自动化任务产品  
> 版本：v1.0.0  
> 日期：2026-05-24  
> 底层参考：AutoLXB 二次开发 → 独立 DirectCoreBridge

---

## 0. 当前进度总览

> **更新时间：2026-05-24**

### 里程碑完成状态

| 里程碑 | 原计划 | 实际状态 | 说明 |
|---|---|---|---|
| M0 | 第 1 周：AutoLXB 技术摸底 | ✅ **已完成** | 确认独立实现 DirectCoreBridge，不依赖外部 lxb-core |
| M1 | 第 2 周：App 壳 + Core Bridge | ✅ **已完成** | App 可安装运行，Core 双模式可用，设备诊断完整 |
| M2 | 第 3 周：AI 任务创建闭环 | 🔶 部分完成 | TaskSpec parser 存在，LLM 未接线 |
| M3 | 第 4 周：路线学习与展示 | 🔶 骨架 | RouteAdapter 存在，UI 骨架 |
| M4 | 第 5 周：Route Studio MVP | 🔶 骨架 | 页面框架已搭建 |
| M5 | 第 6 周：安全、诊断、内测 | 🔶 部分完成 | 设备诊断已完成，Safety Guard 基础 |

### 进度指标

| 指标 | 当前值 | 目标 |
|---|---|---|
| M0 完成率 | 100% | 100% |
| M1 完成率 | 100% | 100% |
| M2 完成率 | ~40% | 100% |
| M3 完成率 | ~15% | 100% |
| M4 完成率 | ~10% | 100% |
| M5 完成率 | ~30% | 100% |
| 核心引擎可用性 | ✅ 可用 | 可用 |
| ADB 通信可用性 | ✅ 可用 | 可用 |
| 真机任务成功率 | 待测 | >80% |

### v1.0.0 已实现的完整功能列表

```
✅ DirectCoreBridge 独立自动化引擎
✅ ShellExecutor (Root + ADB 双模式)
✅ InputEngine (tap/swipe/longPress/inputText/pressKey)
✅ SenseEngine (screenshot/dumpHierarchy/launchApp)
✅ AdbPairingService (NSD 发现 + TLS 配对)
✅ WirelessAdbConnectionManager (RSA + X.509)
✅ AdbShellExecutor (TLS Shell)
✅ DeviceStatusChecker (6 项检查)
✅ 实时状态仪表盘 (CoreControlScreen)
✅ 启动自动重连
✅ Jetpack Compose + Material3 暗色主题
✅ 首页/任务/设置 底部导航
✅ 8+ 设置子页面
✅ Debug 日志查看器
✅ Permission Doctor (基础)
✅ Route Studio (骨架)
✅ AI 创建任务页面
✅ Trial Run 页面
✅ Room 数据库 + DataStore
✅ TaskExecutionService + 定时触发
```

---

## 1. 开发可行性反推

### v0.2 阶段的原始评估

| 维度 | 原结论 | 当前验证 |
|---|---|---|
| 产品范围 | 可以 | ✅ 已验证 |
| UI/UX 方向 | 可以 | ✅ 已实现 Compose + Material3 |
| Route Studio 交互 | 可以 | 🔶 骨架已搭建 |
| 数据结构 | 可以作为产品层基线 | ✅ Room + DataStore 已落地 |
| Core 接口 | 需要第 1 周验证后定稿 | ✅ DirectCoreBridge 接口已定稿 |
| 进度管理 | 可以 | ✅ 已跟踪到 v1.0.0 |
| 测试验收 | 可以 | 🔶 待端到端测试 |

### 关键决策

> **原计划复用 AutoLXB 的 lxb-core，实际确认为独立实现 DirectCoreBridge。这是一个正确的架构决策——避免了外部进程依赖，简化了部署和调试。**

---

## 2. 里程碑（已更新）

| 里程碑 | 状态 | 目标 | 实际交付物 |
|---|---|---|---|
| **M0** | ✅ 完成 | 技术摸底 | 确认独立实现方案，DirectCoreBridge 设计 |
| **M1** | ✅ 完成 | App 壳 + Core | 可运行 APK、双模式引擎、设备诊断、ADB 配对 |
| M2 | 🔶 部分 | AI 任务闭环 | TaskSpec parser、AI 创建页面（LLM 未接线） |
| M3 | 🔶 骨架 | 路线读写 | RouteAdapter 存在、UI 骨架 |
| M4 | 🔶 骨架 | Route Studio | 页面框架 |
| M5 | 🔶 部分 | 安全诊断 | 设备诊断已完成、Safety Guard 基础 |

---

## 3. 6 周计划（回顾与更新）

### 第 1 周：AutoLXB 技术摸底 ✅

原目标：确认底层可复用能力。

**实际结论**：确认不复用 lxb-core 进程，改为独立实现 DirectCoreBridge。这是一个关键架构决策，避免了外部进程依赖。

交付物：
- ✅ 技术验证报告（确认独立实现方案）
- ✅ DirectCoreBridge 设计稿
- ✅ ShellExecutor 接口定义

---

### 第 2 周：产品壳与 Core Bridge ✅

**实际完成**：
- ✅ 首页 UI (Core 控制面板)
- ✅ Core 状态卡 (DeviceStatusChecker)
- ✅ 模型配置页
- ✅ 权限体检页初版
- ✅ CoreBridge 接口 (DirectCoreBridge)
- ✅ 本地数据库初始化 (Room + DataStore)
- ✅ ADB 无线配对完整实现
- ✅ 设备诊断仪表盘

交付物：
- ✅ 可安装 APK
- ✅ 能展示 Core 状态
- ✅ 能通过 Root/ADB 执行操作

---

### 第 3 周：AI 任务创建 🔶

**实际完成**：
- ✅ 自然语言输入页
- 🔶 TaskSpec parser（存在但未接 LLM）
- ✅ 任务草稿确认页
- 🔶 风险初判（基础框架）
- ✅ 首次试跑入口 (Trial Run)

**未完成**：
- ❌ LLM 集成（真实 API 调用）
- ❌ App Resolver 完整实现
- ❌ 首次试跑端到端验证

---

### 第 4 周：路线学习与展示 🔶

**实际完成**：
- 🔶 RouteAdapter（存在但未完全接线）
- ✅ 路线学习结果页骨架

**未完成**：
- ❌ RouteAdapter raw route 转 Product RouteVersion
- ❌ TraceAdapter 完整实现
- ❌ 步骤列表完整展示
- ❌ 保存路线版本
- ❌ 路线复用执行

---

### 第 5 周：Route Studio MVP 🔶

**实际完成**：
- 🔶 Route Studio 页面骨架

**未完成**：
- ❌ 步骤详情编辑
- ❌ 点击点高亮
- ❌ 删除/禁用步骤
- ❌ 修改点击目标/等待时间
- ❌ 单步测试
- ❌ 保存草稿/发布版本

---

### 第 6 周：安全、诊断和内测 🔶

**实际完成**：
- ✅ DeviceStatusChecker（完整 6 项检查）
- ✅ 实时状态仪表盘
- 🔶 Permission Doctor（基础）
- 🔶 Safety Guard（基础框架）

**未完成**：
- ❌ 高风险确认弹窗完整策略
- ❌ Trace Explainer 完整实现
- ❌ ModelUsage 统计
- ❌ 内测任务集
- ❌ MVP 验收报告

---

## 4. 看板状态

建议使用以下状态：

```text
Backlog
Ready
In Progress
Code Review
QA Testing
Blocked
Done ✅
```

Blocked 必须注明：

- 阻塞原因。
- 负责人。
- 下一步动作。
- 预计解除条件。

---

## 5. Issue 标签

| 标签 | 含义 |
|---|---|
| `area:ui` | UI 页面 |
| `area:core` | DirectCoreBridge 引擎 |
| `area:adb` | ADB 无线配对 |
| `area:route` | 路线和 Route Studio |
| `area:ai` | AI Prompt / 解析 / LLM |
| `area:safety` | 安全风控 |
| `area:permission` | 权限体检 |
| `area:test` | 测试 |
| `priority:p0` | MVP 必须 |
| `priority:p1` | MVP 重要但可降级 |
| `blocked` | 阻塞 |
| `needs-source-verification` | 需要源码验证 |

---

## 6. Definition of Done

每个开发任务完成必须满足：

```text
- 功能可在真机上运行
- 有基础错误处理
- UI 符合 02 设计规范
- 数据写入本地 DB 或明确不需要
- 不绕过 Safety Guard
- 有测试步骤说明
- 有验收截图或录屏
- 不引入 P0 崩溃
```

---

## 7. 每周评审模板

```text
本周目标：
完成情况：
已完成 Issue：
未完成 Issue：
阻塞项：
风险变化：
下周目标：
需要产品决策：
需要技术决策：
```

---

## 8. 进度指标

| 指标 | 用途 | 当前值 |
|---|---|---|
| P0 Issue 完成率 | 判断 MVP 进度 | M0+M1 完成，M2-M5 进行中 |
| 真机任务成功率 | 判断技术可用性 | 待端到端测试 |
| Route 复用成功率 | 判断核心壁垒 | 待实现 |
| 模型调用次数 | 判断成本控制 | 待 LLM 集成 |
| 崩溃次数 | 判断稳定性 | 待统计 |
| 高风险确认覆盖率 | 判断安全性 | 基础框架已有 |

---

## 9. 版本策略（已更新）

```text
v0.1 原始单文档
v0.2 拆分文档包 + UI/UX + 进度管理
v0.5 导航修复，设置页面完善
v0.6 真实功能壳
v0.7 移除假数据
v0.8 内置 Core 引擎 (DirectCoreBridge)
v0.9 双模式 Core 启动 (ADB/Root)
v1.0 完整 ADB TLS 配对 + 诊断仪表盘 + 自动重连  ← 当前
v1.1 计划：LLM 集成 + AI 任务创建闭环
v1.2 计划：路线学习、保存与回放
v1.5 计划：Route Studio MVP
v2.0 计划：MVP 内测版（完整闭环）
```
