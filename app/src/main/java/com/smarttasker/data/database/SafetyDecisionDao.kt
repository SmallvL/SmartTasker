package com.smarttasker.data.database

import androidx.room.*
import com.smarttasker.data.entity.SafetyDecisionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SafetyDecisionDao {
    @Query("SELECT * FROM safety_decisions WHERE taskId = :taskId ORDER BY createdAt DESC")
    fun getDecisionsForTask(taskId: String): Flow<List<SafetyDecisionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDecision(decision: SafetyDecisionEntity)
}
