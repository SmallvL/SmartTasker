package com.smarttasker.data.repository

import com.smarttasker.data.database.TaskDao
import com.smarttasker.data.entity.TaskEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class TaskRepositoryTest {

    private fun mockTask(taskId: String = "t1", name: String = "test", status: String = "draft") =
        TaskEntity(taskId = taskId, name = name, status = status)

    @Test
    fun `createTask generates UUID and calls insertTask`() = runTest {
        val dao = mock<TaskDao>()
        val repo = TaskRepository(dao)
        val task = repo.createTask("打开微信", triggerType = "schedule")
        assertEquals("打开微信", task.name)
        assertEquals("schedule", task.triggerType)
        assertTrue(task.taskId.isNotEmpty())
        verify(dao).insertTask(any())
    }

    @Test
    fun `activateTask updates status to active`() = runTest {
        val dao = mock<TaskDao>()
        val repo = TaskRepository(dao)
        val task = mockTask(status = "paused")
        repo.activateTask(task)
        val captured = argumentCaptor<TaskEntity>()
        verify(dao).updateTask(captured.capture())
        assertEquals("active", captured.lastValue.status)
    }

    @Test
    fun `pauseTask updates status to paused`() = runTest {
        val dao = mock<TaskDao>()
        val repo = TaskRepository(dao)
        val task = mockTask(status = "active")
        repo.pauseTask(task)
        val captured = argumentCaptor<TaskEntity>()
        verify(dao).updateTask(captured.capture())
        assertEquals("paused", captured.lastValue.status)
    }

    @Test
    fun `deleteTask calls dao deleteTask`() = runTest {
        val dao = mock<TaskDao>()
        val repo = TaskRepository(dao)
        val task = mockTask()
        repo.deleteTask(task)
        verify(dao).deleteTask(task)
    }

    @Test
    fun `deleteTaskById calls dao deleteTaskById`() = runTest {
        val dao = mock<TaskDao>()
        val repo = TaskRepository(dao)
        repo.deleteTaskById("t1")
        verify(dao).deleteTaskById("t1")
    }

    @Test
    fun `getTaskById returns null on exception`() = runTest {
        val dao = mock<TaskDao> {
            onBlocking { getTaskById("bad") } throw RuntimeException("db error")
        }
        val repo = TaskRepository(dao)
        assertNull(repo.getTaskById("bad"))
    }

    @Test
    fun `updateTask swallows exception`() = runTest {
        val dao = mock<TaskDao> {
            onBlocking { updateTask(any()) } throw RuntimeException("db error")
        }
        val repo = TaskRepository(dao)
        // Should not throw
        repo.updateTask(mockTask())
    }

    @Test
    fun `insertTask swallows exception`() = runTest {
        val dao = mock<TaskDao> {
            onBlocking { insertTask(any()) } throw RuntimeException("db error")
        }
        val repo = TaskRepository(dao)
        repo.insertTask(mockTask())
    }

    @Test
    fun `getAllTasks delegates to dao`() {
        val dao = mock<TaskDao>()
        val repo = TaskRepository(dao)
        repo.getAllTasks()
        verify(dao).getAllTasks()
    }

    @Test
    fun `getActiveTasks delegates to dao`() {
        val dao = mock<TaskDao>()
        val repo = TaskRepository(dao)
        repo.getActiveTasks()
        verify(dao).getActiveTasks()
    }

    @Test
    fun `getTasksByTrigger delegates to dao`() {
        val dao = mock<TaskDao>()
        val repo = TaskRepository(dao)
        repo.getTasksByTrigger("schedule")
        verify(dao).getTasksByTrigger("schedule")
    }
}
