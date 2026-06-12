# 产品方针 / 第一原则 (Product Direction — First Principle)

> **本文件是 SmartTasker 项目的最高优先级声明，优先级高于任何其他文档。**
> 后续所有产品决策、代码改动、文档更新都必须遵守本原则。

---

## 第一原则 (The First Principle)

> **本产品的产品定义、需求来源、用户故事、UI/UX 规范、技术架构与发布节奏，以 GitHub `SmallvL/SmartTasker` 仓库为唯一权威上游。**

```text
权威上游：    https://github.com/SmallvL/SmartTasker
本地主工程：  D:\1_AIagnet\SMARTTASK\SmallvL_SmartTasker\SmartTasker\
              （即 GitHub 仓库的本地克隆，默认分支 master）
```

---

## 已废弃资产 (Deprecated)

> 下列资产为**早期探索性本地实现**，与上游产品**不是同一份代码**：
> - **栈不同**：Java + View-based UI（vs 上游 Kotlin + Compose）
> - **架构不同**：多 module 分层（vs 上游单 module）
> - **目的不同**：工程骨架/算法验证（vs 上游产品级实现）

| 路径 | 状态 | 处理方式 |
| --- | --- | --- |
| `D:\1_AIagnet\SMARTTASK\android\` (app/core-bridge/task-parser/route-adapter) | ❌ **DEPRECATED — 已废弃，不再维护** | 仅留作历史参考；禁止作为产品真相来源 |
| `D:\1_AIagnet\SMARTTASK\app\build\outputs\apk\` | ❌ DEPRECATED | 不再构建/发布 |
| `D:\1_AIagnet\SMARTTASK\scripts\run-all-tests.ps1` | ❌ DEPRECATED | 测试脚本已失去意义（本地代码不再演进） |
| `D:\1_AIagnet\SMARTTASK\reports\P?_PROGRESS_*.md` | 📦 归档 | 留作本地探索阶段的技术记录 |
| `D:\1_AIagnet\SMARTTASK\SmallvL_SmartTasker\_probe_smallvl\` | 🗑️ 临时探测目录 | 已在 2026-06-12 删除 |

**重要：旧项目不会被物理删除**（用户未要求 rm -rf），但**所有后续工作严禁在 `D:\1_AIagnet\SMARTTASK\android\` 下进行**。如需历史参考，可读源码；不要做修改、不要合上游、不要发版。

---

## 上游同步原则

### 同步方向

- ✅ **读上游**：`git pull origin master`（或 `git pull origin-ssh master`）获取最新代码
- ✅ **向上游推**：本地改进通过 `git push origin-ssh <branch>` 贡献回 `SmallvL/SmartTasker`
- ❌ **禁止反向同步**：不要把上游代码复制到 `D:\1_AIagnet\SMARTTASK\android\` 旧项目

### 冲突处理

如本地（`D:\1_AIagnet\SMARTTASK\android\`）的旧设计与上游冲突：
1. **永远以上游为准**
2. 在上游对应的 Issue / PR 中讨论
3. 旧代码保留在旧位置，但加 `DEPRECATED` 注释

### 本地补丁

如确需对上游做本地试验性修改：
- 在 `D:\1_AIagnet\SMARTTASK\SmallvL_SmartTasker\SmartTasker\` 内新建 feature branch
- 不要污染 master
- 完成后 PR 回上游

---

## 与上游差异的"借鉴"原则

> 本地旧项目在**测试覆盖**与**纯逻辑分层**方面有一些好的工程实践（CBT 模式、状态机、幂等键、246 个单元测试等）。
> 这些是**工程方法**而非**产品需求**，不应作为产品决策的输入。

如需把本地测试思想搬到上游：

1. **在 `D:\1_AIagnet\SMARTTASK\SmallvL_SmartTasker\SmartTasker\app\src\test\` 下新建 Kotlin 测试**，镜像本地 Java 测试的覆盖策略
2. **不要把 Java 源文件搬到 Kotlin 项目**（栈不同，搬过来也跑不起来）
3. **优先在上游 `10_BACKLOG_ISSUES.md` / GitHub Issue 中提出"补全 X 模块测试"**

---

## 决策记录

| 日期 | 决策 | 触发人 |
| --- | --- | --- |
| 2026-06-12 | 产品以 `SmallvL/SmartTasker` 为唯一权威上游；本地 `D:\1_AIagnet\SMARTTASK\android\` 旧项目标记为 DEPRECATED | 用户 |
| 2026-06-12 | `D:\1_AIagnet\SMARTTASK\SmallvL_SmartTasker\SmartTasker\` 成为唯一主维护工程 | 用户 |
| 2026-06-12 | SSH Key (`D:\gitkey_lsw\smallvl.ppk`) 已通过 Pageant 加载；新增 `origin-ssh` 远程 | 助理执行 |

---

## 违反本原则的后果

- 在旧项目下提交代码 → **拒绝合并** / 需迁移到新主工程
- 把旧项目文档作为产品决策依据 → **视为无效**
- 不通过上游 Issue / PR 而私自修改上游行为 → **不记录到产品历史**

---

> 写于 2026-06-12，第一次明确产品方向。
> 后续如需调整本原则，**必须先更新本文档**，再执行任何代码动作。
