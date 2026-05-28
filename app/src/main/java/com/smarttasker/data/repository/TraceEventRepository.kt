package com.smarttasker.data.repository

import com.smarttasker.data.database.TraceDao
import com.smarttasker.data.entity.TraceEventEntity
import kotlinx.coroutines.flow.Flow

class TraceEventRepository(private val dao: TraceDao) {
    fun getEventsForRun(runId: String): Flow<List<TraceEventEntity>> = dao.getEventsForRun(runId)
    fun getErrorsForRun(runId: String): Flow<List<TraceEventEntity>> = dao.getErrorsForRun(runId)
    suspend fun insertEvent(event: TraceEventEntity) = dao.insertEvent(event)
    suspend fun insertEvents(events: List<TraceEventEntity>) = dao.insertEvents(events)
}
