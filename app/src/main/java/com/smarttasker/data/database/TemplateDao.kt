package com.smarttasker.data.database

import androidx.room.*
import com.smarttasker.data.entity.TemplateEntity
import com.smarttasker.data.entity.TemplateStepEntity
import com.smarttasker.data.entity.TemplateVersionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {

    // ===== Template queries =====

    @Query("SELECT * FROM templates ORDER BY updatedAt DESC")
    fun getAllTemplates(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates WHERE category = :category ORDER BY updatedAt DESC")
    fun getTemplatesByCategory(category: String): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchTemplates(query: String): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates WHERE templateId = :templateId")
    suspend fun getTemplateById(templateId: String): TemplateEntity?

    @Query("SELECT * FROM templates WHERE templateId = :templateId")
    fun getTemplateByIdFlow(templateId: String): Flow<TemplateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: TemplateEntity)

    @Update
    suspend fun updateTemplate(template: TemplateEntity)

    @Query("DELETE FROM templates WHERE templateId = :templateId")
    suspend fun deleteTemplate(templateId: String)

    // ===== Step queries =====

    @Query("SELECT * FROM template_steps WHERE templateId = :templateId AND versionCode = :versionCode ORDER BY stepIndex ASC")
    fun getStepsForTemplate(templateId: String, versionCode: Int): Flow<List<TemplateStepEntity>>

    @Query("SELECT * FROM template_steps WHERE templateId = :templateId AND versionCode = :versionCode ORDER BY stepIndex ASC")
    suspend fun getStepsForTemplateSync(templateId: String, versionCode: Int): List<TemplateStepEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSteps(steps: List<TemplateStepEntity>)

    @Query("DELETE FROM template_steps WHERE templateId = :templateId AND versionCode = :versionCode")
    suspend fun deleteStepsForVersion(templateId: String, versionCode: Int)

    @Transaction
    suspend fun replaceAllSteps(templateId: String, versionCode: Int, steps: List<TemplateStepEntity>) {
        deleteStepsForVersion(templateId, versionCode)
        insertSteps(steps)
    }

    // ===== Version queries =====

    @Query("SELECT * FROM template_versions WHERE templateId = :templateId ORDER BY versionCode DESC")
    fun getVersionsForTemplate(templateId: String): Flow<List<TemplateVersionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersion(version: TemplateVersionEntity)

    // ===== Usage =====

    @Query("UPDATE templates SET usageCount = usageCount + 1, updatedAt = :updatedAt WHERE templateId = :templateId")
    suspend fun incrementUsageCount(templateId: String, updatedAt: Long = System.currentTimeMillis())
}
