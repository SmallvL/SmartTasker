package com.smarttasker.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smarttasker.data.entity.RouteStepEntity
import com.smarttasker.data.entity.RouteVersionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: RouteDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.routeDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    private fun sampleRoute(routeId: String = "r1", taskId: String = "t1") =
        RouteVersionEntity(routeId = routeId, taskId = taskId, source = "ai_learned")

    private fun sampleStep(stepId: String = "s1", routeId: String = "r1", index: Int = 1) =
        RouteStepEntity(stepId = stepId, routeId = routeId, stepIndex = index, type = "tap", summary = "点击", locatorStrategy = "text", locatorValue = "按钮")

    @Test
    fun insertAndGetRouteById() = runTest {
        dao.insertRouteVersion(sampleRoute())
        val result = dao.getRouteById("r1")
        assertNotNull(result)
        assertEquals("t1", result!!.taskId)
    }

    @Test
    fun insertAndGetSteps() = runTest {
        dao.insertRouteVersion(sampleRoute())
        dao.insertStep(sampleStep("s1", "r1", 1))
        dao.insertStep(sampleStep("s2", "r1", 2))
        val steps = dao.getStepsForRouteSync("r1")
        assertEquals(2, steps.size)
        assertEquals(1, steps[0].stepIndex)
        assertEquals(2, steps[1].stepIndex)
    }

    @Test
    fun deleteRouteAndSteps() = runTest {
        dao.insertRouteVersion(sampleRoute())
        dao.insertStep(sampleStep())
        dao.deleteAllSteps("r1")
        dao.deleteRouteVersion("r1")
        assertNull(dao.getRouteById("r1"))
        assertEquals(0, dao.getStepsForRouteSync("r1").size)
    }

    @Test
    fun updateStep() = runTest {
        dao.insertRouteVersion(sampleRoute())
        dao.insertStep(sampleStep())
        val step = dao.getStepsForRouteSync("r1")[0].copy(summary = "长按")
        dao.updateStep(step)
        val updated = dao.getStepsForRouteSync("r1")[0]
        assertEquals("长按", updated.summary)
    }

    @Test
    fun replaceAllStepsAtomic() = runTest {
        dao.insertRouteVersion(sampleRoute())
        dao.insertStep(sampleStep("s1", "r1", 1))
        dao.insertStep(sampleStep("s2", "r1", 2))
        val newSteps = listOf(
            sampleStep("s3", "r1", 1).copy(type = "swipe"),
            sampleStep("s4", "r1", 2).copy(type = "input"),
            sampleStep("s5", "r1", 3).copy(type = "back")
        )
        dao.replaceAllSteps("r1", newSteps)
        val steps = dao.getStepsForRouteSync("r1")
        assertEquals(3, steps.size)
        assertEquals("swipe", steps[0].type)
        assertEquals("input", steps[1].type)
        assertEquals("back", steps[2].type)
    }

    @Test
    fun reindexStep() = runTest {
        dao.insertRouteVersion(sampleRoute())
        dao.insertStep(sampleStep("s1", "r1", 1))
        dao.reindexStep("s1", 5)
        val step = dao.getStepsForRouteSync("r1")[0]
        assertEquals(5, step.stepIndex)
    }

    @Test
    fun getLatestPublishedRoute() = runTest {
        dao.insertRouteVersion(sampleRoute("r1").copy(status = "draft"))
        dao.insertRouteVersion(sampleRoute("r2").copy(status = "published", version = "2.0.0"))
        val result = dao.getLatestPublishedRoute("t1")
        assertNotNull(result)
        assertEquals("r2", result!!.routeId)
    }

    @Test
    fun toggleStepEnabled() = runTest {
        dao.insertRouteVersion(sampleRoute())
        dao.insertStep(sampleStep().copy(enabled = true))
        val step = dao.getStepsForRouteSync("r1")[0].copy(enabled = false)
        dao.updateStep(step)
        val result = dao.getStepsForRouteSync("r1")[0]
        assertFalse(result.enabled)
    }
}
