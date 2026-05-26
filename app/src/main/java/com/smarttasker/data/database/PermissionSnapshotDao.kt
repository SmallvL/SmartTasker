package com.smarttasker.data.database

import androidx.room.*
import com.smarttasker.data.entity.PermissionSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PermissionSnapshotDao {
    @Query("SELECT * FROM permission_snapshots ORDER BY checkedAt DESC LIMIT 1")
    fun getLatestSnapshot(): Flow<PermissionSnapshotEntity?>

    @Query("SELECT * FROM permission_snapshots ORDER BY checkedAt DESC LIMIT :limit")
    fun getRecentSnapshots(limit: Int = 10): Flow<List<PermissionSnapshotEntity>>

    @Insert
    suspend fun insertSnapshot(snapshot: PermissionSnapshotEntity)
}
