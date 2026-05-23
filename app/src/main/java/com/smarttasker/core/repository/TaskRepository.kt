package com.smarttasker.core.repository

import com.smarttasker.core.database.TaskDao
import com.smarttasker.model.TaskEntity
import com.smarttasker.model.TaskExecutionEntity
import com.smarttasker.model.TaskItem
import com.smarttasker.model.TaskType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 任务仓库
 */
@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao
) {
    /**
     * 获取所有任务
     */
    fun getAllTasks(): Flow<List<TaskItem>> {
        return taskDao.getAllTasks().map { entities ->
            entities.map { it.toTaskItem() }
        }
    }
    
    /**
     * 根据 ID 获取任务
     */
    suspend fun getTaskById(taskId: String): TaskEntity? {
        return taskDao.getTaskById(taskId)
    }
    
    /**
     * 获取启用的任务
     */
    fun getEnabledTasks(): Flow<List<TaskItem>> {
        return taskDao.getEnabledTasks().map { entities ->
            entities.map { it.toTaskItem() }
        }
    }
    
    /**
     * 插入任务
     */
    suspend fun insertTask(task: TaskEntity) {
        taskDao.insertTask(task)
    }
    
    /**
     * 创建新任务
     */
    suspend fun createTask(
        name: String,
        description: String,
        type: TaskType,
        isEnabled: Boolean = true,
        cronExpression: String? = null,
        intervalMinutes: Long? = null
    ): TaskEntity {
        val task = TaskEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            type = type,
            isEnabled = isEnabled,
            cronExpression = cronExpression,
            intervalMinutes = intervalMinutes
        )
        taskDao.insertTask(task)
        return task
    }
    
    /**
     * 更新任务
     */
    suspend fun updateTask(task: TaskEntity) {
        taskDao.updateTask(task)
    }
    
    /**
     * 删除任务
     */
    suspend fun deleteTask(taskId: String) {
        taskDao.deleteTaskById(taskId)
    }
    
    /**
     * 切换任务启用状态
     */
    suspend fun toggleTaskEnabled(taskId: String) {
        val task = taskDao.getTaskById(taskId)
        task?.let {
            taskDao.updateTask(it.copy(isEnabled = !it.isEnabled))
        }
    }
    
    /**
     * 获取任务执行记录
     */
    fun getTaskExecutions(taskId: String): Flow<List<TaskExecutionEntity>> {
        return taskDao.getTaskExecutions(taskId)
    }
    
    /**
     * 插入执行记录
     */
    suspend fun insertExecution(execution: TaskExecutionEntity) {
        taskDao.insertExecution(execution)
    }
    
    /**
     * 获取任务数量
     */
    suspend fun getTaskCount(): Int {
        return taskDao.getTaskCount()
    }
    
    /**
     * 获取启用的任务数量
     */
    suspend fun getEnabledTaskCount(): Int {
        return taskDao.getEnabledTaskCount()
    }
    
    /**
     * TaskEntity 转换为 TaskItem
     */
    private fun TaskEntity.toTaskItem(): TaskItem {
        return TaskItem(
            id = id,
            name = name,
            description = description,
            type = type,
            isEnabled = isEnabled,
            lastRunTime = null // TODO: 从执行记录中获取
        )
    }
}