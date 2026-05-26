package com.smarttasker.data.database

import androidx.room.*
import com.smarttasker.data.entity.TraceEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TraceDao {
    @Query("SELECT * FROM trace_events WHERE runId = :runId ORDER BY timestamp ASC")
    fun getEventsForRun(runId: String): Flow<List<TraceEventEntity>>

    @Query("SELECT * FROM trace_events WHERE runId = :runId AND level = 'error' ORDER BY timestamp ASC")
    fun getErrorsForRun(runId: String): Flow<List<TraceEventEntity>>

    @Insert
    suspend fun insertEvent(event: TraceEventEntity)

    @Insert
    suspend fun insertEvents(events: List<TraceEventEntity>)
}
