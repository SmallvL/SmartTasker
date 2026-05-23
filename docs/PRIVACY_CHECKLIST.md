# SmartTasker 隐私检查清单

## 📋 隐私检查结果

### ✅ 已通过检查

1. **无第三方数据收集**
   - ✅ 不收集用户数据
   - ✅ 不向第三方发送数据
   - ✅ 不使用分析服务

2. **本地数据存储**
   - ✅ 所有数据存储在本地
   - ✅ 使用 Room 数据库
   - ✅ 使用 DataStore 存储设置

3. **网络请求**
   - ✅ 仅在用户主动操作时发送请求
   - ✅ 使用 HTTPS 加密
   - ✅ 不发送设备信息

4. **权限使用**
   - ✅ 仅请求必要权限
   - ✅ 权限用途明确说明
   - ✅ 不滥用权限

### ⚠️ 需要注意

1. **LLM API 调用**
   - 当用户配置 LLM 时，会向用户配置的 API 发送请求
   - 需要用户明确同意
   - 建议使用本地 LLM 或隐私友好的服务

2. **无障碍服务**
   - 无障碍服务可以访问屏幕内容
   - 仅用于执行用户指定的任务
   - 不会记录或上传屏幕内容

3. **网络权限**
   - 需要网络权限来调用 LLM API
   - 建议添加网络使用说明

## 🔍 详细检查

### 1. 权限检查

| 权限 | 用途 | 必要性 |
|------|------|--------|
| INTERNET | 调用 LLM API | 必要 |
| ACCESS_NETWORK_STATE | 检查网络状态 | 必要 |
| FOREGROUND_SERVICE | 后台执行任务 | 必要 |
| POST_NOTIFICATIONS | 显示通知 | 必要 |
| BIND_ACCESSIBILITY_SERVICE | 无障碍服务 | 必要 |
| ACCESS_WIFI_STATE | 无线调试 | 必要 |
| CHANGE_WIFI_STATE | 无线调试 | 必要 |
| RECEIVE_BOOT_COMPLETED | 开机启动 | 可选 |
| SCHEDULE_EXACT_ALARM | 定时任务 | 必要 |
| WAKE_LOCK | 保持唤醒 | 必要 |
| READ_EXTERNAL_STORAGE | 读取脚本文件 | 可选 |
| QUERY_ALL_PACKAGES | 查询应用信息 | 必要 |

### 2. 数据流检查

```
用户输入
    ↓
本地处理
    ↓
本地存储
    ↓
用户查看
```

**无数据外传**

### 3. 网络请求检查

| 请求类型 | 目标 | 数据内容 | 频率 |
|---------|------|---------|------|
| LLM API | 用户配置 | 任务描述 | 按需 |
| 无线调试 | 本地设备 | 配对信息 | 一次 |

### 4. 存储检查

| 数据类型 | 存储位置 | 加密 | 备份 |
|---------|---------|------|------|
| 任务数据 | Room 数据库 | 否 | 否 |
| 脚本数据 | Room 数据库 | 否 | 否 |
| 设置 | DataStore | 否 | 否 |
| 路线 | Room 数据库 | 否 | 否 |

## 🛡️ 隐私保护措施

### 1. 数据最小化
- 仅收集必要数据
- 不收集设备信息
- 不收集用户行为

### 2. 数据本地化
- 所有数据存储在本地
- 不上传到云端
- 不共享给第三方

### 3. 透明度
- 明确说明权限用途
- 提供隐私设置
- 允许用户删除数据

### 4. 安全性
- 使用 HTTPS
- 不存储敏感信息
- 支持数据删除

## 📝 建议改进

### 短期改进
1. 添加隐私政策
2. 添加数据收集说明
3. 添加权限使用说明

### 长期改进
1. 支持端到端加密
2. 支持数据导出
3. 支持数据备份

## 🔧 技术实现

### 1. 网络请求
```kotlin
// 使用 OkHttp
val client = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .build()

// 仅在用户主动操作时发送请求
fun callLlmApi(prompt: String): String {
    // 1. 检查用户是否配置了 API
    // 2. 构建请求
    // 3. 发送请求
    // 4. 返回结果
}
```

### 2. 数据存储
```kotlin
// 使用 Room
@Database(entities = [Task::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
}

// 使用 DataStore
val dataStore = context.createDataStore(name = "settings")
```

### 3. 权限管理
```kotlin
// 动态权限请求
fun requestPermission(permission: String) {
    if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(this, arrayOf(permission), REQUEST_CODE)
    }
}
```

## 📊 隐私评分

| 项目 | 评分 | 说明 |
|------|------|------|
| 数据收集 | ⭐⭐⭐⭐⭐ | 不收集用户数据 |
| 数据存储 | ⭐⭐⭐⭐⭐ | 本地存储 |
| 数据传输 | ⭐⭐⭐⭐ | 仅 LLM API |
| 权限使用 | ⭐⭐⭐⭐ | 仅必要权限 |
| 透明度 | ⭐⭐⭐ | 需要添加隐私政策 |
| **总分** | **⭐⭐⭐⭐** | **良好** |

## 📞 联系方式

如有隐私问题，请联系：
- 邮箱：privacy@smarttasker.com
- GitHub：github.com/smarttasker/smarttasker
