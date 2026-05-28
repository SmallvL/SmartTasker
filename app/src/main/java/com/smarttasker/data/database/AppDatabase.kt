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
    version = 6,
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

        // Migration from v3 to v4: add retryCount to run_records
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE run_records ADD COLUMN retryCount INTEGER NOT NULL DEFAULT 0")
            }
        }
 
        // Migration from v4 to v5: add screenshotPath to run_records
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE run_records ADD COLUMN screenshotPath TEXT")
            }
        }
 
        // Migration from v5 to v6: add new fields to trace_events
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE trace_events ADD COLUMN stepType TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE trace_events ADD COLUMN stepTarget TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE trace_events ADD COLUMN stepResult TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE trace_events ADD COLUMN durationMs INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE trace_events ADD COLUMN retryCount INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE trace_events ADD COLUMN screenshotPath TEXT")
            }
        }
 
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smarttask.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration() // Fallback for dev
                .build().also { INSTANCE = it }
            }
        }
    }
}
