package com.smarttasker.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.smarttasker.data.entity.*

@Database(
    entities = [
        TaskEntity::class,
        RouteVersionEntity::class,
        RouteStepEntity::class,
        RunRecordEntity::class,
        TraceEventEntity::class,
        SafetyDecisionEntity::class,
        ModelUsageEntity::class,
        PermissionSnapshotEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun routeDao(): RouteDao
    abstract fun runRecordDao(): RunRecordDao
    abstract fun traceDao(): TraceDao
    abstract fun safetyDecisionDao(): SafetyDecisionDao
    abstract fun modelUsageDao(): ModelUsageDao
    abstract fun permissionSnapshotDao(): PermissionSnapshotDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        
        // Migration from v1 to v2: add routeSnapshot to run_records
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE run_records ADD COLUMN routeSnapshot TEXT NOT NULL DEFAULT ''")
            }
        }
        
        // Migration from v2 to v3: add waitTimeMs to route_steps
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE route_steps ADD COLUMN waitTimeMs INTEGER NOT NULL DEFAULT 1000")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smarttask.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration() // Fallback for dev
                .build().also { INSTANCE = it }
            }
        }
    }
}
