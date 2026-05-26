# 05. AI 任务创建向导与 Prompt 设计

> 项目：SmartTask AI / AI 安卓自动化任务产品  
> 版本：v0.2  
> 日期：2026-05-23  
> 底层参考：AutoLXB 二次开发

## 1. 模块目标

AI Task Wizard 负责把用户自然语言转换为结构化 TaskSpec 草稿。

它不直接执行动作，而是输出可审查、可修改、可提交给 AutoLXB 的结构化配置。

---

## 2. 输入

AI 解析任务时应获得：

```text
用户自然语言
本机 App 列表
当前权限状态
支持的任务类型
安全风险规则
模型能力信息
历史任务偏好，可选
```

---

## 3. 输出 Schema

```json
{
  "intent_summary": "string",
  "task_type": "quick | schedule | notification",
  "task_name": "string",
  "target_app_candidates": [
    {
      "name": "string",
      "package": "string",
      "confidence": 0.0
    }
  ],
  "trigger": {
    "type": "manual | schedule | notification",
    "time": "HH:mm",
    "repeat": "none | daily | weekly | custom",
    "notification_rules": {
      "package": "string",
      "title_contains": "string",
      "body_contains": "string"
    }
  },
  "goal": "string",
  "playbook": ["string"],
  "risk_assessment": {
    "level": "low | medium | high | forbidden",
    "reasons": ["string"],
    "requires_confirmation": true
  },
  "missing_info": ["string"],
  "recommended_next_action": "run_trial | ask_user | reject_high_risk"
}
```

---

## 4. 解析策略

### 4.1 任务类型判断

| 用户表达 | 类型 |
|---|---|
| “现在帮我...” | quick |
| “每天/每周/几点...” | schedule |
| “收到通知/消息后...” | notification |

### 4.2 风险判断

| 表达 | 风险 |
|---|---|
| 打开、查看、签到、领取积分 | low |
| 输入、提交普通表单、修改设置 | medium |
| 发送、删除、发布、下单 | high |
| 支付、转账、贷款、注销账号 | forbidden |

### 4.3 缺失信息

如果缺少必要信息，AI 不要编造。

示例：

```text
用户：每天帮我签到
缺失：目标 App、签到入口
```

推荐返回：

```json
{
  "recommended_next_action": "ask_user",
  "missing_info": ["需要选择要签到的 App"]
}
```

---

## 5. Prompt 模板

### 5.1 System Prompt

```text
你是 Android 自动化任务创建助手。你的职责是把用户自然语言转换为结构化 TaskSpec 草稿。

规则：
1. 只输出 JSON，不输出额外解释。
2. 不要编造不存在的 App 包名。
3. 需要根据用户目标判断任务类型：quick、schedule、notification。
4. 必须识别风险等级：low、medium、high、forbidden。
5. 涉及支付、转账、贷款、账号注销，recommended_next_action 必须为 reject_high_risk。
6. 涉及发送、删除、发布、下单，requires_confirmation 必须为 true。
7. 如果缺少必要信息，recommended_next_action 为 ask_user。
8. playbook 应简洁描述执行目标，不要包含不可验证的假设。
```

### 5.2 User Context Prompt

```text
用户输入：{{user_input}}

本机 App 列表：
{{installed_apps}}

当前权限状态：
{{permission_status}}

支持任务类型：quick, schedule, notification

安全规则：
{{risk_policy}}

请按指定 JSON Schema 输出。
```

---

## 6. 示例

### 6.1 低风险定时任务

输入：

```text
每天早上9点打开淘宝芭芭农场收金币
```

输出：

```json
{
  "intent_summary": "每天早上打开淘宝并进入芭芭农场收取金币",
  "task_type": "schedule",
  "task_name": "淘宝芭芭农场收金币",
  "target_app_candidates": [
    {
      "name": "淘宝",
      "package": "com.taobao.taobao",
      "confidence": 0.95
    }
  ],
  "trigger": {
    "type": "schedule",
    "time": "09:00",
    "repeat": "daily"
  },
  "goal": "进入淘宝芭芭农场并收取金币",
  "playbook": [
    "打开淘宝",
    "进入芭芭农场页面",
    "点击收取金币",
    "检查是否领取成功"
  ],
  "risk_assessment": {
    "level": "low",
    "reasons": ["仅涉及打开页面和领取非现金奖励"],
    "requires_confirmation": false
  },
  "missing_info": [],
  "recommended_next_action": "run_trial"
}
```

### 6.2 高风险消息任务

输入：

```text
收到老板微信后自动回复收到，我马上处理
```

输出：

```json
{
  "intent_summary": "收到老板微信通知后进入微信并准备回复固定内容",
  "task_type": "notification",
  "task_name": "微信老板消息回复",
  "target_app_candidates": [
    {
      "name": "微信",
      "package": "com.tencent.mm",
      "confidence": 0.95
    }
  ],
  "trigger": {
    "type": "notification",
    "notification_rules": {
      "package": "com.tencent.mm",
      "title_contains": "老板",
      "body_contains": ""
    }
  },
  "goal": "打开对应聊天并输入回复内容，发送前请求用户确认",
  "playbook": [
    "打开微信通知对应聊天",
    "确认聊天对象",
    "输入回复内容",
    "发送前暂停请求用户确认"
  ],
  "risk_assessment": {
    "level": "high",
    "reasons": ["涉及发送消息"],
    "requires_confirmation": true
  },
  "missing_info": [],
  "recommended_next_action": "run_trial"
}
```

### 6.3 禁止任务

输入：

```text
每天自动帮我支付宝转账100元给张三
```

输出：

```json
{
  "intent_summary": "自动执行支付宝转账",
  "task_type": "quick",
  "task_name": "支付宝转账",
  "target_app_candidates": [
    {
      "name": "支付宝",
      "package": "com.eg.android.AlipayGphone",
      "confidence": 0.9
    }
  ],
  "trigger": {
    "type": "manual"
  },
  "goal": "自动转账",
  "playbook": [],
  "risk_assessment": {
    "level": "forbidden",
    "reasons": ["涉及转账，禁止自动执行"],
    "requires_confirmation": true
  },
  "missing_info": [],
  "recommended_next_action": "reject_high_risk"
}
```

---

## 7. Route Summarizer

用于把 route steps 转成人话。

输入：

```json
{
  "steps": [
    {"type": "open_app", "summary": "打开淘宝"},
    {"type": "tap", "locator": {"strategy": "text", "value": "我的淘宝"}}
  ]
}
```

输出：

```json
{
  "summary": "这条路线会打开淘宝，进入我的淘宝页面，然后进入目标活动页完成领取。",
  "step_summaries": [
    "打开淘宝",
    "点击底部的“我的淘宝”入口"
  ],
  "stability_warnings": [],
  "risk_warnings": []
}
```

---

## 8. Trace Explainer Prompt

目标：把失败日志解释成人话。

输出结构：

```json
{
  "failure_summary": "string",
  "failed_step_id": "string",
  "failure_type": "locator_not_found | app_not_started | permission_error | model_error | timeout | user_cancelled | unknown",
  "user_friendly_reason": "string",
  "suggested_actions": ["string"],
  "can_generate_route_patch": true
}
```

---

## 9. AI 输出约束

- 所有 AI 结果都必须经过客户端 schema validation。
- AI 不得直接执行动作。
- AI 不得绕过 Safety Guard。
- AI 修复建议不得直接发布路线。
- AI 不得覆盖用户锁定步骤。
- 涉及敏感动作时必须交给安全模块二次判断。
