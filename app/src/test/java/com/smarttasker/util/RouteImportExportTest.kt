package com.smarttasker.util

import com.smarttasker.data.entity.RouteStepEntity
import com.smarttasker.data.entity.RouteVersionEntity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test

class RouteImportExportTest {

    private val sampleRoute = RouteVersionEntity(
        routeId = "r1", taskId = "t1", version = "1.0.0",
        status = "published", source = "ai_learned"
    )

    private val sampleSteps = listOf(
        RouteStepEntity(stepId = "s1", routeId = "r1", stepIndex = 1, type = "tap",
            summary = "点击搜索", locatorStrategy = "text", locatorValue = "搜索",
            locatorConfidence = 0.85f, riskLevel = "low", source = "ai_learned"),
        RouteStepEntity(stepId = "s2", routeId = "r1", stepIndex = 2, type = "input",
            summary = "输入关键词", locatorStrategy = "resource_id", locatorValue = "search_input",
            locatorConfidence = 0.9f, fallbackStrategy = "coordinate", fallbackValue = "100,200",
            riskLevel = "low", source = "ai_learned")
    )

    @Test
    fun `exportToJson produces valid JSON with version field`() {
        val json = RouteImportExport.exportToJson(sampleRoute, sampleSteps)
        val root = JSONObject(json)
        assertEquals(1, root.getInt("version"))
        assertTrue(root.has("route"))
        assertTrue(root.has("steps"))
    }

    @Test
    fun `exportToJson includes route metadata`() {
        val json = RouteImportExport.exportToJson(sampleRoute, sampleSteps)
        val root = JSONObject(json)
        val route = root.getJSONObject("route")
        assertEquals("r1", route.getString("routeId"))
        assertEquals("t1", route.getString("taskId"))
        assertEquals("published", route.getString("status"))
    }

    @Test
    fun `exportToJson includes all steps`() {
        val json = RouteImportExport.exportToJson(sampleRoute, sampleSteps)
        val root = JSONObject(json)
        val steps = root.getJSONArray("steps")
        assertEquals(2, steps.length())
        assertEquals("tap", steps.getJSONObject(0).getString("type"))
        assertEquals("input", steps.getJSONObject(1).getString("type"))
    }

    @Test
    fun `exportToJson handles empty steps`() {
        val json = RouteImportExport.exportToJson(sampleRoute, emptyList())
        val root = JSONObject(json)
        assertEquals(0, root.getJSONArray("steps").length())
    }

    @Test
    fun `validateJson accepts valid export`() {
        val json = RouteImportExport.exportToJson(sampleRoute, sampleSteps)
        val result = RouteImportExport.validateJson(json)
        assertTrue(result.isValid)
        assertTrue(result.message.contains("2"))
    }

    @Test
    fun `validateJson rejects missing version`() {
        val json = """{"route":{"taskId":"t1"},"steps":[]}"""
        val result = RouteImportExport.validateJson(json)
        assertFalse(result.isValid)
        assertTrue(result.message.contains("version"))
    }

    @Test
    fun `validateJson rejects missing route`() {
        val json = """{"version":1,"steps":[]}"""
        val result = RouteImportExport.validateJson(json)
        assertFalse(result.isValid)
        assertTrue(result.message.contains("route"))
    }

    @Test
    fun `validateJson rejects missing steps`() {
        val json = """{"version":1,"route":{"taskId":"t1"}}"""
        val result = RouteImportExport.validateJson(json)
        assertFalse(result.isValid)
        assertTrue(result.message.contains("steps"))
    }

    @Test
    fun `validateJson rejects route without taskId`() {
        val json = """{"version":1,"route":{},"steps":[]}"""
        val result = RouteImportExport.validateJson(json)
        assertFalse(result.isValid)
        assertTrue(result.message.contains("taskId"))
    }

    @Test
    fun `validateJson rejects step without type`() {
        val json = """{"version":1,"route":{"taskId":"t1"},"steps":[{"stepIndex":1}]}"""
        val result = RouteImportExport.validateJson(json)
        assertFalse(result.isValid)
        assertTrue(result.message.contains("type"))
    }

    @Test
    fun `validateJson rejects step without stepIndex`() {
        val json = """{"version":1,"route":{"taskId":"t1"},"steps":[{"type":"tap"}]}"""
        val result = RouteImportExport.validateJson(json)
        assertFalse(result.isValid)
        assertTrue(result.message.contains("stepIndex"))
    }

    @Test
    fun `validateJson rejects invalid JSON`() {
        val result = RouteImportExport.validateJson("not json at all")
        assertFalse(result.isValid)
        assertTrue(result.message.contains("解析失败"))
    }

    @Ignore("Intent requires Android framework; returnDefaultValues stubs return null for getters")
    @Test
    fun `createExportIntent has correct type and title`() {
        val intent = RouteImportExport.createExportIntent("r1")
        assertEquals("application/json", intent.type)
        assertEquals("route_r1.json", intent.getStringExtra(android.content.Intent.EXTRA_TITLE))
    }

    @Ignore("Intent requires Android framework; returnDefaultValues stubs return null for getters")
    @Test
    fun `createImportIntent has correct type`() {
        val intent = RouteImportExport.createImportIntent()
        assertEquals("application/json", intent.type)
    }
}
