# 贡献指南

## 开发规范

### 1. 版本号规范

遵循 **Major.Minor.Patch** 格式，不限制十进制：

| 改动类型 | 版本变化 | 示例 |
|----------|----------|------|
| Bug 修复、小调整 | Patch +1 | 0.0.1 → 0.0.2 |
| 新功能、大改动 | Minor +1, Patch 归零 | 0.1.5 → 0.2.0 |
| 架构变更、重大重构 | Major +1, 其余归零 | 1.54.3 → 2.0.0 |

允许非十进制递增：`0.101.65`、`1.200.3` 是合法版本号。

版本号定义在 `app/build.gradle.kts` 的 `versionName` 字段中。

### 2. Issue 管理

**Bug 报告** — 使用 `Bug report` 模板：
- 描述复现步骤
- 附上日志（Debug 日志页面可一键复制）
- 标注严重程度（P0 崩溃 / P1 功能异常 / P2 体验问题）

**功能请求** — 使用 `Feature request` 模板：
- 描述需求和场景
- 如果已有设计文档，附上链接

### 3. 分支策略

```
master ← dev ← feature/xxx
                fix/xxx
```

- `master`: 稳定版本，通过 PR 合并
- `dev`: 开发分支，日常开发
- `feature/xxx`: 新功能开发
- `fix/xxx`: Bug 修复

### 4. PR 流程

1. 从 `dev` 创建功能/修复分支
2. 开发并测试
3. 更新 `CHANGELOG.md`
4. 提交 PR 到 `dev`
5. Code Review 后合并

### 5. 提交规范

```
feat: 添加手势识别功能
fix: 修复 RawInputParser 触摸事件解析时序
docs: 更新 README
refactor: 重构 ShellExecutor 检测逻辑
chore: 更新依赖版本
```

### 6. Git 提交要求

- **每次改动必须提交**，不允许大批量一次性提交
- 一个逻辑变更 = 一个 commit
- 提交信息使用中文或英文均可，保持清晰

### 7. 编译验证

提交前确保：
```bash
./gradlew assembleDebug  # 编译通过
```

## 环境配置

```bash
# 配置 GitHub 用户
git config user.name "lsw"
git config user.email "smallvl@users.noreply.github.com"

# 使用 gh CLI 认证
gh auth setup-git
```
