package com.smarttasker.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.smarttasker.model.RouteEntity
import com.smarttasker.model.ScriptEntity
import com.smarttasker.model.ScriptStepEntity
import com.smarttasker.model.TaskEntity
import com.smarttasker.model.TaskExecutionEntity

@Database(
    entities = [
        TaskEntity::class,
        TaskExecutionEntity::class,
        ScriptEntity::class,
        ScriptStepEntity::class,
        RouteEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun scriptDao(): ScriptDao
    abstract fun routeDao(): RouteDao
}

/**
 * 类型转换器
 */
class Converters {
    @androidx.room.TypeConverter
    fun fromTaskType(value: com.smarttasker.model.TaskType): String {
        return value.name
    }
    
    @androidx.room.TypeConverter
    fun toTaskType(value: String): com.smarttasker.model.TaskType {
        return com.smarttasker.model.TaskType.valueOf(value)
    }
    
    @androidx.room.TypeConverter
    fun fromTriggerType(value: com.smarttasker.model.TriggerType?): String? {
        return value?.name
    }
    
    @androidx.room.TypeConverter
    fun toTriggerType(value: String?): com.smarttasker.model.TriggerType? {
        return value?.let { com.smarttasker.model.TriggerType.valueOf(it) }
    }
    
    @androidx.room.TypeConverter
    fun fromExecutionStatus(value: com.smarttasker.model.ExecutionStatus): String {
        return value.name
    }
    
    @androidx.room.TypeConverter
    fun toExecutionStatus(value: String): com.smarttasker.model.ExecutionStatus {
        return com.smarttasker.model.ExecutionStatus.valueOf(value)
    }
    
    @androidx.room.TypeConverter
    fun fromStepOperation(value: com.smarttasker.model.StepOperation): String {
        return value.name
    }
    
    @androidx.room.TypeConverter
    fun toStepOperation(value: String): com.smarttasker.model.StepOperation {
        return com.smarttasker.model.StepOperation.valueOf(value)
    }
    
    @androidx.room.TypeConverter
    fun fromReplayStatus(value: com.smarttasker.model.ReplayStatus): String {
        return value.name
    }
    
    @androidx.room.TypeConverter
    fun toReplayStatus(value: String): com.smarttasker.model.ReplayStatus {
        return com.smarttasker.model.ReplayStatus.valueOf(value)
    }
}
