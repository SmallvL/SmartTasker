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
        PermissionSnapshotEntity::class,
        TemplateEntity::class,
        TemplateStepEntity::class,
        TemplateVersionEntity::class
    ],
    version = 7,
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
    abstract fun templateDao(): TemplateDao

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

        // Migration from v6 to v7: add template tables
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `templates` (`templateId` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `category` TEXT NOT NULL, `icon` TEXT NOT NULL, `version` TEXT NOT NULL, `versionCode` INTEGER NOT NULL, `sourceTaskId` TEXT NOT NULL, `sourceRouteId` TEXT NOT NULL, `stepCount` INTEGER NOT NULL, `avgDurationMs` INTEGER NOT NULL, `successRate` REAL NOT NULL, `usageCount` INTEGER NOT NULL, `isBuiltIn` INTEGER NOT NULL, `tags` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`templateId`))")
                database.execSQL("CREATE TABLE IF NOT EXISTS `template_steps` (`stepId` TEXT NOT NULL, `templateId` TEXT NOT NULL, `versionCode` INTEGER NOT NULL, `stepIndex` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `type` TEXT NOT NULL, `summary` TEXT NOT NULL, `locatorStrategy` TEXT NOT NULL, `locatorValue` TEXT NOT NULL, `waitTimeMs` INTEGER NOT NULL, `riskLevel` TEXT NOT NULL, `notes` TEXT NOT NULL, PRIMARY KEY(`stepId`))")
                database.execSQL("CREATE TABLE IF NOT EXISTS `template_versions` (`versionId` TEXT NOT NULL, `templateId` TEXT NOT NULL, `versionCode` INTEGER NOT NULL, `version` TEXT NOT NULL, `changeSummary` TEXT NOT NULL, `stepCount` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`versionId`))")
            }
        }
 
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smarttask.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .fallbackToDestructiveMigration() // Fallback for dev
                .build().also { INSTANCE = it }
            }
        }
    }
}
