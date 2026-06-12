package com.smarttasker.core.adapter

import org.junit.Assert.*
import org.junit.Test

class TraceAdapterTest {

    @Test
    fun `parseTrace with task_start and task_end success`() {
        val lines = listOf(
            """{"ts":1000,"event":"task_start","task_id":"t1"}""",
            """{"ts":2000,"event":"task_end","task_id":"t1","status":"success"}"""
        )
        val result = TraceAdapter.parseTrace(lines, "t1", "run-1")
        assertEquals("success", result.runRecord.status)
        assertEquals(1000L, result.runRecord.durationMs)
        assertEquals(2, result.events.size)
    }

    @Test
    fun `parseTrace with step_fail produces failed status`() {
        val lines = listOf(
            """{"ts":1000,"event":"task_start","task_id":"t1"}""",
            """{"ts":1500,"event":"step_fail","step_id":"s1","reason":"timeout"}""",
            """{"ts":2000,"event":"task_end","task_id":"t1","status":"failed"}"""
        )
        val result = TraceAdapter.parseTrace(lines, "t1", "run-1")
        assertEquals("failed", result.runRecord.status)
        assertEquals("s1", result.runRecord.failedStepId)
        assertEquals("timeout", result.runRecord.failureType)
    }

    @Test
    fun `parseTrace generates diagnosis for locator_not_found`() {
        val lines = listOf(
            """{"ts":1000,"event":"locator_not_found","step_id":"s2","locator":"btn_submit"}"""
        )
        val result = TraceAdapter.parseTrace(lines, "t1", "run-1")
        assertTrue(result.runRecord.diagnosisSummary.contains("未找到目标控件"))
        assertTrue(result.runRecord.diagnosisSuggestion.contains("文本定位"))
    }

    @Test
    fun `parseTrace generates diagnosis for timeout`() {
        val lines = listOf(
            """{"ts":1000,"event":"step_fail","step_id":"s1","reason":"timeout"}"""
        )
        val result = TraceAdapter.parseTrace(lines, "t1", "run-1")
        assertTrue(result.runRecord.diagnosisSummary.contains("超时"))
    }

    @Test
    fun `parseTrace generates diagnosis for model_error`() {
        val lines = listOf(
            """{"ts":1000,"event":"model_error"}"""
        )
        val result = TraceAdapter.parseTrace(lines, "t1", "run-1")
        assertTrue(result.runRecord.diagnosisSummary.contains("AI 模型"))
    }

    @Test
    fun `parseTrace generates diagnosis for safety_blocked`() {
        val lines = listOf(
            """{"ts":1000,"event":"step_fail","step_id":"s1","reason":"safety_blocked"}"""
        )
        val result = TraceAdapter.parseTrace(lines, "t1", "run-1")
        assertTrue(result.runRecord.diagnosisSummary.contains("安全策略"))
    }

    @Test
    fun `parseTrace generates diagnosis for permission_error`() {
        val lines = listOf(
            """{"ts":1000,"event":"step_fail","step_id":"s1","reason":"permission_error"}"""
        )
        val result = TraceAdapter.parseTrace(lines, "t1", "run-1")
        assertTrue(result.runRecord.diagnosisSummary.contains("权限"))
    }

    @Test
    fun `parseTrace counts model calls`() {
        val lines = listOf(
            """{"ts":1000,"event":"model_call"}""",
            """{"ts":1100,"event":"vision_call"}""",
            """{"ts":1200,"event":"step_ok"}"""
        )
        val result = TraceAdapter.parseTrace(lines, "t1", "run-1")
        assertEquals(2, result.runRecord.modelCalls)
    }

    @Test
    fun `parseTrace skips malformed lines`() {
        val lines = listOf(
            """{"ts":1000,"event":"task_start","task_id":"t1"}""",
            "this is not json",
            """{"ts":2000,"event":"task_end","task_id":"t1","status":"success"}"""
        )
        val result = TraceAdapter.parseTrace(lines, "t1", "run-1")
        assertEquals(2, result.events.size)
        assertEquals("success", result.runRecord.status)
    }

    @Test
    fun `parseTrace event level is error for fail events`() {
        val lines = listOf(
            """{"ts":1000,"event":"step_fail","step_id":"s1"}"""
        )
        val result = TraceAdapter.parseTrace(lines, "t1", "run-1")
        assertEquals("error", result.events[0].level)
    }

    @Test
    fun `parseTrace event level is info for normal events`() {
        val lines = listOf(
            """{"ts":1000,"event":"task_start","task_id":"t1"}"""
        )
        val result = TraceAdapter.parseTrace(lines, "t1", "run-1")
        assertEquals("info", result.events[0].level)
    }

    @Test
    fun `parseTrace with empty lines returns empty events`() {
        val result = TraceAdapter.parseTrace(emptyList(), "t1", "run-1")
        assertEquals(0, result.events.size)
        assertEquals(0L, result.runRecord.durationMs)
    }

    @Test
    fun `parseTrace uses provided runId`() {
        val lines = listOf("""{"ts":1000,"event":"task_start","task_id":"t1"}""")
        val result = TraceAdapter.parseTrace(lines, "t1", "custom-run-id")
        assertEquals("custom-run-id", result.events[0].runId)
        assertEquals("custom-run-id", result.runRecord.runId)
    }
}
