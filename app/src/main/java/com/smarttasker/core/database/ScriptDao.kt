package com.smarttasker.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.smarttasker.model.ScriptEntity
import com.smarttasker.model.ScriptStepEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScriptDao {
    /**
     * 获取所有脚本
     */
    @Query("SELECT * FROM scripts ORDER BY updatedAt DESC")
    fun getAllScripts(): Flow<List<ScriptEntity>>
    
    /**
     * 根据 ID 获取脚本
     */
    @Query("SELECT * FROM scripts WHERE id = :scriptId")
    suspend fun getScriptById(scriptId: String): ScriptEntity?
    
    /**
     * 根据分类获取脚本
     */
    @Query("SELECT * FROM scripts WHERE category = :category ORDER BY updatedAt DESC")
    fun getScriptsByCategory(category: String): Flow<List<ScriptEntity>>
    
    /**
     * 获取 AI 生成的脚本
     */
    @Query("SELECT * FROM scripts WHERE isAiGenerated = 1 ORDER BY updatedAt DESC")
    fun getAiGeneratedScripts(): Flow<List<ScriptEntity>>
    
    /**
     * 搜索脚本
     */
    @Query("SELECT * FROM scripts WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchScripts(query: String): Flow<List<ScriptEntity>>
    
    /**
     * 插入脚本
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScript(script: ScriptEntity)
    
    /**
     * 插入多个脚本
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScripts(scripts: List<ScriptEntity>)
    
    /**
     * 更新脚本
     */
    @Update
    suspend fun updateScript(script: ScriptEntity)
    
    /**
     * 删除脚本
     */
    @Delete
    suspend fun deleteScript(script: ScriptEntity)
    
    /**
     * 根据 ID 删除脚本
     */
    @Query("DELETE FROM scripts WHERE id = :scriptId")
    suspend fun deleteScriptById(scriptId: String)
    
    /**
     * 删除所有脚本
     */
    @Query("DELETE FROM scripts")
    suspend fun deleteAllScripts()
    
    /**
     * 获取脚本数量
     */
    @Query("SELECT COUNT(*) FROM scripts")
    suspend fun getScriptCount(): Int
    
    /**
     * 获取所有分类
     */
    @Query("SELECT DISTINCT category FROM scripts ORDER BY category")
    fun getAllCategories(): Flow<List<String>>
    
    // ============================================================
    // 脚本步骤
    // ============================================================
    
    /**
     * 获取脚本的所有步骤
     */
    @Query("SELECT * FROM script_steps WHERE scriptId = :scriptId ORDER BY stepIndex ASC")
    fun getScriptSteps(scriptId: String): Flow<List<ScriptStepEntity>>
    
    /**
     * 获取脚本步骤数量
     */
    @Query("SELECT COUNT(*) FROM script_steps WHERE scriptId = :scriptId")
    suspend fun getScriptStepCount(scriptId: String): Int
    
    /**
     * 插入脚本步骤
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStep(step: ScriptStepEntity)
    
    /**
     * 插入多个脚本步骤
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSteps(steps: List<ScriptStepEntity>)
    
    /**
     * 更新脚本步骤
     */
    @Update
    suspend fun updateStep(step: ScriptStepEntity)
    
    /**
     * 删除脚本步骤
     */
    @Delete
    suspend fun deleteStep(step: ScriptStepEntity)
    
    /**
     * 删除脚本的所有步骤
     */
    @Query("DELETE FROM script_steps WHERE scriptId = :scriptId")
    suspend fun deleteScriptSteps(scriptId: String)
    
    /**
     * 删除所有脚本步骤
     */
    @Query("DELETE FROM script_steps")
    suspend fun deleteAllSteps()
    
    /**
     * 获取步骤在脚本中的最大索引
     */
    @Query("SELECT MAX(stepIndex) FROM script_steps WHERE scriptId = :scriptId")
    suspend fun getMaxStepIndex(scriptId: String): Int?
    
    /**
     * 更新步骤索引
     */
    @Query("UPDATE script_steps SET stepIndex = :newIndex WHERE id = :stepId")
    suspend fun updateStepIndex(stepId: String, newIndex: Int)
}
