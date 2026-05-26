# 11_AGENT_TODOLIST_PLAN：开发与 AutoLXB 移植执行计划

> 文档版本：v0.3  
> 适用对象：Coding Agent、Android 工程师、AI 工程师、测试 Agent、项目管理 Agent  
> 目标：将 SmartTask AI 从产品方案推进到可运行 MVP。  
> 核心原则：先复用 AutoLXB 跑通闭环，再做产品增强；每一步必须有完成标准和测试标准。

---

## 0. Agent 使用说明

本文件是执行型 TODO/PLAN，不是概念设计文档。Agent 应优先按本文顺序推进任务。

### 0.1 推荐读取顺序

```text
00_AGENT_README_总览.md
 -> 11_AGENT_TODOLIST_PLAN.md
 -> 03_TECH_ARCH_AUTOLXB_INTEGRATION.md
 -> 06_DATA_SCHEMA_AND_API_CONTRACTS.md
 -> 04_ROUTE_STUDIO_SPEC.md
 -> 02_UI_UX_OPENAI_STYLE_GUIDE.md
 -> 09_TEST_ACCEPTANCE_RISK.md
```

### 0.2 执行约束

- 不要一开始重写 AutoLXB 核心执行器。
- 不要先做模板市场、云同步、复杂流程 IDE。
- 先跑通 AutoLXB 原项目，再封装 Core Bridge。
- 所有移植改动必须有回滚方式。
- UI 改造必须遵循现代简约、低噪声、卡片化、对话式创建原则。
- Route Studio 必须保留人工编辑能力，AI 只做建议，不自动覆盖用户编辑。
- 所有高风险动作必须经过 Safety Guard 判断。
- 每个开发任务必须同时提交：实现代码、测试记录、验收截图/日志。

### 0.3 工作流状态

```text
Backlog
 -> Ready
 -> In Progress
 -> Blocked
 -> Code Review
 -> QA Testing
 -> Done
```

### 0.4 Definition of Done

任意任务标记 Done 前必须满足：

```text
[ ] 功能完成
[ ] 无明显崩溃
[ ] 日志可追踪
[ ] 关键异常有用户提示
[ ] 至少完成自测
[ ] 如果涉及 UI，需要有截图
[ ] 如果涉及 Core/Route/Trace，需要提供运行日志
[ ] 如果涉及安全动作，需要通过 Safety Guard 测试
[ ] 文档或接口变更已同步
```

---

## 1. 总体开发路线

MVP 开发分为 8 个阶段：

```text
Phase 0：项目准备与基线确认
Phase 1：AutoLXB 原项目跑通
Phase 2：AutoLXB 移植与 Core Bridge 封装
Phase 3：新产品壳与现代简约 UI 框架
Phase 4：AI Task Wizard 自然语言创建任务
Phase 5：路线学习、Route 数据适配与路线版本管理
Phase 6：Route Studio MVP 人工编辑器
Phase 7：Safety / Permission / Cost / Trace 诊断
Phase 8：内测任务集、验收、打包发布
```

### 1.1 推荐里程碑

| 里程碑 | 目标 | 预计周期 | 产出 |
|---|---|---:|---|
| M0 | 项目准备完成 | 0.5 周 | 环境、仓库、任务看板 |
| M1 | AutoLXB 原版跑通 | 1 周 | 真机运行、route/trace 样本 |
| M2 | Core Bridge 可用 | 1 周 | 新 App 可调用 AutoLXB Core |
| M3 | 新 UI 壳可运行 | 1 周 | 首页、任务列表、设置页 |
| M4 | AI 创建任务闭环 | 1 周 | 自然语言 -> TaskSpec -> 试跑 |
| M5 | 路线学习与保存 | 1 周 | RouteVersion v1.0 |
| M6 | Route Studio 可编辑 | 1.5 周 | 删除、禁用、改点击点、单步测试 |
| M7 | 安全与诊断可用 | 1 周 | Safety、Permission、Trace Explainer |
| M8 | MVP 验收通过 | 0.5 周 | 内测包、测试报告、已知问题 |

### 1.2 关键优先级

```text
AutoLXB 原版跑通
 > CoreBridge
 > route/trace fixture
 > 新 UI 壳
 > AI Task Wizard
 > Route Studio
 > Safety/Trace/Permission
 > MVP 验收
```

原因：如果 AutoLXB 原版无法稳定跑通，后续 UI、AI 创建、Route Studio 都无法被真实验证。

---

## 2. Phase 0：项目准备与基线确认

### 2.1 目标

建立可执行开发环境，明确 AutoLXB fork 策略和 SmartTask AI 项目结构。

### 2.2 TODO

```text
[ ] 创建 SmartTask AI 主仓库
[ ] fork AutoLXB 到组织仓库或 vendor 子目录
[ ] 确认 License 与第三方声明
[ ] 建立 Android 开发环境
[ ] 准备至少 2 台真机
[ ] 准备 OpenAI-compatible VLM/LLM endpoint
[ ] 建立 Issue 看板
[ ] 建立 docs 目录并放入本文档包
[ ] 建立 branch 策略
[ ] 建立基础 CI：编译检查
```

### 2.3 推荐仓库结构

```text
smarttask-ai/
├── android/
│   ├── app/                         # SmartTask AI 产品 App
│   ├── core-bridge/                 # 与 lxb-core 通信的适配层
│   ├── route-studio/                # 路线编辑 UI/逻辑模块
│   └── shared/
├── vendor/
│   └── AutoLXB/                     # AutoLXB fork 或 submodule
├── docs/
│   └── smarttask_ai_docs_v0_3/
├── scripts/
├── test-fixtures/
│   ├── traces/
│   ├── routes/
│   └── screenshots/
└── README.md
```

### 2.4 完成标准

```text
[ ] 开发者可以 clone 仓库并完成 Android 项目同步
[ ] AutoLXB 源码已纳入 vendor 或 fork 管理
[ ] docs 文档可在仓库中直接阅读
[ ] 至少一台真机完成 USB debugging / Wireless debugging 设置
[ ] 模型 endpoint 可用，支持文本与图像输入
[ ] Issue 看板包含 Phase 1 的全部任务
```

### 2.5 测试完成标准

```text
[ ] CI 能跑一次 Gradle sync 或 assembleDebug
[ ] 真机能被 adb devices 识别
[ ] 模型 endpoint 用 curl 或脚本测试通过
[ ] 开发者能打开 AutoLXB Android 工程
```

---

## 3. Phase 1：AutoLXB 原项目跑通

### 3.1 目标

在不改动核心代码的前提下跑通 AutoLXB 原始闭环，拿到真实 route / trace 样本。

### 3.2 TODO

```text
[ ] clone / sync AutoLXB 原始代码
[ ] 编译 android/LXB-Ignition
[ ] 安装原版 APK 到真机
[ ] 配置 Root 或 Wireless ADB 启动路径
[ ] 启动 lxb-core
[ ] 配置模型 API URL / Key / Model
[ ] 执行快速任务：打开系统设置
[ ] 执行快速任务：打开某 App 并点击固定入口
[ ] 执行一个定时任务
[ ] 执行一个通知触发任务，若条件暂不满足可记录阻塞原因
[ ] 导出或定位 route 文件
[ ] 导出或定位 trace 日志
[ ] 整理 route / trace 样本到 test-fixtures
```

### 3.3 建议测试任务

| 编号 | 任务 | 目的 |
|---|---|---|
| A1 | 打开系统设置进入 Wi-Fi 页面 | 验证基础启动与点击 |
| A2 | 打开浏览器进入书签/首页 | 验证 App 启动和页面识别 |
| A3 | 打开淘宝进入“我的淘宝”但不做敏感操作 | 验证第三方 App UI 路径 |
| A4 | 创建每天固定时间打开 App 的任务 | 验证 schedule |
| A5 | 收到指定通知后打开 App | 验证 notification trigger |

### 3.4 需要记录的信息

```text
[ ] lxb-core 启动方式
[ ] App 与 core 通信端口
[ ] 提交任务的协议或入口
[ ] route 存储位置
[ ] trace 存储位置
[ ] route step 字段
[ ] trace event 字段
[ ] screenshot 文件位置
[ ] 任务失败时的 trace 表现
[ ] 任务成功时的 route 表现
```

### 3.5 完成标准

```text
[ ] 原版 AutoLXB APK 能在真机启动
[ ] lxb-core 状态可确认
[ ] 至少 2 个快速任务执行成功
[ ] 至少 1 条 route 被生成或保存
[ ] 至少 1 个 trace 日志被导出
[ ] route 与 trace 样本已放入 test-fixtures
[ ] 已输出 AutoLXB runtime 调研记录
```

### 3.6 测试完成标准

```text
[ ] A1 测试通过：系统设置任务成功
[ ] A2 或 A3 测试通过：第三方 App 页面任务成功
[ ] 重复运行已保存 route 至少 3 次
[ ] 记录每次是否调用模型
[ ] 记录失败截图和 trace
```

### 3.7 阻塞处理

如果无法跑通：

```text
[ ] 记录设备型号、Android 版本、ROM
[ ] 记录 adb / root 状态
[ ] 记录 lxb-core 日志
[ ] 记录模型 endpoint 返回
[ ] 尝试切换简单任务
[ ] 尝试更换真机
[ ] 不要直接进入新产品开发，必须先解决或明确替代方案
```

---

## 4. Phase 2：AutoLXB 移植与 Core Bridge 封装

### 4.1 目标

将 AutoLXB 能力从原始 UI 中解耦，封装成 SmartTask AI 可调用的 Core Bridge。

### 4.2 移植策略

优先采用：

```text
保留 AutoLXB lxb-core
保留 Route-Then-Act FSM
保留底层设备控制
新增 SmartTask AI App 层
通过 Adapter 调用原 core 能力
```

不要一开始改写：

```text
lxb-core 核心状态机
设备触摸注入逻辑
截图/XML/Accessibility 采集逻辑
Wireless ADB 启动逻辑
```

### 4.3 Core Bridge 接口清单

需要封装以下能力：

```text
[ ] getCoreStatus()
[ ] startCore()
[ ] stopCore()
[ ] submitQuickTask(taskPayload)
[ ] submitScheduleTask(taskPayload)
[ ] submitNotificationTask(taskPayload)
[ ] getTaskStatus(taskId)
[ ] getLatestTrace(taskId)
[ ] getLatestRoute(taskId)
[ ] saveRoute(taskId, route)
[ ] updateRouteStep(taskId, step)
[ ] runRoute(taskId, routeVersion)
[ ] runSingleStep(taskId, stepId)
[ ] cancelRunningTask(taskId)
```

### 4.4 TODO

```text
[ ] 阅读 AutoLXB App 与 lxb-core 通信实现
[ ] 定位 LXB-Link / TCP client 代码
[ ] 抽象 CoreBridge interface
[ ] 实现 AutoLxbCoreBridge
[ ] 实现 CoreStatus 数据结构
[ ] 实现 TaskPayload 数据结构
[ ] 实现 RouteRaw 数据结构
[ ] 实现 TraceRaw 数据结构
[ ] 写 mock bridge 便于 UI 开发
[ ] 写 bridge 单元测试
[ ] 写真机 integration test
```

### 4.5 完成标准

```text
[ ] SmartTask AI App 不依赖原 AutoLXB UI 也能检查 core 状态
[ ] SmartTask AI App 能提交一个 quick task
[ ] SmartTask AI App 能读取任务状态
[ ] SmartTask AI App 能读取 latest route
[ ] SmartTask AI App 能读取 latest trace
[ ] 所有 bridge 方法失败时有明确错误码
```

### 4.6 测试完成标准

```text
[ ] Mock bridge 单测通过
[ ] 真机 getCoreStatus 测试通过
[ ] 真机 submitQuickTask 测试通过
[ ] 真机 getLatestTrace 测试通过
[ ] 真机 getLatestRoute 测试通过
[ ] Core 未启动时，App 不崩溃，并提示启动 core
[ ] 模型配置错误时，App 能显示模型不可用
```

### 4.7 错误码建议

```text
CORE_NOT_RUNNING
CORE_START_FAILED
ADB_NOT_AVAILABLE
ROOT_NOT_AVAILABLE
MODEL_NOT_CONFIGURED
MODEL_REQUEST_FAILED
TASK_SUBMIT_FAILED
TASK_TIMEOUT
ROUTE_NOT_FOUND
TRACE_NOT_FOUND
PERMISSION_MISSING
UNKNOWN_ERROR
```

---

## 5. Phase 3：新产品壳与现代简约 UI 框架

### 5.1 目标

建立 SmartTask AI 自己的现代简约 UI 与操作逻辑，避免原 AutoLXB 工具感过强。

### 5.2 UI 原则

```text
[ ] 首屏突出一句话创建任务
[ ] 减少复杂表单
[ ] 使用卡片承载状态与操作
[ ] 使用自然语言解释底层概念
[ ] 高级配置折叠到二级页面
[ ] 错误提示给出下一步动作
[ ] 保持低噪声、留白充足、按钮少而明确
```

### 5.3 MVP 页面 TODO

```text
[ ] 首页
[ ] Core 状态卡
[ ] 一句话创建任务入口
[ ] 任务列表页
[ ] 任务详情页
[ ] 模型配置页
[ ] 权限体检页
[ ] 执行记录页
[ ] Trace 详情页
[ ] 安全确认弹窗
[ ] 设置页
```

### 5.4 首页完成标准

```text
[ ] 显示 Core 当前状态
[ ] 显示一句话创建输入框
[ ] 显示最近任务
[ ] 显示今日执行概览
[ ] Core 未运行时提示用户启动
[ ] 权限异常时提示进入权限体检
```

### 5.5 测试完成标准

```text
[ ] Core 运行中/未运行两种状态展示正确
[ ] 输入自然语言后能进入任务创建流程
[ ] 页面在主流屏幕尺寸下不遮挡
[ ] 深色/浅色模式至少有一个完成且可用
[ ] 弱网/模型未配置时不阻塞首页展示
```

---

## 6. Phase 4：AI Task Wizard 自然语言创建任务

### 6.1 目标

用户输入一句话，系统生成可确认、可修改、可试跑的 TaskSpec。

### 6.2 TODO

```text
[ ] 定义 TaskSpec schema
[ ] 获取本机 App 列表
[ ] 实现 AppResolver：App 名称 -> 包名
[ ] 设计 Task Wizard prompt
[ ] 实现 LLM 调用与 JSON schema 校验
[ ] 实现任务草稿确认页
[ ] 支持用户修改任务名称
[ ] 支持用户修改目标 App
[ ] 支持用户修改触发方式
[ ] 支持用户查看风险等级
[ ] 支持从草稿启动首次试跑
```

### 6.3 TaskSpec 最小字段

```text
[ ] task_id
[ ] name
[ ] description
[ ] target_app.name
[ ] target_app.package
[ ] trigger.type
[ ] trigger.params
[ ] execution.mode
[ ] risk.level
[ ] risk.requires_confirmation
[ ] status
```

### 6.4 完成标准

```text
[ ] 输入“每天早上9点打开淘宝收金币”能生成 schedule task 草稿
[ ] 输入“收到微信通知后打开微信查看”能生成 notification task 草稿
[ ] 输入“打开设置进入 Wi-Fi”能生成 quick task 草稿
[ ] 模型输出非法 JSON 时有重试或错误提示
[ ] App 包名低置信度时要求用户手动选择
[ ] 高风险任务会被标记并展示原因
```

### 6.5 测试完成标准

准备至少 20 条自然语言用例：

```text
[ ] 10 条低风险任务解析正确率 >= 80%
[ ] 5 条通知触发任务解析正确率 >= 70%
[ ] 5 条高风险任务风险识别正确率 >= 90%
[ ] JSON schema 校验覆盖非法输出
[ ] AppResolver 对已安装 App 命中率 >= 90%
```

### 6.6 高风险测试样例

```text
[ ] “收到老板微信后自动回复收到” -> high，需要确认
[ ] “帮我自动转账给张三” -> forbidden
[ ] “帮我提交订单” -> high，需要确认
[ ] “帮我删除相册里的截图” -> high，需要确认
[ ] “每天打开淘宝签到” -> low
```

---

## 7. Phase 5：路线学习、Route 数据适配与路线版本管理

### 7.1 目标

首次试跑成功后，将 AutoLXB route 转换为 SmartTask AI 的 RouteVersion / RouteStep，并可保存、复用、回滚。

### 7.2 TODO

```text
[ ] 分析 AutoLXB route 原始结构
[ ] 定义 RouteRaw -> RouteStep 转换器
[ ] 定义 screenshot_ref 映射
[ ] 提取 step summary
[ ] 提取 locator 信息
[ ] 提取 coordinate fallback
[ ] 提取 risk level
[ ] 创建 RouteVersion v1.0
[ ] 保存 RouteVersion 到本地数据库
[ ] 将 RouteVersion 与 TaskSpec 关联
[ ] 支持发布路线
[ ] 支持回滚路线
[ ] 支持读取当前 published route 执行
```

### 7.3 RouteStep 转换规则

```text
[ ] tap 动作转换为 type=tap
[ ] input 动作转换为 type=input
[ ] swipe 动作转换为 type=swipe
[ ] back 动作转换为 type=back
[ ] wait 动作转换为 type=wait
[ ] 如果存在 text/content-desc/resource-id，优先作为 primary locator
[ ] 如果只有坐标，primary=coordinate，并标记 stability=low
[ ] 如果涉及发送/删除/提交关键词，标记 risk >= high
```

### 7.4 完成标准

```text
[ ] 首次试跑后可以看到路线学习结果
[ ] 每一步有 summary、type、locator、截图引用
[ ] 坐标型步骤被标记为低稳定性
[ ] 高风险步骤被标记
[ ] 用户可以保存为 RouteVersion v1.0
[ ] 后续执行可以选择 published route
```

### 7.5 测试完成标准

```text
[ ] 使用 Phase 1 的至少 3 个 route fixture 做转换测试
[ ] 每个 fixture 转换后 step 数量一致或差异有解释
[ ] 截图引用可打开
[ ] 坐标点击步骤稳定性标记正确
[ ] 低风险任务保存路线后能复用执行 3 次
[ ] 回滚旧版本后执行使用旧版本
```

---

## 8. Phase 6：Route Studio MVP 人工编辑器

### 8.1 目标

实现用户可控的路径编辑能力，保证不是 AI 黑盒。

### 8.2 MVP 必做能力

```text
[ ] 查看步骤列表
[ ] 查看步骤截图
[ ] 高亮点击点
[ ] 查看 locator
[ ] 删除步骤
[ ] 禁用步骤
[ ] 修改步骤 summary
[ ] 修改等待时间
[ ] 修改点击目标
[ ] 单步测试
[ ] 保存为新草稿版本
[ ] 发布版本
[ ] 回滚版本
[ ] AI 路线解释
[ ] AI 稳定性建议
```

### 8.3 修改点击目标流程

```text
用户进入 Route Studio
 -> 选择 tap step
 -> 中间区域显示截图
 -> 用户点击新目标区域
 -> 系统读取候选控件/OCR/坐标
 -> 用户选择定位方式
 -> 保存到 draft route
 -> 单步测试
 -> 测试通过后发布
```

### 8.4 单步测试流程

```text
选择 step
 -> 检查当前设备状态
 -> 执行该 step
 -> 返回执行结果
 -> 展示成功/失败
 -> 记录测试 trace
```

### 8.5 TODO

```text
[ ] Route Studio 三栏布局
[ ] StepTimeline 组件
[ ] ScreenshotViewer 组件
[ ] LocatorEditor 组件
[ ] StepPropertyPanel 组件
[ ] AIAdvicePanel 组件
[ ] Step enable/disable
[ ] Step delete
[ ] Wait time editor
[ ] Tap target editor
[ ] Draft route 保存
[ ] Publish route
[ ] Rollback route
[ ] Single step test 调用 CoreBridge
```

### 8.6 完成标准

```text
[ ] 用户能打开任意任务的当前路线
[ ] 用户能看到每一步截图和操作说明
[ ] 用户能禁用步骤并保存草稿
[ ] 用户能删除步骤并保存草稿
[ ] 用户能修改等待时间
[ ] 用户能修改点击目标坐标
[ ] 用户能将点击目标改为文本/OCR 定位，若底层暂不支持则保存为候选并给出提示
[ ] 用户能单步测试
[ ] 用户能发布草稿为当前路线
[ ] AI 建议必须手动确认后才应用
```

### 8.7 测试完成标准

```text
[ ] 删除一个无效步骤后，路线 step 数减少且执行不崩溃
[ ] 禁用一个步骤后，执行时跳过该步骤
[ ] 修改等待时间后，route JSON 中对应字段变化
[ ] 修改点击坐标后，截图高亮位置变化
[ ] 单步测试成功时展示成功状态
[ ] 单步测试失败时展示失败原因
[ ] 发布新版本后，任务执行使用新版本
[ ] 回滚后，任务执行使用旧版本
```

### 8.8 不允许的行为

```text
[ ] AI 自动覆盖用户手动编辑
[ ] 修改后直接覆盖 published route
[ ] 删除步骤不可恢复
[ ] 高风险步骤绕过 Safety Guard
[ ] 单步测试失败但仍提示成功
```

---

## 9. Phase 7：Safety / Permission / Cost / Trace 诊断

### 9.1 Safety Guard

#### TODO

```text
[ ] 定义风险等级 L0/L1/L2/L3
[ ] 实现关键词风险识别
[ ] 实现动作类型风险识别
[ ] 实现 App 类型风险识别
[ ] 实现 SafetyDecision 数据结构
[ ] 实现高风险确认弹窗
[ ] 禁止自动执行支付/转账/贷款/账号注销
[ ] 对发送/删除/提交动作强制确认
```

#### 完成标准

```text
[ ] 发送消息类任务会弹出确认
[ ] 支付/转账类任务被拦截
[ ] 用户取消确认后任务停止
[ ] 用户允许一次后仅本次执行
[ ] SafetyDecision 被记录
```

#### 测试完成标准

```text
[ ] “自动转账”被标记 forbidden
[ ] “发送微信消息”被标记 high
[ ] “提交订单”被标记 high
[ ] “打开设置”被标记 low
[ ] 高风险确认弹窗不会被自动点击绕过
```

### 9.2 Permission Doctor

#### TODO

```text
[ ] 检查 Android 版本
[ ] 检查 adb 可用性
[ ] 检查 wireless debugging 状态
[ ] 检查 root 状态
[ ] 检查 lxb-core 状态
[ ] 检查通知读取权限
[ ] 检查无障碍服务
[ ] 检查电池无限制
[ ] 检查模型配置
[ ] 检查输入法配置
[ ] 输出修复建议
```

#### 完成标准

```text
[ ] 权限体检页能显示通过/警告/失败
[ ] 每个失败项有修复说明
[ ] Core 未运行时可跳转启动页
[ ] 模型未配置时可跳转模型配置页
```

#### 测试完成标准

```text
[ ] 手动关闭通知权限后能检测到
[ ] 手动清空模型 Key 后能检测到
[ ] Core 停止后能检测到
[ ] 电池策略异常能提示
```

### 9.3 Cost Monitor

#### TODO

```text
[ ] 记录每次模型调用
[ ] 记录调用任务 ID
[ ] 记录调用类型：text / vision
[ ] 记录成功/失败
[ ] 聚合每个任务最近 7 天模型调用次数
[ ] 在任务详情展示路线直接成功次数和模型兜底次数
```

#### 完成标准

```text
[ ] 每次 LLM/VLM 请求都能记录
[ ] 任务详情能看到模型调用次数
[ ] 路线复用成功时模型调用次数为 0 或接近 0
```

#### 测试完成标准

```text
[ ] 模型调用成功记录一条 usage
[ ] 模型调用失败也记录
[ ] 执行 route-only 任务不增加 vision usage
```

### 9.4 Trace Explainer

#### TODO

```text
[ ] 定义 TraceEvent 产品层结构
[ ] 解析 AutoLXB trace JSONL
[ ] 识别 task_start/task_end
[ ] 识别 route_success/route_failed
[ ] 识别 action_failed
[ ] 识别 model_failed
[ ] 生成失败摘要
[ ] 生成修复建议
[ ] 展示原始日志入口
```

#### 完成标准

```text
[ ] 运行记录能展示成功/失败
[ ] 失败任务能定位到 step
[ ] 失败任务能给出人话解释
[ ] 用户能从失败记录跳转 Route Studio
```

#### 测试完成标准

```text
[ ] 使用 5 条 trace fixture 测试解析
[ ] locator_not_found 能解释为控件未找到
[ ] model_error 能解释为模型调用失败
[ ] permission_error 能解释为权限问题
[ ] route_failed 后 vision_success 能生成修复建议
```

---

## 10. Phase 8：内测任务集、验收与打包发布

### 10.1 目标

通过一组可重复的低风险任务验证 MVP 可用性。

### 10.2 内测任务集

| 编号 | 任务 | 类型 | 风险 | 通过标准 |
|---|---|---|---|---|
| T01 | 打开设置进入 Wi-Fi 页面 | quick | low | 成功进入页面 |
| T02 | 打开浏览器进入首页 | quick | low | 成功打开 |
| T03 | 打开淘宝进入我的淘宝 | quick | low | 成功进入指定页面 |
| T04 | 每天固定时间打开某 App | schedule | low | 触发成功 |
| T05 | 打开 App 签到但不涉及支付 | schedule | low | 首次试跑成功，路线可复用 |
| T06 | 收到指定通知后打开 App | notification | low/medium | 通知匹配并触发 |
| T07 | 草拟微信回复但发送前确认 | notification | high | 弹确认，不自动发送 |
| T08 | 修改 route 等待时间后执行 | route edit | low | 新等待时间生效 |
| T09 | 禁用 route 某一步后执行 | route edit | low | 执行跳过该步 |
| T10 | route 失败后 Trace 解释 | diagnosis | low | 显示失败原因和建议 |

### 10.3 MVP 总验收标准

```text
[ ] T01-T05 至少 4 个通过
[ ] T06 可通过或记录明确阻塞原因
[ ] T07 必须通过安全确认
[ ] T08-T09 必须通过 Route Studio 编辑验证
[ ] T10 必须能展示 Trace 解释
[ ] App 连续运行 30 分钟无严重崩溃
[ ] 核心流程录屏完成
[ ] 已知问题列表完成
```

### 10.4 发布前检查

```text
[ ] 版本号更新
[ ] 权限声明检查
[ ] 第三方 License 检查
[ ] 默认模型配置为空，不内置敏感 Key
[ ] 高风险策略默认开启
[ ] Debug 日志不包含 API Key
[ ] 隐私说明包含截图/模型调用提示
[ ] Release APK 可安装
```

---

## 11. Agent 可执行任务清单

下面是可直接拆分给 coding agent 的任务格式。

---

### TASK-001：跑通 AutoLXB 原版快速任务

```yaml
id: TASK-001
title: 跑通 AutoLXB 原版快速任务
owner: Android Agent
phase: Phase 1
priority: P0
depends_on: []
inputs:
  - AutoLXB source
  - Android device
  - LLM/VLM endpoint
outputs:
  - run log
  - screenshot
  - route sample
  - trace sample
steps:
  - 编译 android/LXB-Ignition
  - 安装 APK
  - 启动 lxb-core
  - 配置模型
  - 执行“打开设置进入 Wi-Fi”
done:
  - APK 可运行
  - core 可启动
  - 任务执行成功
  - route/trace 已保存
tests:
  - 连续执行 3 次
  - 至少 2 次成功
  - 失败时记录日志
```

---

### TASK-002：实现 CoreBridge.getCoreStatus

```yaml
id: TASK-002
title: 实现 CoreBridge.getCoreStatus
owner: Android Agent
phase: Phase 2
priority: P0
depends_on:
  - TASK-001
outputs:
  - CoreBridge interface
  - AutoLxbCoreBridge implementation
  - Unit test
done:
  - 能区分 running/stopped/error
  - 错误包含 code/message
tests:
  - core 运行中返回 running
  - core 停止返回 stopped
  - 端口不可达返回 error
```

---

### TASK-003：实现 AI TaskSpec Parser

```yaml
id: TASK-003
title: 实现自然语言到 TaskSpec 解析
owner: AI Agent
phase: Phase 4
priority: P0
depends_on: []
outputs:
  - prompt
  - parser
  - schema validator
  - test cases
done:
  - 输出符合 TaskSpec schema
  - 非法 JSON 有错误处理
  - 高风险任务可识别
tests:
  - 20 条自然语言样例
  - 低风险解析准确率 >= 80%
  - 高风险识别准确率 >= 90%
```

---

### TASK-004：实现 RouteRaw 转 RouteStep

```yaml
id: TASK-004
title: 实现 AutoLXB RouteRaw 到 SmartTask RouteStep 转换
owner: Android Agent
phase: Phase 5
priority: P0
depends_on:
  - TASK-001
outputs:
  - RouteAdapter
  - fixture tests
done:
  - tap/input/swipe/wait/back 可转换
  - locator 可提取
  - screenshot_ref 可映射
  - 坐标型步骤标记低稳定性
tests:
  - 至少 3 个 route fixture
  - 转换后 step 可展示
```

---

### TASK-005：实现 Route Studio 步骤列表与截图预览

```yaml
id: TASK-005
title: Route Studio 步骤列表与截图预览
owner: Android UI Agent
phase: Phase 6
priority: P0
depends_on:
  - TASK-004
outputs:
  - RouteStudio screen
  - StepTimeline component
  - ScreenshotViewer component
done:
  - 能显示任务路线
  - 能选择步骤
  - 能显示对应截图
  - 能高亮点击点
tests:
  - 打开包含 5+ 步 route
  - 每一步截图正确显示
  - tap step 点击点高亮正确
```

---

### TASK-006：实现 Route Studio 删除/禁用步骤

```yaml
id: TASK-006
title: Route Studio 删除与禁用步骤
owner: Android UI Agent
phase: Phase 6
priority: P0
depends_on:
  - TASK-005
outputs:
  - delete step feature
  - disable step feature
  - draft route save
done:
  - 删除步骤只影响 draft
  - 禁用步骤执行时跳过
  - published route 不被直接覆盖
tests:
  - 删除后 step 数减少
  - 禁用后 step enabled=false
  - 发布前原路线仍可执行
```

---

### TASK-007：实现 Safety Guard 高风险确认

```yaml
id: TASK-007
title: Safety Guard 高风险动作确认
owner: Android/AI Agent
phase: Phase 7
priority: P0
depends_on:
  - TASK-003
outputs:
  - risk classifier
  - confirmation dialog
  - SafetyDecision record
done:
  - high 风险动作弹窗
  - forbidden 动作阻止
  - 用户取消后任务停止
tests:
  - 自动回复微信需要确认
  - 自动转账被禁止
  - 普通签到不弹确认
```

---

### TASK-008：实现 Trace Explainer

```yaml
id: TASK-008
title: Trace Explainer 失败原因解释
owner: AI/Android Agent
phase: Phase 7
priority: P0
depends_on:
  - TASK-001
  - TASK-004
outputs:
  - Trace parser
  - Failure classification
  - UI detail page
done:
  - 能解析 trace
  - 能定位失败步骤
  - 能生成用户可读解释
tests:
  - 5 个 trace fixture
  - locator_not_found 解释正确
  - model_error 解释正确
  - permission_error 解释正确
```

---

## 12. 每日/每周进度管理模板

### 12.1 每日同步模板

```text
日期：
负责人：

昨天完成：
- 

今天计划：
- 

阻塞：
- 

需要决策：
- 

风险：
- 
```

### 12.2 每周评审模板

```text
周次：
目标里程碑：

已完成：
- 

未完成：
- 

测试结果：
- 

关键问题：
- 

下周计划：
- 

是否需要调整范围：
- 
```

### 12.3 Bug 报告模板

```text
Bug 标题：
环境：
设备型号：
Android 版本：
App 版本：
任务 ID：
Route 版本：
复现步骤：
期望结果：
实际结果：
Trace 文件：
截图/录屏：
严重程度：P0/P1/P2/P3
```

---

## 13. 开发优先级总表

### P0：MVP 必须完成

```text
[ ] AutoLXB 原版跑通
[ ] CoreBridge
[ ] 首页
[ ] 一句话创建任务
[ ] TaskSpec Parser
[ ] 首次试跑
[ ] RouteAdapter
[ ] RouteVersion 保存
[ ] Route Studio 查看步骤
[ ] Route Studio 删除/禁用步骤
[ ] Route Studio 修改等待时间/点击目标
[ ] 单步测试
[ ] Safety Guard
[ ] Permission Doctor
[ ] Trace Explainer
[ ] 内测任务集
```

### P1：MVP 后增强

```text
[ ] 条件等待
[ ] 用户锁定步骤
[ ] AI 批量优化
[ ] 路线差异对比
[ ] 成功率统计
[ ] 模型成本估算
[ ] 模板本地导入
```

### P2：后续版本

```text
[ ] 社区模板市场
[ ] 多设备同步
[ ] 复杂条件分支
[ ] 循环
[ ] 插件系统
[ ] 云端控制台
```

---

## 14. 最小可交付 MVP 定义

MVP 不是“所有设想都实现”，而是必须能完整演示：

```text
1. 用户打开 App
2. 首页显示 Core 状态
3. 用户输入一句话任务
4. AI 生成 TaskSpec
5. 用户确认后首次试跑
6. 试跑成功后生成路线
7. 用户进入 Route Studio 查看步骤与截图
8. 用户禁用/修改一个步骤
9. 用户单步测试
10. 用户发布路线版本
11. 后续执行优先复用路线
12. 失败时能看到 Trace 解释
13. 高风险动作会被拦截或确认
```

只要以上 13 步可稳定演示，即可认为 MVP 具备继续迭代价值。

---

## 15. 给 Agent 的执行提示

当你作为 coding agent 处理任务时，请遵循：

```text
1. 先读本文件对应 Phase。
2. 明确当前任务依赖。
3. 不要越级实现 P1/P2 功能。
4. 每次改动尽量小。
5. 所有与 AutoLXB 的交互必须经过 Adapter。
6. 所有用户可见错误必须转成可理解提示。
7. 涉及路线修改时，默认写入 draft，不直接覆盖 published。
8. 涉及高风险动作时，必须调用 Safety Guard。
9. 任务完成后补充测试记录。
10. 如果底层能力不可用，记录阻塞，不要伪造实现。
```

---

## 16. 当前最优下一步

从现在开始，建议立即执行：

```text
[ ] TASK-001：跑通 AutoLXB 原版快速任务
[ ] TASK-002：实现 CoreBridge.getCoreStatus
[ ] TASK-003：实现 AI TaskSpec Parser 的离线 schema 与 prompt
[ ] TASK-004：收集 route/trace fixture
```

优先级排序：

```text
AutoLXB 原版跑通 > CoreBridge > Route/Trace fixture > 新 UI > AI Task Wizard > Route Studio
```

原因：

> 如果 AutoLXB 原版无法稳定跑通，后续所有产品层设计都无法验证。因此第一阶段不要急于做 UI 和模板市场，必须先确认底层执行能力、route 结构和 trace 结构。
