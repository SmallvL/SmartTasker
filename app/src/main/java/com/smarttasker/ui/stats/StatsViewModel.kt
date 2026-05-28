package com.smarttasker.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smarttasker.data.entity.RunRecordEntity
import com.smarttasker.data.repository.RunRepository
import com.smarttasker.data.repository.TaskRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 统计数据 UI 状态
 */
data class StatsUiState(
    val isLoading: Boolean = true,
    val totalRuns: Int = 0,
    val successRuns: Int = 0,
    val failedRuns: Int = 0,
    val successRate: Float = 0f,
    val avgDurationMs: Long = 0,
    val totalModelCalls: Int = 0,
    val recentRuns: List<RunRecordEntity> = emptyList(),
    val taskStats: List<TaskStat> = emptyList(),
    val dailyStats: List<DailyStat> = emptyList(),
    val error: String? = null
)

/**
 * 任务统计
 */
data class TaskStat(
    val taskId: String,
    val taskName: String,
    val totalRuns: Int,
    val successRuns: Int,
    val successRate: Float,
    val avgDurationMs: Long
)

/**
 * 每日统计
 */
data class DailyStat(
    val date: String,
    val totalRuns: Int,
    val successRuns: Int,
    val failedRuns: Int
)

/**
 * 统计 ViewModel
 */
class StatsViewModel(
    application: Application,
    private val runRepo: RunRepository,
    private val taskRepo: TaskRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    /**
     * 加载统计数据
     */
    private fun loadStats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                // 获取所有运行记录
                val allRuns = runRepo.getAllRuns().first()
                
                // 计算概览统计
                val totalRuns = allRuns.size
                val successRuns = allRuns.count { it.status == "success" }
                val failedRuns = allRuns.count { it.status == "failed" }
                val successRate = if (totalRuns > 0) successRuns.toFloat() / totalRuns else 0f
                
                // 计算平均耗时
                val completedRuns = allRuns.filter { it.durationMs > 0 }
                val avgDurationMs = if (completedRuns.isNotEmpty()) {
                    completedRuns.sumOf { it.durationMs } / completedRuns.size
                } else 0
                
                // 计算总模型调用
                val totalModelCalls = allRuns.sumOf { it.modelCalls }
                
                // 获取最近运行
                val recentRuns = allRuns.sortedByDescending { it.startedAt }.take(10)
                
                // 计算任务统计
                val taskStats = calculateTaskStats(allRuns)
                
                // 计算每日统计
                val dailyStats = calculateDailyStats(allRuns)
                
                _uiState.value = StatsUiState(
                    isLoading = false,
                    totalRuns = totalRuns,
                    successRuns = successRuns,
                    failedRuns = failedRuns,
                    successRate = successRate,
                    avgDurationMs = avgDurationMs,
                    totalModelCalls = totalModelCalls,
                    recentRuns = recentRuns,
                    taskStats = taskStats,
                    dailyStats = dailyStats
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "加载统计数据失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 计算任务统计
     */
    private suspend fun calculateTaskStats(runs: List<RunRecordEntity>): List<TaskStat> {
        val taskGroups = runs.groupBy { it.taskId }
        
        return taskGroups.map { (taskId, taskRuns) ->
            val task = taskRepo.getTaskById(taskId)
            val totalRuns = taskRuns.size
            val successRuns = taskRuns.count { it.status == "success" }
            val successRate = if (totalRuns > 0) successRuns.toFloat() / totalRuns else 0f
            
            val completedRuns = taskRuns.filter { it.durationMs > 0 }
            val avgDurationMs = if (completedRuns.isNotEmpty()) {
                completedRuns.sumOf { it.durationMs } / completedRuns.size
            } else 0
            
            TaskStat(
                taskId = taskId,
                taskName = task?.name ?: "未知任务",
                totalRuns = totalRuns,
                successRuns = successRuns,
                successRate = successRate,
                avgDurationMs = avgDurationMs
            )
        }.sortedByDescending { it.totalRuns }
    }

    /**
     * 计算每日统计
     */
    private fun calculateDailyStats(runs: List<RunRecordEntity>): List<DailyStat> {
        val dateFormat = java.text.SimpleDateFormat("MM/dd", java.util.Locale.getDefault())
        
        val dailyGroups = runs.groupBy { run ->
            dateFormat.format(java.util.Date(run.startedAt))
        }
        
        return dailyGroups.map { (date, dayRuns) ->
            DailyStat(
                date = date,
                totalRuns = dayRuns.size,
                successRuns = dayRuns.count { it.status == "success" },
                failedRuns = dayRuns.count { it.status == "failed" }
            )
        }.sortedByDescending { it.date }.take(7)
    }

    /**
     * 刷新数据
     */
    fun refresh() {
        loadStats()
    }

    /**
     * 清除错误
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
