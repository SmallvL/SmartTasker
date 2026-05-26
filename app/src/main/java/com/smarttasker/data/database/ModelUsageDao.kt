package com.smarttasker.data.database

import androidx.room.*
import com.smarttasker.data.entity.ModelUsageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelUsageDao {
    @Query("SELECT * FROM model_usage ORDER BY createdAt DESC")
    fun getAllUsage(): Flow<List<ModelUsageEntity>>

    @Query("SELECT SUM(costCents) FROM model_usage WHERE createdAt > :since")
    fun getTotalCostSince(since: Long): Flow<Float?>

    @Query("SELECT COUNT(*) FROM model_usage WHERE createdAt > :since")
    fun getCallCountSince(since: Long): Flow<Int?>

    @Insert
    suspend fun insertUsage(usage: ModelUsageEntity)
}
