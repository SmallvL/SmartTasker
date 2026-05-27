package com.smarttasker.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smarttasker.data.entity.TaskEntity
import com.smarttasker.data.repository.TaskRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
 
/**
 * ViewModel for task list management.
 * Bridges Room DB (TaskRepository) ↔ Compose UI (TasksPage).
 */
class TaskViewModel(private val taskRepo: TaskRepository) : ViewModel() {

    // ===== State =====

    private val _currentFilter = MutableStateFlow(AutoLxbTaskFilter.ALL)
    val currentFilter: StateFlow<AutoLxbTaskFilter> = _currentFilter.asStateFlow()

    // All tasks from DB
    private val allTasks: StateFlow<List<TaskEntity>> = taskRepo.getAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered for display
    val filteredTasks: StateFlow<List<TaskItem>> = combine(
        allTasks, _currentFilter
    ) { tasks, filter ->
        tasks.map { it.toTaskItem() }.filterBy(filter)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Task counts per filter
    val taskCounts: StateFlow<Map<AutoLxbTaskFilter, Int>> = allTasks.map { tasks ->
        val items = tasks.map { it.toTaskItem() }
        mapOf(
            AutoLxbTaskFilter.ALL to items.size,
            AutoLxbTaskFilter.QUICK to items.count { it.type == TaskType.QUICK },
            AutoLxbTaskFilter.SCHEDULED to items.count { it.type == TaskType.SCHEDULED },
            AutoLxbTaskFilter.NOTIFICATION to items.count { it.type == TaskType.NOTIFICATION },
            AutoLxbTaskFilter.RUNNING to items.count { it.status == AutoLxbTaskItemStatus.RUNNING },
            AutoLxbTaskFilter.COMPLETED to items.count { it.status == AutoLxbTaskItemStatus.SUCCESS },
            AutoLxbTaskFilter.FAILED to items.count { it.status == AutoLxbTaskItemStatus.FAILED }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Active task count
    val activeTaskCount: StateFlow<Int> = taskRepo.getActiveTaskCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Recent tasks (for home screen)
    val recentTasks: StateFlow<List<TaskEntity>> = taskRepo.getRecentTasks(5)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ===== Actions =====

    fun setFilter(filter: AutoLxbTaskFilter) {
        _currentFilter.value = filter
    }

    suspend fun createQuickTask(input: String): TaskEntity {
        return taskRepo.createTask(
            name = input.take(20).ifBlank { "快速任务" },
            description = input,
            triggerType = "manual"
        )
    }

    fun activateTask(taskId: String) {
        viewModelScope.launch {
            taskRepo.getTaskById(taskId)?.let { taskRepo.activateTask(it) }
        }
    }

    fun pauseTask(taskId: String) {
        viewModelScope.launch {
            taskRepo.getTaskById(taskId)?.let { taskRepo.pauseTask(it) }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            taskRepo.deleteTaskById(taskId)
        }
    }

    fun getTaskById(taskId: String): TaskEntity? {
        // Synchronous get for navigation callbacks
        // In Compose, prefer LaunchedEffect + collectAsState
        return null // Use taskRepo.getTaskById(taskId) in LaunchedEffect
    }

    // ===== Factory =====

    class Factory(private val repo: TaskRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TaskViewModel(repo) as T
        }
    }
}

// ===== Extensions =====

private fun TaskEntity.toTaskItem(): TaskItem {
    val type = when (triggerType) {
        "schedule" -> TaskType.SCHEDULED
        "notification" -> TaskType.NOTIFICATION
        else -> TaskType.QUICK
    }
    val status = when (this.status) {
        "active" -> AutoLxbTaskItemStatus.RUNNING
        "paused" -> AutoLxbTaskItemStatus.PAUSED
        "completed" -> AutoLxbTaskItemStatus.SUCCESS
        else -> when (triggerType) {
            "schedule" -> AutoLxbTaskItemStatus.SCHEDULED
            else -> AutoLxbTaskItemStatus.IDLE
        }
    }
    return TaskItem(
        id = taskId,
        name = name,
        description = description.ifBlank { "点击查看详情" },
        icon = when (type) {
            TaskType.SCHEDULED -> "⏰"
            TaskType.NOTIFICATION -> "🔔"
            else -> "⚡"
        },
        type = type,
        status = status,
        lastRunTime = null,
        nextRunTime = if (triggerType == "schedule") triggerTime else null,
        runCount = 0,
        successRate = 0f
    )
}

private fun List<TaskItem>.filterBy(filter: AutoLxbTaskFilter): List<TaskItem> = when (filter) {
    AutoLxbTaskFilter.ALL -> this
    AutoLxbTaskFilter.QUICK -> filter { it.type == TaskType.QUICK }
    AutoLxbTaskFilter.SCHEDULED -> filter { it.type == TaskType.SCHEDULED }
    AutoLxbTaskFilter.NOTIFICATION -> filter { it.type == TaskType.NOTIFICATION }
    AutoLxbTaskFilter.RUNNING -> filter { it.status == AutoLxbTaskItemStatus.RUNNING }
    AutoLxbTaskFilter.COMPLETED -> filter { it.status == AutoLxbTaskItemStatus.SUCCESS }
    AutoLxbTaskFilter.FAILED -> filter { it.status == AutoLxbTaskItemStatus.FAILED }
}
