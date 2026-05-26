package com.smarttasker.data.repository

import com.smarttasker.data.database.TaskDao
import com.smarttasker.data.entity.TaskEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class TaskRepository(private val dao: TaskDao) {
    fun getAllTasks(): Flow<List<TaskEntity>> = dao.getAllTasks()
    fun getActiveTasks(): Flow<List<TaskEntity>> = dao.getActiveTasks()
    fun getPausedTasks(): Flow<List<TaskEntity>> = dao.getPausedTasks()
    fun getTasksByTrigger(type: String): Flow<List<TaskEntity>> = dao.getTasksByTrigger(type)
    fun getRecentTasks(limit: Int = 5): Flow<List<TaskEntity>> = dao.getRecentTasks(limit)
    fun getActiveTaskCount(): Flow<Int> = dao.getActiveTaskCount()

    suspend fun getTaskById(id: String): TaskEntity? = dao.getTaskById(id)

    suspend fun createTask(name: String, description: String = "", targetApp: String = "", targetPackage: String = "", triggerType: String = "manual"): TaskEntity {
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

    suspend fun updateTask(task: TaskEntity) = dao.updateTask(task.copy(updatedAt = System.currentTimeMillis()))
    suspend fun deleteTask(task: TaskEntity) = dao.deleteTask(task)
    suspend fun insertTask(task: TaskEntity) = dao.insertTask(task)
    suspend fun deleteTaskById(id: String) = dao.deleteTaskById(id)
    suspend fun activateTask(task: TaskEntity) = dao.updateTask(task.copy(status = "active", updatedAt = System.currentTimeMillis()))
    suspend fun pauseTask(task: TaskEntity) = dao.updateTask(task.copy(status = "paused", updatedAt = System.currentTimeMillis()))
}
