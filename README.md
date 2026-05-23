# SmartTasker

> AI 驱动的智能任务自动化工具 - 让手机自动化更简单

## 🎯 产品定位

SmartTasker 是一款基于 AI 的智能任务自动化工具，具有以下特点：

1. **AI 自学习**：首次执行用 AI 学习，后续自动回放，大幅降低成本
2. **脚本管理**：支持脚本的增删改查、分类、导入导出
3. **多模式执行**：支持脚本模式和全 LLM 模式
4. **现代 UI**：简约现代的苹果风格设计，操作直观

## ✨ 核心功能

### 1. 智能引导
- TodoList 形式的引导流程
- 完成一项勾选一项
- 每项都可跳转到对应设置

### 2. 任务管理
- 单次任务：立即执行
- 定时任务：指定时间或间隔执行
- 触发任务：通知触发、应用触发等

### 3. 脚本管理
- 脚本增删改查
- 分类管理
- 外部导入
- AI 自动生成
- 手动配置生成
- 步骤二次编辑
- 测试和重录

### 4. 设置管理
- 语言切换
- 暗夜/白天模式
- LLM 配置
- 调试模式
- 操作模式

## 🏗️ 技术架构

```
SmartTasker/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/smarttasker/
│   │   │   │   ├── core/           # 核心逻辑（复用 AutoLXB）
│   │   │   │   ├── ui/             # 界面层（全新设计）
│   │   │   │   ├── service/        # 后台服务
│   │   │   │   ├── model/          # 数据模型
│   │   │   │   └── util/           # 工具类
│   │   │   ├── res/                # 资源文件
│   │   │   └── AndroidManifest.xml
│   │   └── test/                   # 测试代码
│   └── build.gradle.kts
├── docs/                           # 文档
└── README.md
```

## 📱 界面设计

### 设计风格
- 苹果圆角设计风格
- 简约现代但有高级感
- 暗夜/白天模式切换
- 流畅的动画效果

### 页面结构
```
┌─────────────────────────────────────┐
│            顶部导航栏               │
│  ┌─────┐  ┌─────┐  ┌─────┐         │
│  │首页 │  │任务 │  │设置 │         │
│  └─────┘  └─────┘  └─────┘         │
├─────────────────────────────────────┤
│                                   │
│           页面内容                 │
│                                   │
│                                   │
│                                   │
│                                   │
│                                   │
└─────────────────────────────────────┘
```

## 🔧 技术栈

- **语言**: Kotlin
- **UI 框架**: Jetpack Compose
- **架构**: MVVM
- **数据库**: Room
- **依赖注入**: Hilt
- **后台任务**: WorkManager
- **最低 SDK**: Android 7.0 (API 24)

## 📦 依赖

```kotlin
dependencies {
    // Jetpack Compose
    implementation("androidx.compose.ui:ui:1.5.0")
    implementation("androidx.compose.material3:material3:1.1.1")
    implementation("androidx.compose.ui:ui-tooling-preview:1.5.0")
    
    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")
    
    // Room
    implementation("androidx.room:room-runtime:2.5.2")
    implementation("androidx.room:room-ktx:2.5.2")
    
    // Hilt
    implementation("com.google.dagger:hilt-android:2.47")
    
    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.8.1")
}
```

## 🚀 快速开始

1. 克隆项目
2. 打开 Android Studio
3. 同步 Gradle
4. 运行到设备

## 📄 许可证

MIT License

## 🙏 致谢

- [AutoLXB](https://github.com/nicholascm90/lxb-ignition) - 核心逻辑参考
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - UI 框架
- [Material Design 3](https://m3.material.io/) - 设计系统
