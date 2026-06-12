package com.smarttasker.core.adapter

import com.smarttasker.data.entity.RouteStepEntity
import com.smarttasker.data.entity.RouteVersionEntity
import org.junit.Assert.*
import org.junit.Test

class RouteAdapterTest {

    private val sampleRouteJson = """
    {
      "segments": [
        {
          "steps": [
            {"op":"LAUNCH","args":[],"locator":{"text":"淘宝"}},
            {"op":"TAP","args":[300,500],"locator":{"resource_id":"com.taobao:id/search_box"}},
            {"op":"INPUT","args":[],"locator":{"text":"搜索"}},
            {"op":"TAP","args":[],"locator":{"text":"收金币"}},
            {"op":"BACK","args":[]},
            {"op":"WAIT","args":[2000]}
          ]
        }
      ]
    }
    """.trimIndent()

    @Test
    fun `parseRoute returns ParsedRoute with correct step count`() {
        val result = RouteAdapter.parseRoute(sampleRouteJson, "task-1")
        assertNotNull(result)
        assertEquals(6, result!!.steps.size)
    }

    @Test
    fun `parseRoute maps op to correct step type`() {
        val result = RouteAdapter.parseRoute(sampleRouteJson, "task-1")!!
        assertEquals("open_app", result.steps[0].type)
        assertEquals("tap", result.steps[1].type)
        assertEquals("input", result.steps[2].type)
        assertEquals("tap", result.steps[3].type)
        assertEquals("back", result.steps[4].type)
        assertEquals("wait", result.steps[5].type)
    }

    @Test
    fun `parseRoute extracts locator strategy from locator field`() {
        val result = RouteAdapter.parseRoute(sampleRouteJson, "task-1")!!
        assertEquals("text", result.steps[0].locatorStrategy)
        assertEquals("resource_id", result.steps[1].locatorStrategy)
    }

    @Test
    fun `parseRoute uses coordinate fallback when no locator`() {
        val json = """{"segments":[{"steps":[{"op":"TAP","args":[100,200]}]}]}"""
        val result = RouteAdapter.parseRoute(json, "task-2")!!
        assertEquals("coordinate", result.steps[0].locatorStrategy)
        assertEquals("100,200", result.steps[0].locatorValue)
    }

    @Test
    fun `parseRoute returns null for invalid JSON`() {
        assertNull(RouteAdapter.parseRoute("not json", "task-1"))
    }

    @Test
    fun `parseRoute returns null when no segments key`() {
        assertNull(RouteAdapter.parseRoute("""{"other":[]}""", "task-1"))
    }

    @Test
    fun `parseRoute creates routeVersion with draft status`() {
        val result = RouteAdapter.parseRoute(sampleRouteJson, "task-1")!!
        assertEquals("draft", result.routeVersion.status)
        assertEquals("ai_learned", result.routeVersion.source)
        assertEquals("task-1", result.routeVersion.taskId)
    }

    @Test
    fun `parseRoute detects critical risk for payment keywords`() {
        val json = """{"segments":[{"steps":[{"op":"TAP","locator":{"text":"确认转账"}}]}]}"""
        val result = RouteAdapter.parseRoute(json, "task-1")!!
        assertEquals("critical", result.steps[0].riskLevel)
    }

    @Test
    fun `parseRoute detects high risk for submit keywords`() {
        val json = """{"segments":[{"steps":[{"op":"TAP","locator":{"text":"提交订单"}}]}]}"""
        val result = RouteAdapter.parseRoute(json, "task-1")!!
        assertEquals("high", result.steps[0].riskLevel)
    }

    @Test
    fun `parseRoute assigns low risk for normal actions`() {
        val json = """{"segments":[{"steps":[{"op":"TAP","locator":{"text":"查看详情"}}]}]}"""
        val result = RouteAdapter.parseRoute(json, "task-1")!!
        assertEquals("low", result.steps[0].riskLevel)
    }

    @Test
    fun `toRouteJson produces valid JSON with correct op`() {
        val steps = listOf(
            RouteStepEntity("s1", "r1", 1, "tap", "点击搜索", "text", "搜索", 0.85f, "", "", "low", "ai_learned"),
            RouteStepEntity("s2", "r1", 2, "input", "输入关键词", "resource_id", "search_input", 0.85f, "", "", "low", "ai_learned")
        )
        val json = RouteAdapter.toRouteJson(steps)
        assertTrue(json.contains("\"op\":\"TAP\""))
        assertTrue(json.contains("\"op\":\"INPUT\""))
        assertTrue(json.startsWith("{"))
        assertTrue(json.endsWith("}"))
    }

    @Test
    fun `round-trip parse then toRouteJson preserves op type`() {
        val result = RouteAdapter.parseRoute(sampleRouteJson, "task-1")!!
        val json = RouteAdapter.toRouteJson(result.steps)
        assertTrue(json.contains("\"op\":\"LAUNCH\""))
        assertTrue(json.contains("\"op\":\"BACK\""))
        assertTrue(json.contains("\"op\":\"WAIT\""))
    }

    @Test
    fun `parseRoute handles empty segments array`() {
        val json = """{"segments":[]}"""
        val result = RouteAdapter.parseRoute(json, "task-1")!!
        assertEquals(0, result.steps.size)
    }

    @Test
    fun `parseRoute assigns coordinate confidence 0_5 for coordinate locator`() {
        val json = """{"segments":[{"steps":[{"op":"TAP","args":[100,200]}]}]}"""
        val result = RouteAdapter.parseRoute(json, "task-1")!!
        assertEquals(0.5f, result.steps[0].locatorConfidence, 0.01f)
    }

    @Test
    fun `parseRoute assigns 0_85 confidence for text locator`() {
        val json = """{"segments":[{"steps":[{"op":"TAP","locator":{"text":"按钮"}}]}]}"""
        val result = RouteAdapter.parseRoute(json, "task-1")!!
        assertEquals(0.85f, result.steps[0].locatorConfidence, 0.01f)
    }
}
