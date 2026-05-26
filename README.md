# SmartTasker — Android 自动化任务执行器

基于 ADB/Shell 的安卓自动化任务录制与执行引擎。

## 功能

- **Core 引擎** — 通过 ADB TLS / Root / SH 模式执行 Shell 命令
- **手动录制** — 录制用户操作生成可回放的任务步骤
- **试跑模式** — 执行单次任务验证
- **任务管理** — 创建、编辑、调度自动化任务

## 技术栈

- Kotlin + Jetpack Compose
- ADB TLS (libadb-android)
- Hilt DI
- Room Database

## 开发环境

- Android SDK 34+
- Gradle 8.x
- JDK 17

## 架构

```
app/
├── core/
│   ├── adb/          # ADB 连接管理
│   ├── bridge/       # Core 桥接层
│   ├── direct/       # Shell 执行器
│   ├── protocol/     # Core 通信协议
│   └── record/       # 录制引擎
│       ├── adb/      # ADB 流式客户端
│       ├── fusion/   # 目标解析器
│       ├── gesture/  # 手势识别
│       ├── model/    # 数据模型
│       ├── parser/   # 原始输入解析
│       ├── session/  # 录制会话管理
│       └── ui/       # 录制 UI
├── ui/               # Compose UI
│   ├── settings/     # 设置页
│   ├── tasks/        # 任务页
│   └── trialrun/     # 试跑/录制页
└── model/            # 数据模型
```

## 许可证

MIT
