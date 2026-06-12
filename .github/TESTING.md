# SmartTasker 测试手册 (v0.9.8)

本项目有两层测试：

- **单元测试** (`app/src/test/`) — JVM 上跑，无需设备
- **集成测试** (`app/src/androidTest/`) — 跑在 MuMu 模拟器 (Android 9, 127.0.0.1:7555)

## 单元测试

```bash
./gradlew :app:testDebugUnitTest
```

当前覆盖范围（v0.9.8）：

| 模块 | 文件 | 测试数 |
| --- | --- | --- |
| core/parser | `TaskSpecParserTest.kt` | 30+ |
| core/retry | `RetryPolicyTest.kt` | 8 |
| core/protocol | `FrameCodecTest.kt` | 11 |
| core/record/parser | `RawInputParserTest.kt` | 10 |
| core/record/gesture | `TouchGestureRecognizerTest.kt` | 9 |
| core/direct | `SafeJsonTest.kt` | 13 |
| **合计** | | **80+** |

报告输出在 `app/build/reports/tests/testDebugUnitTest/`。

## 集成测试 (MuMu)

### 准备工作

1. 启动 MuMu 模拟器 (Android 9, 900x1600)
2. ADB 连接：
   ```bash
   adb connect 127.0.0.1:7555
   adb devices
   ```
3. 启用 `SmartTasker` 的 `adb` 服务端口 (5.0+ 自动)

### 跑测试

```bash
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

报告输出在 `app/build/reports/androidTests/connected/`。

### 跑特定测试

```bash
# 只跑 MainActivity 启动测试
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.smarttasker.MainActivityTest

# 只跑录制服务契约测试
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.smarttasker.core.record.ui.RecordingOverlayServiceTest
```

## 录屏/截图

MuMu 模拟器跑 androidTest 时，自动截图会保存到设备的
`/sdcard/Android/data/com.smarttasker.debug/cache/`。可在测试回调里用
`androidx.test.runner.screenshot.Screenshot.capture()` 调用，或直接用
`adb exec-out screencap -p` 抓屏。

## CI

`./.github/workflows/test.yml` 定义了两条流水线：

- `unit-test`: 任意 push/PR 触发
- `android-test`: 仅在 `self-hosted, mumu` runner 上跑，依赖前者成功

### MuMu runner 准备

1. 找一台 Windows 机器 (推荐 16G+ 内存)
2. 安装 MuMu 模拟器并启动
3. 装 GitHub Actions runner，标签 `self-hosted, mumu`
4. 在 `~/.bashrc` 或 `runner/.env` 中加：
   ```bash
   export ADB_SERVER_PORT=5037
   export PATH="$PATH:/path/to/adb"
   ```
5. 注册 runner 后，CI 任务即可自动连上 MuMu

### 调试 CI 失败

- `Actions` → 选 run → `android-test` job → `Run androidTest on MuMu` step
- 失败时下载 `android-test-report` artifact，查看 `connected/index.html`
- 必要时 SSH 到 runner 手动重跑命令
