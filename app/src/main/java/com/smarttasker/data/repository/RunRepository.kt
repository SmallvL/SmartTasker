package com.smarttasker.data.repository

import com.smarttasker.data.database.RunRecordDao
import com.smarttasker.data.entity.RunRecordEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.UUID

class RunRepository(private val dao: RunRecordDao) {
    fun getAllRuns(): Flow<List<RunRecordEntity>> = dao.getAllRuns()
    fun getRunsForTask(taskId: String): Flow<List<RunRecordEntity>> = dao.getRunsForTask(taskId)
    fun getRunsByStatus(status: String): Flow<List<RunRecordEntity>> = dao.getRunsByStatus(status)
    fun getRecentFailedRuns(limit: Int = 5): Flow<List<RunRecordEntity>> = dao.getRecentFailedRuns(limit)

    private fun todayStart(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun getTodaySuccessCount(): Flow<Int> = dao.getSuccessCountSince(todayStart())
    fun getTodayFailedCount(): Flow<Int> = dao.getFailedCountSince(todayStart())
    fun getTodayModelCalls(): Flow<Int?> = dao.getModelCallsSince(todayStart())

    suspend fun insertRun(run: RunRecordEntity) = dao.insertRun(run)

    suspend fun createRun(taskId: String, triggerType: String = "manual"): RunRecordEntity {
        val run = RunRecordEntity(
            runId = UUID.randomUUID().toString().take(8),
            taskId = taskId,
            triggerType = triggerType
        )
        dao.insertRun(run)
        return run
    }

    suspend fun completeRun(run: RunRecordEntity, status: String) {
        dao.updateRun(run.copy(
            status = status,
            endedAt = System.currentTimeMillis(),
            durationMs = System.currentTimeMillis() - run.startedAt
        ))
    }

    suspend fun failRun(run: RunRecordEntity, failedStepId: String, failureType: String, diagnosis: String, suggestion: String) {
        dao.updateRun(run.copy(
            status = "failed",
            endedAt = System.currentTimeMillis(),
            durationMs = System.currentTimeMillis() - run.startedAt,
            failedStepId = failedStepId,
            failureType = failureType,
            diagnosisSummary = diagnosis,
            diagnosisSuggestion = suggestion
        ))
    }
}
