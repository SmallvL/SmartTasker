package com.smarttasker.data.database

import androidx.room.*
import com.smarttasker.data.entity.RouteVersionEntity
import com.smarttasker.data.entity.RouteStepEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {
    @Query("SELECT * FROM route_versions WHERE taskId = :taskId ORDER BY createdAt DESC")
    fun getRouteVersions(taskId: String): Flow<List<RouteVersionEntity>>

    @Query("SELECT * FROM route_versions WHERE taskId = :taskId AND status = 'published' ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestPublishedRoute(taskId: String): RouteVersionEntity?

    @Query("SELECT * FROM route_versions WHERE routeId = :routeId")
    suspend fun getRouteById(routeId: String): RouteVersionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRouteVersion(route: RouteVersionEntity)

    @Update
    suspend fun updateRouteVersion(route: RouteVersionEntity)

    @Query("SELECT * FROM route_steps WHERE routeId = :routeId ORDER BY stepIndex ASC")
    fun getStepsForRoute(routeId: String): Flow<List<RouteStepEntity>>

    @Query("SELECT * FROM route_steps WHERE routeId = :routeId ORDER BY stepIndex ASC")
    suspend fun getStepsForRouteSync(routeId: String): List<RouteStepEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStep(step: RouteStepEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSteps(steps: List<RouteStepEntity>)

    @Update
    suspend fun updateStep(step: RouteStepEntity)

    @Delete
    suspend fun deleteStep(step: RouteStepEntity)

    @Query("DELETE FROM route_steps WHERE routeId = :routeId")
    suspend fun deleteAllSteps(routeId: String)
}
