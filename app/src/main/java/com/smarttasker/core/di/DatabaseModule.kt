package com.smarttasker.core.di

import android.content.Context
import androidx.room.Room
import com.smarttasker.core.database.AppDatabase
import com.smarttasker.core.database.RouteDao
import com.smarttasker.core.database.ScriptDao
import com.smarttasker.core.database.TaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "smarttasker_database"
        )
        .fallbackToDestructiveMigration()
        .build()
    }
    
    @Provides
    @Singleton
    fun provideTaskDao(database: AppDatabase): TaskDao {
        return database.taskDao()
    }
    
    @Provides
    @Singleton
    fun provideScriptDao(database: AppDatabase): ScriptDao {
        return database.scriptDao()
    }
    
    @Provides
    @Singleton
    fun provideRouteDao(database: AppDatabase): RouteDao {
        return database.routeDao()
    }
}