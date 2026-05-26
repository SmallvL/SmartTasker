package com.smarttasker.data.database

import androidx.room.*
import com.smarttasker.data.entity.RunRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RunRecordDao {
    @Query("SELECT * FROM run_records ORDER BY startedAt DESC")
    fun getAllRuns(): Flow<List<RunRecordEntity>>

    @Query("SELECT * FROM run_records WHERE taskId = :taskId ORDER BY startedAt DESC")
    fun getRunsForTask(taskId: String): Flow<List<RunRecordEntity>>

    @Query("SELECT * FROM run_records WHERE status = :status ORDER BY startedAt DESC")
    fun getRunsByStatus(status: String): Flow<List<RunRecordEntity>>

    @Query("SELECT * FROM run_records WHERE startedAt > :since ORDER BY startedAt DESC")
    fun getRunsSince(since: Long): Flow<List<RunRecordEntity>>

    @Query("SELECT * FROM run_records WHERE runId = :runId")
    suspend fun getRunById(runId: String): RunRecordEntity?

    @Query("SELECT COUNT(*) FROM run_records WHERE startedAt > :since AND status = 'success'")
    fun getSuccessCountSince(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM run_records WHERE startedAt > :since AND status = 'failed'")
    fun getFailedCountSince(since: Long): Flow<Int>

    @Query("SELECT SUM(modelCalls) FROM run_records WHERE startedAt > :since")
    fun getModelCallsSince(since: Long): Flow<Int?>

    @Query("SELECT * FROM run_records WHERE status = 'failed' ORDER BY startedAt DESC LIMIT :limit")
    fun getRecentFailedRuns(limit: Int = 5): Flow<List<RunRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: RunRecordEntity)

    @Update
    suspend fun updateRun(run: RunRecordEntity)
}
