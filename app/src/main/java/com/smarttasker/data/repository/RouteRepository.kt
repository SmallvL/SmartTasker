package com.smarttasker.data.repository

import com.smarttasker.core.record.model.RecordedStep
import com.smarttasker.core.record.model.RecordedStepType
import com.smarttasker.core.record.model.RouteDraft
import com.smarttasker.core.record.model.StepAction
import com.smarttasker.data.database.RouteDao
import com.smarttasker.data.entity.RouteVersionEntity
import com.smarttasker.data.entity.RouteStepEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class RouteRepository(private val dao: RouteDao) {
    fun getRouteVersions(taskId: String): Flow<List<RouteVersionEntity>> = dao.getRouteVersions(taskId)
    fun getStepsForRoute(routeId: String): Flow<List<RouteStepEntity>> = dao.getStepsForRoute(routeId)
    fun getAllRoutes(): Flow<List<RouteVersionEntity>> = dao.getAllRoutes()

    suspend fun getLatestPublishedRoute(taskId: String) = dao.getLatestPublishedRoute(taskId)
    suspend fun getStepsForRouteSync(routeId: String) = dao.getStepsForRouteSync(routeId)
    suspend fun getRouteById(routeId: String) = dao.getRouteById(routeId)

    suspend fun createRoute(taskId: String, source: String = "ai_learned"): RouteVersionEntity {
        val route = RouteVersionEntity(
            routeId = UUID.randomUUID().toString().take(8),
            taskId = taskId,
            source = source
        )
        dao.insertRouteVersion(route)
        return route
    }

    /**
     * Save a RouteDraft (from manual recording) to Room DB.
     * Converts RecordedStep → RouteStepEntity and stores everything.
     *
     * @return routeId of the saved route
     */
    suspend fun saveFromDraft(draft: RouteDraft, taskId: String): String {
        val routeId = draft.routeId.ifEmpty { UUID.randomUUID().toString().take(8) }

        val routeVersion = RouteVersionEntity(
            routeId = routeId,
            taskId = taskId,
            version = "1.0.0",
            status = "draft",
            source = when (draft.source.name) {
                "MANUAL_RECORDING" -> "manual_recording"
                "AI_LEARNED" -> "ai_learned"
                "USER_EDIT" -> "user_edit"
                else -> "manual_recording"
            }
        )
        dao.insertRouteVersion(routeVersion)

        // Convert each RecordedStep → RouteStepEntity
        val steps = draft.steps.mapIndexed { index, step ->
            recordedStepToEntity(step, routeId, index + 1)
        }
        if (steps.isNotEmpty()) {
            dao.replaceAllSteps(routeId, steps) // Atomic transaction
        }

        return routeId
    }

    /**
     * Save AI trial steps as RouteStepEntity.
     */
    suspend fun saveFromTrialSteps(
        taskId: String,
        stepSummaries: List<Pair<String, String>>, // (type, summary)
        source: String = "ai_learned"
    ): String {
        val routeId = UUID.randomUUID().toString().take(8)

        val routeVersion = RouteVersionEntity(
            routeId = routeId,
            taskId = taskId,
            version = "1.0.0",
            status = "draft",
            source = source
        )
        dao.insertRouteVersion(routeVersion)

        val steps = stepSummaries.mapIndexed { index, (type, summary) ->
            RouteStepEntity(
                stepId = UUID.randomUUID().toString().take(8),
                routeId = routeId,
                stepIndex = index + 1,
                type = type,
                summary = summary,
                locatorStrategy = "coordinate",
                locatorValue = "",
                source = source
            )
        }
        if (steps.isNotEmpty()) {
            dao.insertSteps(steps)
        }

        return routeId
    }

    /**
     * Save trial steps from actual execution results (RouteStepEntity list).
     * These steps already have proper type, locator, and coordinates.
     */
    suspend fun saveFromTrialSteps(
        taskId: String,
        executedSteps: List<RouteStepEntity>
    ): String {
        val routeId = UUID.randomUUID().toString().take(8)

        val routeVersion = RouteVersionEntity(
            routeId = routeId,
            taskId = taskId,
            version = "1.0.0",
            status = "draft",
            source = "ai_executed"
        )
        dao.insertRouteVersion(routeVersion)

        // Remap step IDs and routeId to the new route
        val steps = executedSteps.mapIndexed { index, step ->
            step.copy(
                stepId = UUID.randomUUID().toString().take(8),
                routeId = routeId,
                stepIndex = index + 1
            )
        }
        if (steps.isNotEmpty()) {
            dao.insertSteps(steps)
        }

        return routeId
    }

    /**
     * 从模板步骤创建路线，将 TemplateStepEntity 转换为 RouteStepEntity。
     * @return routeId of the saved route
     */
    suspend fun saveFromTemplateSteps(
        taskId: String,
        templateSteps: List<com.smarttasker.data.entity.TemplateStepEntity>
    ): String {
        val routeId = UUID.randomUUID().toString().take(8)

        val routeVersion = RouteVersionEntity(
            routeId = routeId,
            taskId = taskId,
            version = "1.0.0",
            status = "published",
            source = "template"
        )
        dao.insertRouteVersion(routeVersion)

        val steps = templateSteps.mapIndexed { index, tStep ->
            RouteStepEntity(
                stepId = UUID.randomUUID().toString().take(8),
                routeId = routeId,
                stepIndex = tStep.stepIndex,
                enabled = tStep.enabled,
                type = tStep.type,
                summary = tStep.summary,
                locatorStrategy = tStep.locatorStrategy,
                locatorValue = tStep.locatorValue,
                waitTimeMs = tStep.waitTimeMs,
                riskLevel = tStep.riskLevel,
                source = "template"
            )
        }
        if (steps.isNotEmpty()) {
            dao.insertSteps(steps)
        }

        return routeId
    }

    suspend fun publishRoute(route: RouteVersionEntity) {
        dao.updateRouteVersion(route.copy(status = "published", publishedAt = System.currentTimeMillis()))
    }

    suspend fun addStep(routeId: String, index: Int, type: String, summary: String, locatorStrategy: String = "text", locatorValue: String = ""): RouteStepEntity {
        val step = RouteStepEntity(
            stepId = UUID.randomUUID().toString().take(8),
            routeId = routeId,
            stepIndex = index,
            type = type,
            summary = summary,
            locatorStrategy = locatorStrategy,
            locatorValue = locatorValue
        )
        dao.insertStep(step)
        return step
    }

    suspend fun updateStep(step: RouteStepEntity) = dao.updateStep(step)
    suspend fun deleteStep(step: RouteStepEntity) = dao.deleteStep(step)
    suspend fun toggleStepEnabled(step: RouteStepEntity) = dao.updateStep(step.copy(enabled = !step.enabled))
    suspend fun toggleStepLocked(step: RouteStepEntity) = dao.updateStep(step.copy(lockedByUser = !step.lockedByUser))

    /**
     * Delete route version and all its steps.
     */
    suspend fun deleteRoute(routeId: String) {
        dao.deleteAllSteps(routeId)
        dao.deleteRouteVersion(routeId)
    }

    /**
     * Re-sequence step indices after deletion/reorder.
     */
    suspend fun reindexSteps(routeId: String) {
        val steps = dao.getStepsForRouteSync(routeId)
        steps.forEachIndexed { index, step ->
            if (step.stepIndex != index + 1) {
                dao.reindexStep(step.stepId, index + 1)
            }
        }
    }
 
    /**
     * 更新路线的所有步骤 (使用 @Transaction 原子操作)
     */
    suspend fun updateRouteSteps(routeId: String, steps: List<RouteStepEntity>) {
        dao.replaceAllSteps(routeId, steps)
    }

    // ===== Conversion helpers =====

    private data class StepInfo(
        val type: String,
        val summary: String,
        val locatorStrategy: String,
        val locatorValue: String
    )

    private fun recordedStepToEntity(step: RecordedStep, routeId: String, index: Int): RouteStepEntity {
        val info = extractStepInfo(step)
        return RouteStepEntity(
            stepId = step.id.ifEmpty { UUID.randomUUID().toString().take(8) },
            routeId = routeId,
            stepIndex = index,
            type = info.type,
            summary = info.summary,
            screenshotRef = step.beforeScreenshotRef ?: "",
            locatorStrategy = info.locatorStrategy,
            locatorValue = info.locatorValue,
            locatorConfidence = step.confidence,
            fallbackStrategy = if (info.locatorStrategy == "coordinate") "" else "coordinate",
            fallbackValue = "",
            waitTimeMs = step.delayFromPreviousMs,
            riskLevel = detectRiskLevel(info.summary, info.type),
            source = "manual_recording"
        )
    }

    private fun extractStepInfo(step: RecordedStep): StepInfo {
        return when (val action = step.action) {
            is StepAction.Tap -> {
                val locatorValue = step.target?.let {
                    "${it.rawX},${it.rawY}"
                } ?: "${action.x},${action.y}"
                StepInfo("tap", "点击 ($locatorValue)", "coordinate", locatorValue)
            }
            is StepAction.LongPress -> {
                val locatorValue = "${action.x},${action.y}"
                StepInfo("tap", "长按 ($locatorValue) ${action.durationMs}ms", "coordinate", locatorValue)
            }
            is StepAction.Swipe -> StepInfo("swipe", "滑动 (${action.startX},${action.startY}) → (${action.endX},${action.endY})", "coordinate", "")
            is StepAction.Key -> {
                val type = when (action.keyName) {
                    "BACK" -> "back"
                    "HOME" -> "home"
                    else -> "key"
                }
                StepInfo(type, "按键 ${action.keyName}", "key", action.keyName)
            }
            is StepAction.TextInput -> StepInfo("input", "输入 '${action.text.take(30)}'", "text", action.text.take(30))
            is StepAction.Wait -> StepInfo("wait", "等待 ${action.durationMs}ms", "time", "${action.durationMs}")
            is StepAction.Screenshot -> StepInfo("screenshot", "截图", "", "")
            is StepAction.AppStart -> StepInfo("open_app", "打开 ${action.packageName}", "package", action.packageName)
        }
    }

    private fun detectRiskLevel(summary: String, type: String): String {
        val lower = summary.lowercase()
        return when {
            lower.contains("转账") || lower.contains("支付") || lower.contains("贷款") -> "critical"
            lower.contains("发送") || lower.contains("删除") || lower.contains("提交") ||
            lower.contains("下单") || lower.contains("注销") -> "high"
            lower.contains("确认") || lower.contains("同意") -> "medium"
            else -> "low"
        }
    }
}
