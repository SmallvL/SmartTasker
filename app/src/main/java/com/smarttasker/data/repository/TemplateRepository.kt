package com.smarttasker.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.smarttasker.data.database.TemplateDao
import com.smarttasker.data.entity.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class TemplateRepository(private val dao: TemplateDao) {

    // ===== Read queries =====

    fun getAllTemplates(): Flow<List<TemplateEntity>> = dao.getAllTemplates()

    fun getTemplatesByCategory(category: String): Flow<List<TemplateEntity>> =
        dao.getTemplatesByCategory(category)

    fun searchTemplates(query: String): Flow<List<TemplateEntity>> =
        dao.searchTemplates(query)

    suspend fun getTemplateById(id: String): TemplateEntity? = dao.getTemplateById(id)

    fun getTemplateByIdFlow(id: String): Flow<TemplateEntity?> = dao.getTemplateByIdFlow(id)

    fun getStepsForTemplate(templateId: String, versionCode: Int): Flow<List<TemplateStepEntity>> =
        dao.getStepsForTemplate(templateId, versionCode)

    suspend fun getStepsForTemplateSync(templateId: String, versionCode: Int): List<TemplateStepEntity> =
        dao.getStepsForTemplateSync(templateId, versionCode)

    fun getVersionsForTemplate(templateId: String): Flow<List<TemplateVersionEntity>> =
        dao.getVersionsForTemplate(templateId)

    // ===== Create from route =====

    /**
     * 从路线创建模板，复制路线的所有步骤
     */
    suspend fun createFromRoute(
        name: String,
        description: String,
        category: String,
        routeId: String,
        routeRepo: RouteRepository
    ): TemplateEntity {
        val route = routeRepo.getRouteById(routeId)
            ?: throw IllegalArgumentException("Route not found: $routeId")
        val routeSteps = routeRepo.getStepsForRouteSync(routeId)

        val templateId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val template = TemplateEntity(
            templateId = templateId,
            name = name,
            description = description,
            category = category,
            sourceTaskId = route.taskId,
            sourceRouteId = routeId,
            stepCount = routeSteps.size,
            createdAt = now,
            updatedAt = now
        )
        dao.insertTemplate(template)

        // Copy route steps to template steps
        val templateSteps = routeSteps.mapIndexed { index, routeStep ->
            TemplateStepEntity(
                stepId = UUID.randomUUID().toString(),
                templateId = templateId,
                versionCode = 1,
                stepIndex = routeStep.stepIndex,
                enabled = routeStep.enabled,
                type = routeStep.type,
                summary = routeStep.summary,
                locatorStrategy = routeStep.locatorStrategy,
                locatorValue = routeStep.locatorValue,
                waitTimeMs = routeStep.waitTimeMs,
                riskLevel = routeStep.riskLevel,
                notes = ""
            )
        }
        if (templateSteps.isNotEmpty()) {
            dao.insertSteps(templateSteps)
        }

        // Create initial version record
        val version = TemplateVersionEntity(
            versionId = UUID.randomUUID().toString(),
            templateId = templateId,
            versionCode = 1,
            version = "1.0.0",
            changeSummary = "初始版本",
            stepCount = routeSteps.size,
            createdAt = now
        )
        dao.insertVersion(version)

        return template
    }

    /**
     * 从任务创建模板，查找已发布的路线并复制
     */
    suspend fun createFromTask(
        name: String,
        description: String,
        category: String,
        taskId: String,
        routeRepo: RouteRepository
    ): TemplateEntity? {
        val route = routeRepo.getLatestPublishedRoute(taskId) ?: return null
        return createFromRoute(name, description, category, route.routeId, routeRepo)
    }

    // ===== Save / Update / Delete =====

    /**
     * 保存模板（新建或更新），同时保存步骤
     */
    suspend fun saveAsTemplate(
        templateId: String,
        name: String,
        steps: List<TemplateStepEntity>
    ): TemplateEntity {
        val existing = dao.getTemplateById(templateId)
        val now = System.currentTimeMillis()

        val template = if (existing != null) {
            existing.copy(
                name = name,
                stepCount = steps.size,
                updatedAt = now
            )
        } else {
            TemplateEntity(
                templateId = templateId,
                name = name,
                stepCount = steps.size,
                createdAt = now,
                updatedAt = now
            )
        }
        dao.insertTemplate(template)

        val versionCode = template.versionCode
        dao.replaceAllSteps(templateId, versionCode, steps)

        return template
    }

    suspend fun updateTemplate(template: TemplateEntity) {
        dao.updateTemplate(template)
    }

    suspend fun deleteTemplate(templateId: String) {
        dao.deleteTemplate(templateId)
    }

    suspend fun incrementUsage(templateId: String) {
        dao.incrementUsageCount(templateId)
    }

    // ===== Versioning =====

    /**
     * 创建新版本，更新模板的版本信息
     */
    suspend fun createNewVersion(
        templateId: String,
        changeSummary: String,
        steps: List<TemplateStepEntity>
    ): TemplateVersionEntity {
        val template = dao.getTemplateById(templateId)
            ?: throw IllegalArgumentException("Template not found: $templateId")

        val newVersionCode = template.versionCode + 1
        val newVersion = incrementVersionString(template.version)
        val now = System.currentTimeMillis()

        // Save steps for new version
        val versionedSteps = steps.map { step ->
            step.copy(
                stepId = UUID.randomUUID().toString(),
                templateId = templateId,
                versionCode = newVersionCode
            )
        }
        dao.insertSteps(versionedSteps)

        // Create version record
        val versionEntity = TemplateVersionEntity(
            versionId = UUID.randomUUID().toString(),
            templateId = templateId,
            versionCode = newVersionCode,
            version = newVersion,
            changeSummary = changeSummary,
            stepCount = steps.size,
            createdAt = now
        )
        dao.insertVersion(versionEntity)

        // Update template to point to new version
        dao.updateTemplate(
            template.copy(
                version = newVersion,
                versionCode = newVersionCode,
                stepCount = steps.size,
                updatedAt = now
            )
        )

        return versionEntity
    }

    // ===== Export / Import =====

    /**
     * 导出单个模板为 JSON 字符串
     */
    suspend fun exportTemplateToJson(templateId: String): String {
        val template = dao.getTemplateById(templateId)
            ?: throw IllegalArgumentException("Template not found: $templateId")
        val steps = dao.getStepsForTemplateSync(templateId, template.versionCode)
        val exportData = mapOf(
            "template" to template,
            "steps" to steps
        )
        return Gson().toJson(exportData)
    }

    /**
     * 从 JSON 导入模板，生成新 ID
     */
    suspend fun importTemplateFromJson(json: String): TemplateEntity {
        val gson = Gson()
        val type = object : TypeToken<Map<String, Any>>() {}.type
        val raw: Map<String, Any> = gson.fromJson(json, type)

        val templateJson = gson.toJson(raw["template"])
        val stepsJson = gson.toJson(raw["steps"])

        val originalTemplate = gson.fromJson(templateJson, TemplateEntity::class.java)
        val originalSteps = gson.fromJson(stepsJson, Array<TemplateStepEntity>::class.java).toList()

        val newTemplateId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val newTemplate = originalTemplate.copy(
            templateId = newTemplateId,
            isBuiltIn = false,
            usageCount = 0,
            createdAt = now,
            updatedAt = now
        )
        dao.insertTemplate(newTemplate)

        val newSteps = originalSteps.map { step ->
            step.copy(
                stepId = UUID.randomUUID().toString(),
                templateId = newTemplateId
            )
        }
        if (newSteps.isNotEmpty()) {
            dao.insertSteps(newSteps)
        }

        // Create initial version record
        val version = TemplateVersionEntity(
            versionId = UUID.randomUUID().toString(),
            templateId = newTemplateId,
            versionCode = newTemplate.versionCode,
            version = newTemplate.version,
            changeSummary = "从外部导入",
            stepCount = newSteps.size,
            createdAt = now
        )
        dao.insertVersion(version)

        return newTemplate
    }

    /**
     * 批量导出多个模板为 JSON 数组
     */
    suspend fun batchExportToJson(templateIds: List<String>): String {
        val exportList = templateIds.map { id ->
            val template = dao.getTemplateById(id) ?: return@map null
            val steps = dao.getStepsForTemplateSync(id, template.versionCode)
            mapOf(
                "template" to template,
                "steps" to steps
            )
        }.filterNotNull()
        return Gson().toJson(exportList)
    }

    /**
     * 批量从 JSON 导入模板
     */
    suspend fun batchImportFromJson(json: String): List<TemplateEntity> {
        val gson = Gson()
        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        val rawList: List<Map<String, Any>> = gson.fromJson(json, type)

        return rawList.map { raw ->
            val templateJson = gson.toJson(raw["template"])
            val stepsJson = gson.toJson(raw["steps"])

            val originalTemplate = gson.fromJson(templateJson, TemplateEntity::class.java)
            val originalSteps = gson.fromJson(stepsJson, Array<TemplateStepEntity>::class.java).toList()

            val newTemplateId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            val newTemplate = originalTemplate.copy(
                templateId = newTemplateId,
                isBuiltIn = false,
                usageCount = 0,
                createdAt = now,
                updatedAt = now
            )
            dao.insertTemplate(newTemplate)

            val newSteps = originalSteps.map { step ->
                step.copy(
                    stepId = UUID.randomUUID().toString(),
                    templateId = newTemplateId
                )
            }
            if (newSteps.isNotEmpty()) {
                dao.insertSteps(newSteps)
            }

            val version = TemplateVersionEntity(
                versionId = UUID.randomUUID().toString(),
                templateId = newTemplateId,
                versionCode = newTemplate.versionCode,
                version = newTemplate.version,
                changeSummary = "从外部导入",
                stepCount = newSteps.size,
                createdAt = now
            )
            dao.insertVersion(version)

            newTemplate
        }
    }

    // ===== Helpers =====

    private fun incrementVersionString(version: String): String {
        val parts = version.split(".")
        if (parts.size != 3) return "1.0.0"
        val patch = (parts[2].toIntOrNull() ?: 0) + 1
        return "${parts[0]}.${parts[1]}.$patch"
    }
}
