package com.smarttasker.data.repository

import com.smarttasker.data.database.RouteDao
import com.smarttasker.data.entity.RouteVersionEntity
import com.smarttasker.data.entity.RouteStepEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class RouteRepository(private val dao: RouteDao) {
    fun getRouteVersions(taskId: String): Flow<List<RouteVersionEntity>> = dao.getRouteVersions(taskId)
    fun getStepsForRoute(routeId: String): Flow<List<RouteStepEntity>> = dao.getStepsForRoute(routeId)

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
}
