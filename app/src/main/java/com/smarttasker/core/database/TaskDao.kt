package com.smarttasker.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.smarttasker.model.TaskEntity
import com.smarttasker.model.TaskExecutionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    /**
     * 获取所有任务
     */
    @Query("SELECT * FROM tasks ORDER BY updatedAt DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>
    
    /**
     * 根据 ID 获取任务
     */
    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: String): TaskEntity?
    
    /**
     * 根据类型获取任务
     */
    @Query("SELECT * FROM tasks WHERE type = :type ORDER BY updatedAt DESC")
    fun getTasksByType(type: com.smarttasker.model.TaskType): Flow<List<TaskEntity>>
    
    /**
     * 获取启用的任务
     */
    @Query("SELECT * FROM tasks WHERE isEnabled = 1 ORDER BY updatedAt DESC")
    fun getEnabledTasks(): Flow<List<TaskEntity>>
    
    /**
     * 插入任务
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)
    
    /**
     * 插入多个任务
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)
    
    /**
     * 更新任务
     */
    @Update
    suspend fun updateTask(task: TaskEntity)
    
    /**
     * 删除任务
     */
    @Delete
    suspend fun deleteTask(task: TaskEntity)
    
    /**
     * 根据 ID 删除任务
     */
    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTaskById(taskId: String)
    
    /**
     * 删除所有任务
     */
    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()
    
    /**
     * 获取任务数量
     */
    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun getTaskCount(): Int
    
    /**
     * 获取启用的任务数量
     */
    @Query("SELECT COUNT(*) FROM tasks WHERE isEnabled = 1")
    suspend fun getEnabledTaskCount(): Int
    
    // ============================================================
    // 任务执行记录
    // ============================================================
    
    /**
     * 获取任务的执行记录
     */
    @Query("SELECT * FROM task_executions WHERE taskId = :taskId ORDER BY startTime DESC")
    fun getTaskExecutions(taskId: String): Flow<List<TaskExecutionEntity>>
    
    /**
     * 获取最近的执行记录
     */
    @Query("SELECT * FROM task_executions ORDER BY startTime DESC LIMIT :limit")
    fun getRecentExecutions(limit: Int = 10): Flow<List<TaskExecutionEntity>>
    
    /**
     * 插入执行记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExecution(execution: TaskExecutionEntity)
    
    /**
     * 更新执行记录
     */
    @Update
    suspend fun updateExecution(execution: TaskExecutionEntity)
    
    /**
     * 删除执行记录
     */
    @Delete
    suspend fun deleteExecution(execution: TaskExecutionEntity)
    
    /**
     * 删除任务的所有执行记录
     */
    @Query("DELETE FROM task_executions WHERE taskId = :taskId")
    suspend fun deleteTaskExecutions(taskId: String)
    
    /**
     * 删除所有执行记录
     */
    @Query("DELETE FROM task_executions")
    suspend fun deleteAllExecutions()
}
