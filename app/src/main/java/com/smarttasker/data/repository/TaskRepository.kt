package com.smarttasker.data.repository

import android.util.Log
import com.smarttasker.data.database.TaskDao
import com.smarttasker.data.entity.TaskEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

private const val TAG = "TaskRepository"

class TaskRepository(private val dao: TaskDao) {
    fun getAllTasks(): Flow<List<TaskEntity>> = dao.getAllTasks()
    fun getActiveTasks(): Flow<List<TaskEntity>> = dao.getActiveTasks()
    fun getPausedTasks(): Flow<List<TaskEntity>> = dao.getPausedTasks()
    fun getTasksByTrigger(type: String): Flow<List<TaskEntity>> = dao.getTasksByTrigger(type)
    fun getRecentTasks(limit: Int = 5): Flow<List<TaskEntity>> = dao.getRecentTasks(limit)
    fun getActiveTaskCount(): Flow<Int> = dao.getActiveTaskCount()

    suspend fun getTaskById(id: String): TaskEntity? = try {
        dao.getTaskById(id)
    } catch (e: Exception) {
        Log.e(TAG, "getTaskById failed: $id", e)
        null
    }

    suspend fun createTask(
        name: String,
        description: String = "",
        targetApp: String = "",
        targetPackage: String = "",
        triggerType: String = "manual"
    ): TaskEntity {
        val task = TaskEntity(
            taskId = UUID.randomUUID().toString().take(8),
            name = name,
            description = description,
            targetAppName = targetApp,
            targetPackage = targetPackage,
            triggerType = triggerType
        )
        dao.insertTask(task)
        return task
    }

    suspend fun updateTask(task: TaskEntity) {
        try {
            dao.updateTask(task.copy(updatedAt = System.currentTimeMillis()))
        } catch (e: Exception) {
            Log.e(TAG, "updateTask failed: ${task.taskId}", e)
        }
    }

    suspend fun deleteTask(task: TaskEntity) {
        try {
            dao.deleteTask(task)
        } catch (e: Exception) {
            Log.e(TAG, "deleteTask failed: ${task.taskId}", e)
        }
    }

    suspend fun insertTask(task: TaskEntity) {
        try {
            dao.insertTask(task)
        } catch (e: Exception) {
            Log.e(TAG, "insertTask failed: ${task.taskId}", e)
        }
    }

    suspend fun deleteTaskById(id: String) {
        try {
            dao.deleteTaskById(id)
        } catch (e: Exception) {
            Log.e(TAG, "deleteTaskById failed: $id", e)
        }
    }

    suspend fun activateTask(task: TaskEntity) {
        try {
            dao.updateTask(task.copy(status = "active", updatedAt = System.currentTimeMillis()))
        } catch (e: Exception) {
            Log.e(TAG, "activateTask failed: ${task.taskId}", e)
        }
    }

    suspend fun pauseTask(task: TaskEntity) {
        try {
            dao.updateTask(task.copy(status = "paused", updatedAt = System.currentTimeMillis()))
        } catch (e: Exception) {
            Log.e(TAG, "pauseTask failed: ${task.taskId}", e)
        }
    }
}
