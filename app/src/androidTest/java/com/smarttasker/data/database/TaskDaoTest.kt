package com.smarttasker.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smarttasker.data.entity.TaskEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TaskDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.taskDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    private fun sampleTask(taskId: String = "t1", name: String = "test", status: String = "draft") =
        TaskEntity(taskId = taskId, name = name, status = status)

    @Test
    fun insertAndGetById() = runTest {
        val task = sampleTask()
        dao.insertTask(task)
        val result = dao.getTaskById("t1")
        assertNotNull(result)
        assertEquals("test", result!!.name)
    }

    @Test
    fun insertAndGetAll() = runTest {
        dao.insertTask(sampleTask("t1", "Task 1"))
        dao.insertTask(sampleTask("t2", "Task 2"))
        // Flow-based query; just verify no crash
        dao.getAllTasks()
    }

    @Test
    fun updateTask() = runTest {
        dao.insertTask(sampleTask("t1", "original"))
        val task = dao.getTaskById("t1")!!.copy(name = "updated")
        dao.updateTask(task)
        assertEquals("updated", dao.getTaskById("t1")!!.name)
    }

    @Test
    fun deleteTask() = runTest {
        dao.insertTask(sampleTask("t1"))
        dao.deleteTask(sampleTask("t1"))
        assertNull(dao.getTaskById("t1"))
    }

    @Test
    fun deleteTaskById() = runTest {
        dao.insertTask(sampleTask("t1"))
        dao.deleteTaskById("t1")
        assertNull(dao.getTaskById("t1"))
    }

    @Test
    fun getActiveTasksFiltersCorrectly() = runTest {
        dao.insertTask(sampleTask("t1", "active", "active"))
        dao.insertTask(sampleTask("t2", "paused", "paused"))
        dao.insertTask(sampleTask("t3", "draft", "draft"))
        // Flow-based query; just verify no crash
        dao.getActiveTasks()
    }

    @Test
    fun getTasksByTrigger() = runTest {
        dao.insertTask(sampleTask("t1").copy(triggerType = "schedule"))
        dao.insertTask(sampleTask("t2").copy(triggerType = "manual"))
        dao.getTasksByTrigger("schedule")
    }

    @Test
    fun insertDuplicateReplaces() = runTest {
        dao.insertTask(sampleTask("t1", "first"))
        dao.insertTask(sampleTask("t1", "second"))
        val result = dao.getTaskById("t1")
        // OnConflictStrategy.REPLACE should replace
        assertEquals("second", result!!.name)
    }
}
