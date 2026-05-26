package com.smarttasker.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "smarttask_settings")

/**
 * Central settings repository backed by DataStore.
 * All app configuration flows through here.
 */
class SettingsRepository(private val context: Context) {

    // ===== Keys =====

    // Model / LLM
    private object Keys {
        val API_URL = stringPreferencesKey("api_url")
        val API_KEY = stringPreferencesKey("api_key")
        val MODEL_NAME = stringPreferencesKey("model_name")
        val CUSTOM_PROMPT = stringPreferencesKey("custom_prompt")

        // Core
        val USE_MOCK_BRIDGE = booleanPreferencesKey("use_mock_bridge")
        val CORE_HOST = stringPreferencesKey("core_host")
        val CORE_PORT = intPreferencesKey("core_port")

        // Safety
        val CONFIRM_HIGH_RISK = booleanPreferencesKey("confirm_high_risk")
        val AUTO_CONFIRM_LOW_RISK = booleanPreferencesKey("auto_confirm_low_risk")
        val ALLOW_SEND = booleanPreferencesKey("allow_send")
        val ALLOW_DELETE = booleanPreferencesKey("allow_delete")
        val ALLOW_PAYMENT = booleanPreferencesKey("allow_payment")

        // Budget
        val DAILY_BUDGET_YUAN = floatPreferencesKey("daily_budget_yuan")
        val ALERT_ON_THRESHOLD = booleanPreferencesKey("alert_on_threshold")
        val STOP_ON_BUDGET = booleanPreferencesKey("stop_on_budget")
    }

    // ===== Model Config =====

    val apiUrl: Flow<String> = context.dataStore.data.map {
        it[Keys.API_URL] ?: "https://api.openai.com/v1/chat/completions"
    }

    val apiKey: Flow<String> = context.dataStore.data.map {
        it[Keys.API_KEY] ?: ""
    }

    val modelName: Flow<String> = context.dataStore.data.map {
        it[Keys.MODEL_NAME] ?: "gpt-4o-mini"
    }

    suspend fun saveModelConfig(apiUrl: String, apiKey: String, modelName: String) {
        context.dataStore.edit {
            it[Keys.API_URL] = apiUrl
            it[Keys.API_KEY] = apiKey
            it[Keys.MODEL_NAME] = modelName
        }
    }

    // ===== Custom Prompt =====

    val customPrompt: Flow<String> = context.dataStore.data.map {
        it[Keys.CUSTOM_PROMPT] ?: ""
    }

    suspend fun saveCustomPrompt(prompt: String) {
        context.dataStore.edit { it[Keys.CUSTOM_PROMPT] = prompt }
    }

    // ===== Core Config =====

    val useMockBridge: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.USE_MOCK_BRIDGE] ?: false  // Default to REAL bridge
    }

    val coreHost: Flow<String> = context.dataStore.data.map {
        it[Keys.CORE_HOST] ?: "127.0.0.1"
    }

    val corePort: Flow<Int> = context.dataStore.data.map {
        it[Keys.CORE_PORT] ?: 12345
    }

    suspend fun saveCoreConfig(useMock: Boolean, host: String, port: Int) {
        context.dataStore.edit {
            it[Keys.USE_MOCK_BRIDGE] = useMock
            it[Keys.CORE_HOST] = host
            it[Keys.CORE_PORT] = port
        }
    }

    // ===== Safety Policy =====

    data class SafetySettings(
        val confirmHighRisk: Boolean = true,
        val autoConfirmLowRisk: Boolean = false,
        val allowSend: Boolean = true,
        val allowDelete: Boolean = true,
        val allowPayment: Boolean = false
    )

    val safetySettings: Flow<SafetySettings> = context.dataStore.data.map { prefs ->
        SafetySettings(
            confirmHighRisk = prefs[Keys.CONFIRM_HIGH_RISK] ?: true,
            autoConfirmLowRisk = prefs[Keys.AUTO_CONFIRM_LOW_RISK] ?: false,
            allowSend = prefs[Keys.ALLOW_SEND] ?: true,
            allowDelete = prefs[Keys.ALLOW_DELETE] ?: true,
            allowPayment = prefs[Keys.ALLOW_PAYMENT] ?: false
        )
    }

    suspend fun saveSafetySettings(settings: SafetySettings) {
        context.dataStore.edit {
            it[Keys.CONFIRM_HIGH_RISK] = settings.confirmHighRisk
            it[Keys.AUTO_CONFIRM_LOW_RISK] = settings.autoConfirmLowRisk
            it[Keys.ALLOW_SEND] = settings.allowSend
            it[Keys.ALLOW_DELETE] = settings.allowDelete
            it[Keys.ALLOW_PAYMENT] = settings.allowPayment
        }
    }

    // ===== Budget =====

    data class BudgetSettings(
        val dailyBudgetYuan: Float = 10f,
        val alertOnThreshold: Boolean = true,
        val stopOnBudget: Boolean = false
    )

    val budgetSettings: Flow<BudgetSettings> = context.dataStore.data.map { prefs ->
        BudgetSettings(
            dailyBudgetYuan = prefs[Keys.DAILY_BUDGET_YUAN] ?: 10f,
            alertOnThreshold = prefs[Keys.ALERT_ON_THRESHOLD] ?: true,
            stopOnBudget = prefs[Keys.STOP_ON_BUDGET] ?: false
        )
    }

    suspend fun saveBudgetSettings(settings: BudgetSettings) {
        context.dataStore.edit {
            it[Keys.DAILY_BUDGET_YUAN] = settings.dailyBudgetYuan
            it[Keys.ALERT_ON_THRESHOLD] = settings.alertOnThreshold
            it[Keys.STOP_ON_BUDGET] = settings.stopOnBudget
        }
    }

    // ===== Export / Import =====

    suspend fun exportAll(): Map<String, Any?> {
        val prefs = context.dataStore.data.first()
        return prefs.asMap().mapKeys { it.key.name }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
