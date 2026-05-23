package com.smarttasker.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.smarttasker.model.ReplayStatus
import com.smarttasker.model.RouteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {
    /**
     * 获取所有路线
     */
    @Query("SELECT * FROM routes ORDER BY createdAt DESC")
    fun getAllRoutes(): Flow<List<RouteEntity>>
    
    /**
     * 根据 ID 获取路线
     */
    @Query("SELECT * FROM routes WHERE routeId = :routeId")
    suspend fun getRouteById(routeId: String): RouteEntity?
    
    /**
     * 根据脚本 ID 获取路线
     */
    @Query("SELECT * FROM routes WHERE scriptId = :scriptId ORDER BY createdAt DESC")
    fun getRoutesByScriptId(scriptId: String): Flow<List<RouteEntity>>
    
    /**
     * 根据包名获取路线
     */
    @Query("SELECT * FROM routes WHERE packageName = :packageName ORDER BY createdAt DESC")
    fun getRoutesByPackageName(packageName: String): Flow<List<RouteEntity>>
    
    /**
     * 根据任务描述搜索路线
     */
    @Query("SELECT * FROM routes WHERE userTask LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchRoutes(query: String): Flow<List<RouteEntity>>
    
    /**
     * 获取最近成功的路线
     */
    @Query("SELECT * FROM routes WHERE lastReplayStatus = :status ORDER BY createdAt DESC LIMIT :limit")
    fun getRoutesByStatus(status: ReplayStatus, limit: Int = 10): Flow<List<RouteEntity>>
    
    /**
     * 插入路线
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: RouteEntity)
    
    /**
     * 插入多个路线
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutes(routes: List<RouteEntity>)
    
    /**
     * 更新路线
     */
    @Update
    suspend fun updateRoute(route: RouteEntity)
    
    /**
     * 更新路线状态
     */
    @Query("UPDATE routes SET lastReplayStatus = :status WHERE routeId = :routeId")
    suspend fun updateRouteStatus(routeId: String, status: ReplayStatus)
    
    /**
     * 删除路线
     */
    @Delete
    suspend fun deleteRoute(route: RouteEntity)
    
    /**
     * 根据 ID 删除路线
     */
    @Query("DELETE FROM routes WHERE routeId = :routeId")
    suspend fun deleteRouteById(routeId: String)
    
    /**
     * 根据脚本 ID 删除路线
     */
    @Query("DELETE FROM routes WHERE scriptId = :scriptId")
    suspend fun deleteRoutesByScriptId(scriptId: String)
    
    /**
     * 删除所有路线
     */
    @Query("DELETE FROM routes")
    suspend fun deleteAllRoutes()
    
    /**
     * 获取路线数量
     */
    @Query("SELECT COUNT(*) FROM routes")
    suspend fun getRouteCount(): Int
    
    /**
     * 获取成功路线数量
     */
    @Query("SELECT COUNT(*) FROM routes WHERE lastReplayStatus = :status")
    suspend fun getRouteCountByStatus(status: ReplayStatus): Int
    
    /**
     * 获取所有包名
     */
    @Query("SELECT DISTINCT packageName FROM routes ORDER BY packageName")
    fun getAllPackageNames(): Flow<List<String>>
    
    /**
     * 获取所有任务描述
     */
    @Query("SELECT DISTINCT userTask FROM routes ORDER BY userTask")
    fun getAllUserTasks(): Flow<List<String>>
}
