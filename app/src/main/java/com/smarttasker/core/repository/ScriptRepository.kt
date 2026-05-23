package com.smarttasker.core.repository

import com.smarttasker.core.database.ScriptDao
import com.smarttasker.model.ScriptEntity
import com.smarttasker.model.ScriptItem
import com.smarttasker.model.ScriptStepEntity
import com.smarttasker.model.StepOperation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 脚本仓库
 */
@Singleton
class ScriptRepository @Inject constructor(
    private val scriptDao: ScriptDao
) {
    /**
     * 获取所有脚本
     */
    fun getAllScripts(): Flow<List<ScriptItem>> {
        return scriptDao.getAllScripts().map { entities ->
            entities.map { it.toScriptItem() }
        }
    }
    
    /**
     * 根据 ID 获取脚本
     */
    suspend fun getScriptById(scriptId: String): ScriptEntity? {
        return scriptDao.getScriptById(scriptId)
    }
    
    /**
     * 获取脚本步骤
     */
    fun getScriptSteps(scriptId: String): Flow<List<ScriptStepEntity>> {
        return scriptDao.getScriptSteps(scriptId)
    }
    
    /**
     * 插入脚本
     */
    suspend fun insertScript(script: ScriptEntity) {
        scriptDao.insertScript(script)
    }
    
    /**
     * 创建新脚本
     */
    suspend fun createScript(
        name: String,
        description: String,
        category: String,
        isAiGenerated: Boolean = false
    ): ScriptEntity {
        val script = ScriptEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            category = category,
            isAiGenerated = isAiGenerated
        )
        scriptDao.insertScript(script)
        return script
    }
    
    /**
     * 更新脚本
     */
    suspend fun updateScript(script: ScriptEntity) {
        scriptDao.updateScript(script)
    }
    
    /**
     * 删除脚本
     */
    suspend fun deleteScript(scriptId: String) {
        scriptDao.deleteScriptById(scriptId)
    }
    
    /**
     * 插入脚本步骤
     */
    suspend fun insertStep(step: ScriptStepEntity) {
        scriptDao.insertStep(step)
    }
    
    /**
     * 创建新步骤
     */
    suspend fun createStep(
        scriptId: String,
        operation: StepOperation,
        params: String,
        description: String,
        semanticNote: String = "",
        expected: String = ""
    ): ScriptStepEntity {
        val maxIndex = scriptDao.getMaxStepIndex(scriptId) ?: -1
        val step = ScriptStepEntity(
            id = UUID.randomUUID().toString(),
            scriptId = scriptId,
            stepIndex = maxIndex + 1,
            operation = operation,
            params = params,
            description = description,
            semanticNote = semanticNote,
            expected = expected
        )
        scriptDao.insertStep(step)
        return step
    }
    
    /**
     * 更新步骤
     */
    suspend fun updateStep(step: ScriptStepEntity) {
        scriptDao.updateStep(step)
    }
    
    /**
     * 删除步骤
     */
    suspend fun deleteStep(step: ScriptStepEntity) {
        scriptDao.deleteStep(step)
    }
    
    /**
     * 删除脚本的所有步骤
     */
    suspend fun deleteScriptSteps(scriptId: String) {
        scriptDao.deleteScriptSteps(scriptId)
    }
    
    /**
     * 获取脚本步骤数量
     */
    suspend fun getScriptStepCount(scriptId: String): Int {
        return scriptDao.getScriptStepCount(scriptId)
    }
    
    /**
     * 获取脚本数量
     */
    suspend fun getScriptCount(): Int {
        return scriptDao.getScriptCount()
    }
    
    /**
     * 获取所有分类
     */
    fun getAllCategories(): Flow<List<String>> {
        return scriptDao.getAllCategories()
    }
    
    /**
     * 搜索脚本
     */
    fun searchScripts(query: String): Flow<List<ScriptItem>> {
        return scriptDao.searchScripts(query).map { entities ->
            entities.map { it.toScriptItem() }
        }
    }
    
    /**
     * ScriptEntity 转换为 ScriptItem
     */
    private suspend fun ScriptEntity.toScriptItem(): ScriptItem {
        return ScriptItem(
            id = id,
            name = name,
            description = description,
            category = category,
            stepCount = scriptDao.getScriptStepCount(id),
            lastModified = updatedAt
        )
    }
}