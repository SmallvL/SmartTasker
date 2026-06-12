# SmartTasker 开发进度

> ⚠️ **第一原则（2026-06-12 确立）**：本项目以 GitHub `SmallvL/SmartTasker` 仓库为唯一权威上游。
> 本地旧项目 `D:\1_AIagnet\SMARTTASK\android\` 已 DEPRECATED（Java/View 栈，与本项目 Kotlin/Compose 不同），不再维护。
> 详见 [PRODUCT_DIRECTION.md](./PRODUCT_DIRECTION.md)。

## 产品定位

**手机超级管家** — 类似超级 Siri / 超级小爱，一句话完成复杂任务。

用户用自然语言描述需求（如"每天早上9点打开企业微信打卡"），SmartTasker 自动解析为可执行的操作序列，并在指定条件下自动执行。

### 核心价值
- **零门槛**：不需要技术知识，说人话就行
- **全自动**：创建后无需干预，定时/触发自动执行
- **可信赖**：安全策略 + 失败诊断 + 运行统计

---

## 当前版本: v0.9.7

---

## Phase 1-4: 基础能力 ✅ (v0.5.0 - v0.8.0)

- 核心稳定性（重试、截图验证、日志）
- 智能触发（通知、条件、变量）
- 路线编辑器（ViewModel、UI、导入导出）
- 数据统计（成功率、趋势、排名）

---

## Phase 5: 产品打磨 ✅ (v0.9.x)

### ✅ 已完成

**对话框与交互 (v0.9.3)**
- 对话框关闭优化（Box 覆盖层方案，支持点击外部/取消/保存关闭）
- 路线回放运行记录保存
- Trace Explainer 失败诊断
- 路线编辑器交互动画优化

**导航修复 (v0.9.4)**
- 首页失败任务点击导航到 Trace Explainer

**自然语言解析 (v0.9.5)**
- 应用数据库扩展：70+ 应用（购物/社交/娱乐/工具/出行）
- 英文应用名支持：wechat → 微信，douyin → 抖音
- 时间模式增强：am/pm、every morning/day
- 风险关键词扩展：投资理财、密码修改等
- 任务名称生成优化

**首页卡片交互 (v0.9.6)**
- 应用图标占位符（首字母）
- 触发信息增强（⏰定时/🔔通知/▶️手动）
- 重复周期显示（每天/每周/一次）
- 风险等级指示器（⚠️高风险/🚫禁止）
- 长按菜单操作（暂停/启用/删除）
- SmartCard 组件支持 onLongClick

**执行状态反馈 (v0.9.7)**
- 首页执行状态卡片（任务执行中/正在提交）
- 执行阶段显示（🔍分析/🚀启动/🧭导航/👆操作/✅验证）
- 进度条实时更新

**Bug 修复 (v0.9.8)**
- 🔴 修复 SH 模式录制无提示（Issue #1）：`RecordingOverlayService` 在 `startForeground()` 之前先做 `canRecord()` 预检；SH 模式失败时直接 Toast + 广播 `ACTION_RECORD_START_FAILED` 后 `stopSelf()`；`ManualRecordingScreen` 注册 `BroadcastReceiver` 接收失败广播，撤销乐观状态
- 🔴 修复 org.json 重复依赖（Issue #2）：`app/build.gradle.kts` 中 `org.json:json:20231013` 声明两次，删除重复一处

**测试框架 (v0.9.8)**
- 单元测试从 13 扩到 80+ 覆盖：`core/parser/`, `core/retry/`, `core/protocol/`, `core/record/parser/`, `core/record/gesture/`, `core/direct/`
- Espresso 集成测试：`MainActivityTest`, `BottomNavigationTest`, `RecordingOverlayServiceTest`
- CI (`.github/workflows/test.yml`)：unit-test 在 ubuntu-latest；android-test 在 self-hosted MuMu runner
- 测试手册：`.github/TESTING.md`

### 📋 后续优化
- LLM 解析集成（需配置 API Key）
- 更多 UI 动画效果
- 性能优化

---

## 版本历史

| 版本 | 日期 | 功能 |
|------|------|------|
| v0.9.8 | 2026-06-13 | SH 录制无提示修复 + org.json 重复依赖修复 + 测试框架 |
| v0.9.7 | 2026-05-29 | 任务执行实时状态反馈 + 草稿状态修复 |
| v0.9.6 | 2026-05-29 | 首页任务卡片交互优化 + 长按菜单 |
| v0.9.5 | 2026-05-29 | 自然语言解析增强（70+应用/英文支持） |
| v0.9.4 | 2026-05-29 | 首页失败任务导航修复 |
| v0.9.3 | 2026-05-29 | 对话框关闭优化（Box覆盖层方案） |
| v0.9.2 | 2026-05-28 | 路线编辑器交互动画优化 |
| v0.9.1 | 2026-05-28 | 修复路线回放不保存运行记录 |
| v0.9.0 | 2026-05-28 | Trace Explainer 失败诊断增强 |
| v0.8.4 | 2026-05-28 | 任务管理 Bug 修复 |
| v0.8.3 | 2026-05-28 | 路线编辑器 5 个关键 Bug 修复 |
| v0.8.0 | 2026-05-28 | Phase 4 - 数据统计 |
| v0.7.0 | 2026-05-27 | Phase 3 - 路线编辑器 |
| v0.6.0 | 2026-05-26 | Phase 2 - 智能触发系统 |
| v0.5.0 | 2026-05-26 | Phase 1 - 核心稳定性增强 |
| v0.4.0 | 2026-05-25 | Epic F - 定时执行、数据库导入 |
| v0.3.0 | 2026-05-25 | ADB_LOCAL 流式录制 |
| v0.2.0 | 2026-05-25 | SH模式状态区分 |
