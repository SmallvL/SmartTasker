package com.smarttasker.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.data.repository.SettingsRepository
import com.smarttasker.ui.common.SmartCard
import com.smarttasker.ui.common.SmartButton
import com.smarttasker.ui.common.SmartSecondaryButton
import com.smarttasker.ui.common.StatusPill
import com.smarttasker.ui.theme.SmartColors
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

sealed class TestResult {
    object Success : TestResult()
    data class Error(val msg: String) : TestResult()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelConfigScreen(
    settingsRepo: SettingsRepository,
    onBack: () -> Unit
) {
    val savedUrl by settingsRepo.apiUrl.collectAsState(initial = "https://api.openai.com/v1/chat/completions")
    val savedKey by settingsRepo.apiKey.collectAsState(initial = "")
    val savedModel by settingsRepo.modelName.collectAsState(initial = "gpt-4o-mini")
    val scope = rememberCoroutineScope()

    var apiUrl by remember(savedUrl) { mutableStateOf(savedUrl) }
    var apiKey by remember(savedKey) { mutableStateOf(savedKey) }
    var modelName by remember(savedModel) { mutableStateOf(savedModel) }
    var showKey by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<TestResult?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = SmartColors.accent(),
        unfocusedBorderColor = SmartColors.borderSubtle()
    )
    val fieldShape = RoundedCornerShape(16)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型配置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // API Config Card
            item {
                SmartCard {
                    Text(
                        "API 配置",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = apiUrl,
                        onValueChange = { apiUrl = it },
                        label = { Text("API 地址") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = fieldShape,
                        colors = fieldColors,
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = fieldShape,
                        colors = fieldColors,
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    if (showKey) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                    contentDescription = if (showKey) "隐藏" else "显示"
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = modelName,
                        onValueChange = { modelName = it },
                        label = { Text("模型名称") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = fieldShape,
                        colors = fieldColors,
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.SmartToy, contentDescription = null) }
                    )
                }
            }

            // Test Connection
            item {
                SmartCard {
                    Text(
                        "连接测试",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    SmartButton(
                        text = if (isTesting) "测试中..." else "测试连接",
                        onClick = {
                            isTesting = true
                            testResult = null
                            scope.launch {
                                try {
                                    val client = OkHttpClient.Builder()
                                        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                                        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                                        .build()
                                    val messages = JSONArray().put(
                                        JSONObject().apply {
                                            put("role", "user")
                                            put("content", "hi")
                                        }
                                    )
                                    val body = JSONObject().apply {
                                        put("model", modelName)
                                        put("messages", messages)
                                        put("max_tokens", 5)
                                    }
                                    val req = Request.Builder()
                                        .url(apiUrl)
                                        .addHeader("Authorization", "Bearer $apiKey")
                                        .post(body.toString().toRequestBody("application/json".toMediaType()))
                                        .build()
                                    val resp = client.newCall(req).execute()
                                    testResult = if (resp.isSuccessful) {
                                        TestResult.Success
                                    } else {
                                        TestResult.Error("HTTP ${resp.code}: ${resp.body?.string()?.take(200)}")
                                    }
                                } catch (e: Exception) {
                                    testResult = TestResult.Error(e.message ?: "连接失败")
                                }
                                isTesting = false
                            }
                        },
                        icon = Icons.Outlined.NetworkCheck,
                        enabled = !isTesting && apiUrl.isNotBlank() && apiKey.isNotBlank()
                    )

                    if (isTesting) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = SmartColors.accent()
                        )
                    }

                    testResult?.let { result ->
                        Spacer(modifier = Modifier.height(12.dp))
                        when (result) {
                            is TestResult.Success -> StatusPill("连接成功", SmartColors.success())
                            is TestResult.Error -> {
                                Column {
                                    StatusPill("连接失败", SmartColors.danger())
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        result.msg,
                                        fontSize = 12.sp,
                                        color = SmartColors.textSecondary()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Preset Templates
            item {
                SmartCard {
                    Text(
                        "快捷模板",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    data class Preset(val name: String, val url: String)
                    val presets = listOf(
                        Preset("OpenAI", "https://api.openai.com/v1/chat/completions"),
                        Preset("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"),
                        Preset("DeepSeek", "https://api.deepseek.com/v1/chat/completions")
                    )

                    presets.forEachIndexed { index, preset ->
                        if (index > 0) {
                            Divider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = SmartColors.borderSubtle()
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    apiUrl = preset.url
                                    testResult = null
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Language,
                                contentDescription = null,
                                tint = SmartColors.accent(),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    preset.name,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                                Text(
                                    preset.url,
                                    fontSize = 11.sp,
                                    color = SmartColors.textSecondary()
                                )
                            }
                            Icon(
                                Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = SmartColors.textSecondary()
                            )
                        }
                    }
                }
            }

            // Save Button
            item {
                SmartButton(
                    text = "保存配置",
                    onClick = {
                        scope.launch {
                            settingsRepo.saveModelConfig(apiUrl, apiKey, modelName)
                            onBack()
                        }
                    },
                    icon = Icons.Outlined.Save,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                SmartSecondaryButton(
                    text = "取消",
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
