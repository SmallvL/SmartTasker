package com.smarttasker.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * LLM 提供者接口
 */
interface LlmProvider {
    /**
     * 文本补全
     */
    suspend fun complete(
        prompt: String,
        systemPrompt: String = "",
        temperature: Float = 0.7f,
        maxTokens: Int = 4096
    ): String
    
    /**
     * 多模态补全（包含图片）
     */
    suspend fun completeWithImages(
        prompt: String,
        images: List<ByteArray>,
        systemPrompt: String = ""
    ): String
    
    /**
     * 结构化输出
     */
    suspend fun completeStructured(
        prompt: String,
        schema: String,
        systemPrompt: String = ""
    ): JSONObject
}

/**
 * OpenAI 提供者实现
 */
class OpenAiProvider(
    private val apiKey: String,
    private val baseUrl: String = "https://api.openai.com/v1",
    private val model: String = "gpt-4-vision-preview"
) : LlmProvider {
    
    companion object {
        private const val TAG = "OpenAiProvider"
    }
    
    override suspend fun complete(
        prompt: String,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int
    ): String = withContext(Dispatchers.IO) {
        try {
            val messages = JSONArray()
            
            if (systemPrompt.isNotEmpty()) {
                messages.put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
            }
            
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })
            
            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("temperature", temperature)
                put("max_tokens", maxTokens)
            }
            
            val response = makeRequest("chat/completions", requestBody)
            val choices = response.getJSONArray("choices")
            val message = choices.getJSONObject(0).getJSONObject("message")
            message.getString("content")
        } catch (e: Exception) {
            Log.e(TAG, "LLM 调用失败", e)
            throw e
        }
    }
    
    override suspend fun completeWithImages(
        prompt: String,
        images: List<ByteArray>,
        systemPrompt: String
    ): String = withContext(Dispatchers.IO) {
        try {
            val messages = JSONArray()
            
            if (systemPrompt.isNotEmpty()) {
                messages.put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
            }
            
            val content = JSONArray()
            
            // 添加图片
            for (image in images) {
                content.put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", "data:image/png;base64,${android.util.Base64.encodeToString(image, android.util.Base64.NO_WRAP)}")
                    })
                })
            }
            
            // 添加文本
            content.put(JSONObject().apply {
                put("type", "text")
                put("text", prompt)
            })
            
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", content)
            })
            
            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("max_tokens", 4096)
            }
            
            val response = makeRequest("chat/completions", requestBody)
            val choices = response.getJSONArray("choices")
            val message = choices.getJSONObject(0).getJSONObject("message")
            message.getString("content")
        } catch (e: Exception) {
            Log.e(TAG, "LLM 调用失败", e)
            throw e
        }
    }
    
    override suspend fun completeStructured(
        prompt: String,
        schema: String,
        systemPrompt: String
    ): JSONObject = withContext(Dispatchers.IO) {
        try {
            val fullPrompt = """
                $prompt
                
                请以 JSON 格式返回，格式如下：
                $schema
            """.trimIndent()
            
            val response = complete(fullPrompt, systemPrompt)
            
            // 提取 JSON
            val jsonStr = response.let {
                val start = it.indexOf("{")
                val end = it.lastIndexOf("}") + 1
                if (start >= 0 && end > start) {
                    it.substring(start, end)
                } else {
                    it
                }
            }
            
            JSONObject(jsonStr)
        } catch (e: Exception) {
            Log.e(TAG, "结构化输出失败", e)
            throw e
        }
    }
    
    /**
     * 发送 HTTP 请求
     */
    private fun makeRequest(endpoint: String, body: JSONObject): JSONObject {
        val url = URL("$baseUrl/$endpoint")
        val connection = url.openConnection() as HttpURLConnection
        
        connection.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            doOutput = true
            connectTimeout = 30000
            readTimeout = 60000
        }
        
        // 写入请求体
        OutputStreamWriter(connection.outputStream).use { writer ->
            writer.write(body.toString())
            writer.flush()
        }
        
        // 读取响应
        val responseCode = connection.responseCode
        if (responseCode != 200) {
            val error = BufferedReader(InputStreamReader(connection.errorStream)).use { it.readText() }
            throw Exception("API 请求失败: $responseCode - $error")
        }
        
        val response = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
        return JSONObject(response)
    }
}

/**
 * LLM 缓存
 */
class LlmCache {
    private val cache = LinkedHashMap<String, String>(100, 0.75f, true)
    private val maxSize = 100
    
    fun get(key: String): String? {
        return cache[key]
    }
    
    fun put(key: String, value: String) {
        if (cache.size >= maxSize) {
            val oldest = cache.keys.first()
            cache.remove(oldest)
        }
        cache[key] = value
    }
    
    fun clear() {
        cache.clear()
    }
}

/**
 * LLM 管理器
 */
class LlmManager {
    private var provider: LlmProvider? = null
    private val cache = LlmCache()
    
    /**
     * 初始化 LLM 提供者
     */
    fun initialize(config: LlmConfig) {
        provider = when (config.provider) {
            "openai" -> OpenAiProvider(
                apiKey = config.apiKey,
                baseUrl = config.baseUrl,
                model = config.model
            )
            else -> throw IllegalArgumentException("不支持的 LLM 提供者: ${config.provider}")
        }
    }
    
    /**
     * 带缓存的补全
     */
    suspend fun completeWithCache(
        prompt: String,
        systemPrompt: String = "",
        useCache: Boolean = true
    ): String {
        if (useCache) {
            val cacheKey = "$systemPrompt:$prompt"
            cache.get(cacheKey)?.let { return it }
        }
        
        val provider = this.provider ?: throw IllegalStateException("LLM 未初始化")
        val response = provider.complete(prompt, systemPrompt)
        
        if (useCache) {
            val cacheKey = "$systemPrompt:$prompt"
            cache.put(cacheKey, response)
        }
        
        return response
    }
    
    /**
     * 清除缓存
     */
    fun clearCache() {
        cache.clear()
    }
}

/**
 * LLM 配置
 */
data class LlmConfig(
    val provider: String = "openai",
    val apiKey: String = "",
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "gpt-4-vision-preview",
    val maxTokens: Int = 4096,
    val temperature: Float = 0.7f
)
