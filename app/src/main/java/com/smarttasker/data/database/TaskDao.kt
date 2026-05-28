package com.smarttasker.data.database

import androidx.room.*
import com.smarttasker.data.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY updatedAt DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = 'active' ORDER BY updatedAt DESC")
    fun getActiveTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = 'paused'")
    fun getPausedTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE triggerType = :type ORDER BY updatedAt DESC")
    fun getTasksByTrigger(type: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE triggerType = :type ORDER BY updatedAt DESC")
    suspend fun getTasksByTriggerSync(type: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE taskId = :taskId")
    suspend fun getTaskById(taskId: String): TaskEntity?

    @Query("SELECT * FROM tasks ORDER BY updatedAt DESC LIMIT :limit")
    fun getRecentTasks(limit: Int = 5): Flow<List<TaskEntity>>

    @Query("SELECT COUNT(*) FROM tasks WHERE status = 'active'")
    fun getActiveTaskCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE taskId = :taskId")
    suspend fun deleteTaskById(taskId: String)
}
