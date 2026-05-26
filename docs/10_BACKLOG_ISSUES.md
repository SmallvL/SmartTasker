# 10. 开发 Backlog / Issue 拆解

> 项目：SmartTask AI / AI 安卓自动化任务产品  
> 版本：v0.2  
> 日期：2026-05-23  
> 底层参考：AutoLXB 二次开发

## 1. 使用方式

本文件可直接用于创建 GitHub Issues / Linear / Jira 任务。每个 Issue 包含：目标、范围、依赖、验收标准。

---

## Epic A：AutoLXB 技术摸底

### A01 编译并运行 AutoLXB

- 优先级：P0
- 标签：`area:core-bridge`, `needs-source-verification`
- 目标：在真机上完成 AutoLXB 编译、安装、启动。
- 验收：能启动 App 与 `lxb-core`。

### A02 验证 Quick Task 执行

- 优先级：P0
- 依赖：A01
- 验收：能提交一个简单任务并完成执行。

### A03 获取 Route 样例

- 优先级：P0
- 依赖：A02
- 验收：导出或读取一次成功任务的 raw route。

### A04 获取 Trace 样例

- 优先级：P0
- 依赖：A02
- 验收：读取 raw trace JSONL。

### A05 验证 Route 修改与回放

- 优先级：P0
- 依赖：A03
- 验收：修改 route 中一个简单 step 后可执行或得到明确失败原因。

---

## Epic B：App 基础壳

### B01 首页与底部导航

- 优先级：P0
- 标签：`area:ui`
- 范围：首页、任务、运行记录、设置四个入口。
- 验收：符合 `02_UI_UX_OPENAI_STYLE_GUIDE.md`。

### B02 Core 状态卡

- 优先级：P0
- 标签：`area:core-bridge`, `area:ui`
- 依赖：A01
- 验收：首页展示 Core running / stopped。

### B03 模型配置页

- 优先级：P0
- 标签：`area:ui`, `area:ai`
- 验收：可配置 endpoint、API key、model、vision model。

### B04 权限体检页

- 优先级：P0
- 标签：`area:permission`
- 验收：展示 Core、通知、模型、电池等状态。

---

## Epic C：AI 任务创建

### C01 自然语言输入页

- 优先级：P0
- 标签：`area:ui`, `area:ai`
- 验收：用户可输入任务描述。

### C02 App Resolver

- 优先级：P0
- 标签：`area:ai`
- 验收：可从本机 App 列表匹配目标 App。

### C03 TaskSpec Parser

- 优先级：P0
- 标签：`area:ai`
- 验收：按 `05` 输出合法 JSON。

### C04 任务草稿确认页

- 优先级：P0
- 标签：`area:ui`
- 验收：用户可确认/修改任务名、App、触发方式。

### C05 首次试跑入口

- 优先级：P0
- 标签：`area:core-bridge`
- 依赖：C03、A02
- 验收：确认后提交 quick task。

---

## Epic D：路线学习与展示

### D01 RouteAdapter v0.1

- 优先级：P0
- 标签：`area:route`, `area:core-bridge`
- 依赖：A03
- 验收：raw route 转 Product RouteVersion。

### D02 路线学习结果页

- 优先级：P0
- 标签：`area:ui`, `area:route`
- 验收：展示学到的步骤摘要。

### D03 步骤截图展示

- 优先级：P0
- 标签：`area:ui`, `area:route`
- 验收：选中步骤能看到截图。

### D04 保存 RouteVersion

- 优先级：P0
- 标签：`area:route`
- 验收：成功保存 route 到本地 DB。

### D05 路线复用执行

- 优先级：P0
- 标签：`area:route`, `area:core-bridge`
- 验收：已保存路线可再次执行。

---

## Epic E：Route Studio MVP

### E01 步骤时间线

- 优先级：P0
- 标签：`area:route`, `area:ui`
- 验收：展示所有 step 和状态。

### E02 点击点高亮

- 优先级：P0
- 验收：截图上显示原点击位置。

### E03 删除步骤

- 优先级：P0
- 验收：删除后保存草稿。

### E04 禁用步骤

- 优先级：P0
- 验收：禁用步骤不参与执行。

### E05 修改点击目标

- 优先级：P0
- 验收：可从截图或属性面板修改 locator。

### E06 修改等待时间

- 优先级：P0
- 验收：wait step timeout 可修改。

### E07 单步测试

- 优先级：P0
- 依赖：A05
- 验收：当前步骤能独立测试或用临时 route 测试。

### E08 保存草稿与发布版本

- 优先级：P0
- 验收：draft -> published 流程可用。

---

## Epic F：Trace 与诊断

### F01 TraceAdapter v0.1

- 优先级：P0
- 依赖：A04
- 验收：raw trace 转 RunRecord。

### F02 运行记录列表

- 优先级：P0
- 验收：展示成功/失败历史。

### F03 Trace 解释页

- 优先级：P0
- 验收：展示失败步骤、原因、建议。

### F04 AI Trace Explainer

- 优先级：P1
- 验收：AI 输出符合 `05` schema。

---

## Epic G：安全与成本

### G01 Safety Guard 策略表

- 优先级：P0
- 标签：`area:safety`
- 验收：L0-L3 风险策略实现。

### G02 高风险确认弹窗

- 优先级：P0
- 验收：发送、删除、提交前弹窗。

### G03 禁止支付/转账自动执行

- 优先级：P0
- 验收：L3 动作被阻断。

### G04 模型调用统计

- 优先级：P1
- 验收：RunRecord 记录 model_calls。

---

## Epic H：测试与发布

### H01 内测任务集

- 优先级：P0
- 验收：准备 10 个任务样例。

### H02 MVP 验收测试

- 优先级：P0
- 验收：按 `09` 完成测试报告。

### H03 崩溃与异常处理

- 优先级：P0
- 验收：Core 不运行、模型不可用、权限缺失时 App 不崩溃。

---

## 2. MVP P0 Issue 汇总

P0 最小闭环：

```text
A01-A05
B01-B04
C01-C05
D01-D05
E01-E08
F01-F03
G01-G03
H01-H03
```

完成以上任务后，可以进行 MVP 内测。
