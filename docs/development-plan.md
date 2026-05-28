# SmartTasker 完整开发计划

## 📋 项目概述

**目标**：将 SmartTasker 从基础自动化工具升级为智能自动化平台，功能完整度从 40% 提升至 90%+

**技术栈**：Kotlin + Jetpack Compose + ADB + Room + Hilt

**开发周期**：12周（3个月）

---

## 🎯 功能优先级与开发阶段

### Phase 1: 核心稳定性与基础增强（第1-2周）

**目标**：确保现有功能稳定，补全基础能力

#### 1.1 错误重试机制
- **优先级**：🔴 高
- **技术方案**：
  - 在 `TaskExecutionService` 中添加重试逻辑
  - 支持配置重试次数（默认3次）、重试间隔（指数退避）
  - 记录重试日志到 `RunRecordEntity`
- **验收标准**：
  - 任务失败后自动重试，最多3次
  - 重试间隔：1s → 2s → 4s
  - 重试日志可在 Debug 页面查看

#### 1.2 屏幕截图验证
- **优先级**：🔴 高
- **技术方案**：
  - 使用 ADB `screencap` 命令截图
  - 在 `RouteStepEntity` 添加 `screenshotPath` 字段
  - 执行每步后自动截图保存
- **验收标准**：
  - 每步执行后自动截图
  - 截图保存到 `/sdcard/SmartTasker/screenshots/`
  - 可在路线详情页查看截图

#### 1.3 执行日志增强
- **优先级**：🟡 中
- **技术方案**：
  - 扩展 `TraceEventEntity` 记录详细信息
  - 添加步骤级别日志（开始/结束/耗时/结果）
  - 支持日志导出
- **验收标准**：
  - 每个步骤有详细日志
  - 可导出为 JSON 文件
  - 日志包含时间戳、操作类型、坐标、结果

---

### Phase 2: 智能触发与条件执行（第3-5周）

**目标**：实现智能触发和条件判断能力

#### 2.1 通知触发
- **优先级**：🔴 高
- **技术方案**：
  - 使用 `NotificationListenerService` 监听通知
  - 在 `TaskEntity` 添加 `notificationFilter` 字段（包名/关键词）
  - 创建 `NotificationTriggerService` 后台服务
  - 匹配到通知时自动触发任务
- **验收标准**：
  - 可配置监听的应用包名
  - 可配置关键词过滤（支持正则）
  - 通知匹配后自动执行任务
  - 支持白名单/黑名单模式

#### 2.2 条件执行
- **优先级**：🔴 高
- **技术方案**：
  - 创建 `ConditionEvaluator` 类
  - 支持条件类型：
    - 屏幕文字包含/不包含
    - 特定应用在前台
    - 时间范围
    - 电量水平
    - 网络状态
  - 在 `RouteStepEntity` 添加 `condition` 字段
- **验收标准**：
  - 每步可配置前置条件
  - 条件不满足时跳过或等待
  - 支持 AND/OR 逻辑组合

#### 2.3 变量/参数传递
- **优先级**：🟡 中
- **技术方案**：
  - 创建 `VariableManager` 类
  - 支持内置变量：`{SCREEN_TEXT}`, `{CURRENT_APP}`, `{TIME}`, `{DATE}`
  - 支持自定义变量：`{VAR_NAME}`
  - 在执行上下文中传递变量
- **验收标准**：
  - 任务间可传递参数
  - 支持内置变量自动填充
  - 支持自定义变量设置

---

### Phase 3: 工作流与任务链（第6-8周）

**目标**：实现复杂工作流编排

#### 3.1 任务链/工作流
- **优先级**：🔴 高
- **技术方案**：
  - 创建 `WorkflowEntity` 和 `WorkflowStepEntity`
  - 支持串行/并行执行
  - 支持条件分支（if-else）
  - 创建 `WorkflowEditorScreen` 可视化编辑器
- **验收标准**：
  - 可创建多步骤工作流
  - 支持串行/并行执行
  - 支持条件分支
  - 可视化编辑工作流

#### 3.2 循环执行
- **优先级**：🟡 中
- **技术方案**：
  - 在工作流中添加循环节点
  - 支持循环类型：
    - 固定次数
    - 条件循环（while）
    - 遍历列表
  - 防止无限循环（最大次数限制）
- **验收标准**：
  - 支持固定次数循环
  - 支持条件循环
  - 最大循环次数限制（默认100次）

#### 3.3 异常处理
- **优先级**：🟡 中
- **技术方案**：
  - 在工作流中添加 try-catch 节点
  - 支持异常处理策略：
    - 重试
    - 跳过
    - 终止
    - 执行备用路线
  - 记录异常日志
- **验收标准**：
  - 每步可配置异常处理策略
  - 支持重试/跳过/终止
  - 异常日志详细记录

---

### Phase 4: 高级识别能力（第9-10周）

**目标**：实现屏幕内容识别

#### 4.1 OCR 文字识别
- **优先级**：🟡 中
- **技术方案**：
  - 集成 ML Kit Text Recognition
  - 创建 `OcrEngine` 类
  - 支持实时识别和截图识别
  - 识别结果可用于条件判断
- **验收标准**：
  - 支持中英文识别
  - 识别准确率 > 95%
  - 识别速度 < 500ms
  - 可用于条件判断

#### 4.2 图像匹配
- **优先级**：🟡 中
- **技术方案**：
  - 使用 OpenCV Android SDK
  - 创建 `ImageMatcher` 类
  - 支持模板匹配和特征匹配
  - 支持多尺度匹配
- **验收标准**：
  - 支持模板匹配
  - 匹配准确率 > 90%
  - 支持多尺度匹配
  - 可用于点击定位

#### 4.3 UI 元素识别
- **优先级**：🟢 低
- **技术方案**：
  - 使用 AccessibilityService 获取 UI 树
  - 解析 View hierarchy
  - 支持按 ID/文本/类型查找元素
- **验收标准**：
  - 可获取 UI 元素信息
  - 支持多种查找方式
  - 可用于精确点击

---

### Phase 5: 生态与协作（第11-12周）

**目标**：构建生态系统

#### 5.1 任务模板市场
- **优先级**：🟢 低
- **技术方案**：
  - 创建 `TemplateEntity` 数据库表
  - 支持导入/导出模板
  - 模板分类和搜索
  - 本地模板库
- **验收标准**：
  - 可创建/编辑/删除模板
  - 支持导入/导出
  - 模板分类管理

#### 5.2 多设备协同
- **优先级**：🟢 低
- **技术方案**：
  - 使用 WebSocket 实现设备间通信
  - 创建 `DeviceManager` 类
  - 支持主从模式
  - 任务分发和结果汇总
- **验收标准**：
  - 支持多设备连接
  - 可分发任务到多设备
  - 结果汇总显示

#### 5.3 云端同步
- **优先级**：🟢 低
- **技术方案**：
  - 使用 Firebase 或自建服务器
  - 同步任务/路线/配置
  - 支持冲突解决
  - 离线优先策略
- **验收标准**：
  - 支持云端备份
  - 多设备同步
  - 离线可用

---

## 🏗️ 技术架构设计

### 新增模块

```
app/src/main/java/com/smarttasker/
├── core/
│   ├── trigger/           # 触发器模块
│   │   ├── NotificationTrigger.kt
│   │   ├── TimeTrigger.kt
│   │   └── ConditionTrigger.kt
│   ├── condition/         # 条件执行模块
│   │   ├── ConditionEvaluator.kt
│   │   ├── ScreenCondition.kt
│   │   └── SystemCondition.kt
│   ├── workflow/          # 工作流模块
│   │   ├── WorkflowEngine.kt
│   │   ├── WorkflowStep.kt
│   │   └── WorkflowVariable.kt
│   ├── ocr/               # OCR 模块
│   │   ├── OcrEngine.kt
│   │   └── TextMatcher.kt
│   ├── image/             # 图像匹配模块
│   │   ├── ImageMatcher.kt
│   │   └── TemplateStore.kt
│   └── retry/             # 重试模块
│       ├── RetryPolicy.kt
│       └── RetryExecutor.kt
├── data/
│   ├── entity/
│   │   ├── WorkflowEntity.kt
│   │   ├── WorkflowStepEntity.kt
│   │   ├── TemplateEntity.kt
│   │   └── TriggerConfigEntity.kt
│   └── database/
│       ├── WorkflowDao.kt
│       └── TemplateDao.kt
└── ui/
    ├── workflow/          # 工作流编辑器
    │   ├── WorkflowEditorScreen.kt
    │   └── WorkflowNode.kt
    ├── trigger/           # 触发器配置
    │   └── TriggerConfigScreen.kt
    └── template/          # 模板管理
        └── TemplateScreen.kt
```

### 数据库扩展

```sql
-- 工作流表
CREATE TABLE workflows (
    workflow_id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    trigger_type TEXT,
    trigger_config TEXT,
    created_at INTEGER,
    updated_at INTEGER
);

-- 工作流步骤表
CREATE TABLE workflow_steps (
    step_id TEXT PRIMARY KEY,
    workflow_id TEXT,
    step_order INTEGER,
    step_type TEXT,  -- task/condition/loop/parallel
    task_id TEXT,
    condition TEXT,
    on_success TEXT,  -- next/retry/stop
    on_failure TEXT,  -- next/retry/stop
    FOREIGN KEY (workflow_id) REFERENCES workflows(workflow_id)
);

-- 模板表
CREATE TABLE templates (
    template_id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    category TEXT,
    description TEXT,
    content TEXT,  -- JSON
    created_at INTEGER
);

-- 触发器配置表
CREATE TABLE trigger_configs (
    config_id TEXT PRIMARY KEY,
    task_id TEXT,
    trigger_type TEXT,
    config TEXT,  -- JSON
    enabled INTEGER DEFAULT 1,
    FOREIGN KEY (task_id) REFERENCES tasks(task_id)
);
```

---

## 📅 详细开发排期

### Week 1-2: Phase 1 - 核心稳定性

| 任务 | 负责人 | 工时 | 产出 |
|------|--------|------|------|
| 错误重试机制 | Claude Code | 2天 | RetryPolicy + RetryExecutor |
| 屏幕截图验证 | Claude Code | 2天 | ScreenshotManager |
| 执行日志增强 | Claude Code | 1天 | TraceEventEntity 扩展 |
| 单元测试 | Claude Code | 2天 | 测试用例 |
| 集成测试 | 手动 | 2天 | 模拟器验证 |
| 文档更新 | 手动 | 1天 | README + CHANGELOG |

### Week 3-5: Phase 2 - 智能触发

| 任务 | 负责人 | 工时 | 产出 |
|------|--------|------|------|
| 通知触发服务 | Claude Code | 3天 | NotificationTriggerService |
| 条件执行引擎 | Claude Code | 3天 | ConditionEvaluator |
| 变量管理系统 | Claude Code | 2天 | VariableManager |
| UI 配置界面 | Claude Code | 3天 | TriggerConfigScreen |
| 测试验证 | 手动 | 3天 | 真机测试 |

### Week 6-8: Phase 3 - 工作流

| 任务 | 负责人 | 工时 | 产出 |
|------|--------|------|------|
| 工作流引擎 | Claude Code | 4天 | WorkflowEngine |
| 可视化编辑器 | Claude Code | 4天 | WorkflowEditorScreen |
| 循环/分支支持 | Claude Code | 3天 | 循环/分支节点 |
| 异常处理 | Claude Code | 2天 | 异常处理策略 |
| 测试验证 | 手动 | 3天 | 复杂工作流测试 |

### Week 9-10: Phase 4 - 高级识别

| 任务 | 负责人 | 工时 | 产出 |
|------|--------|------|------|
| OCR 集成 | Claude Code | 3天 | OcrEngine |
| 图像匹配 | Claude Code | 3天 | ImageMatcher |
| UI 元素识别 | Claude Code | 2天 | UiElementFinder |
| 性能优化 | Claude Code | 2天 | 识别速度优化 |
| 测试验证 | 手动 | 2天 | 准确率测试 |

### Week 11-12: Phase 5 - 生态建设

| 任务 | 负责人 | 工时 | 产出 |
|------|--------|------|------|
| 模板系统 | Claude Code | 3天 | TemplateManager |
| 多设备协同 | Claude Code | 4天 | DeviceManager |
| 云端同步 | Claude Code | 3天 | CloudSync |
| 文档完善 | 手动 | 2天 | 用户手册 |
| 发布准备 | 手动 | 2天 | 打包 + 发布 |

---

## 🧪 测试策略

### 单元测试
- 每个核心模块编写单元测试
- 覆盖率目标：80%+
- 使用 JUnit 5 + MockK

### 集成测试
- 模拟器端到端测试
- 真机兼容性测试
- 性能测试（内存/电量）

### 回归测试
- 每次发布前运行完整测试套件
- 自动化测试脚本

---

## 📊 成功指标

| 指标 | 目标值 | 测量方式 |
|------|--------|----------|
| 功能完整度 | 90%+ | 功能清单检查 |
| 任务成功率 | 95%+ | 运行记录统计 |
| OCR 识别率 | 95%+ | 测试集验证 |
| 图像匹配率 | 90%+ | 测试集验证 |
| 应用崩溃率 | < 0.1% | 崩溃日志统计 |
| 用户满意度 | 4.5+ | 用户反馈 |

---

## 🚀 发布计划

### v0.6.0 (Week 2)
- 错误重试
- 屏幕截图验证
- 执行日志增强

### v0.7.0 (Week 5)
- 通知触发
- 条件执行
- 变量传递

### v0.8.0 (Week 8)
- 工作流引擎
- 可视化编辑器
- 循环/分支

### v0.9.0 (Week 10)
- OCR 识别
- 图像匹配
- UI 元素识别

### v1.0.0 (Week 12)
- 模板市场
- 多设备协同
- 云端同步
- 正式发布

---

## ⚠️ 风险与应对

| 风险 | 影响 | 应对措施 |
|------|------|----------|
| OCR 识别率低 | 条件执行不可靠 | 备选方案：手动标注 |
| 图像匹配慢 | 执行效率低 | 优化算法 + 缓存 |
| 通知监听权限 | 部分设备不支持 | 引导用户开启权限 |
| 多设备同步冲突 | 数据不一致 | 冲突解决策略 |
| 内存占用过高 | 应用卡顿 | 内存优化 + 泄漏检测 |

---

## 📝 文档清单

- [ ] README.md - 项目介绍
- [ ] CONTRIBUTING.md - 贡献指南
- [ ] API.md - API 文档
- [ ] USER_GUIDE.md - 用户手册
- [ ] CHANGELOG.md - 版本日志
- [ ] ARCHITECTURE.md - 架构文档

---

**制定日期**：2026-05-28
**版本**：v1.0
**状态**：待审批
