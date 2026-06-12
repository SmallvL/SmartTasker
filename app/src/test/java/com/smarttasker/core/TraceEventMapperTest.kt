package com.smarttasker.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TraceEventMapperTest {

    @Test
    fun `map returns null for empty event`() {
        val obj = JSONObject("""{"task_id":"t1"}""")
        assertNull(TraceEventMapper.map(obj))
    }

    @Test
    fun `map fsm_state_enter APP_RESOLVE`() {
        val obj = JSONObject("""{"event":"fsm_state_enter","state":"APP_RESOLVE","task_id":"t1"}""")
        val result = TraceEventMapper.map(obj)!!
        assertEquals("fsm_state_enter", result.event)
        assertTrue(result.messages.any { it.contains("APP_RESOLVE") })
        assertNotNull(result.runtimeUpdate)
        assertEquals("APP_RESOLVE", result.runtimeUpdate!!.phase)
    }

    @Test
    fun `map fsm_state_enter FINISH has stopAfter`() {
        val obj = JSONObject("""{"event":"fsm_state_enter","state":"FINISH","task_id":"t1"}""")
        val result = TraceEventMapper.map(obj)!!
        assertTrue(result.runtimeUpdate!!.stopAfter)
        assertEquals("DONE", result.runtimeUpdate!!.phase)
    }

    @Test
    fun `map fsm_state_enter FAIL has stopAfter`() {
        val obj = JSONObject("""{"event":"fsm_state_enter","state":"FAIL","task_id":"t1"}""")
        val result = TraceEventMapper.map(obj)!!
        assertTrue(result.runtimeUpdate!!.stopAfter)
        assertEquals("FAILED", result.runtimeUpdate!!.phase)
    }

    @Test
    fun `map fsm_init_ready includes device dimensions`() {
        val obj = JSONObject("""{"event":"fsm_init_ready","task_id":"t1","device_info":{"width":1080,"height":1920},"app_candidates":5}""")
        val result = TraceEventMapper.map(obj)!!
        assertTrue(result.messages.any { it.contains("1080x1920") })
        assertTrue(result.messages.any { it.contains("5") })
    }

    @Test
    fun `map exec_tap_start includes coordinates`() {
        val obj = JSONObject("""{"event":"exec_tap_start","task_id":"t1","x":300,"y":500}""")
        val result = TraceEventMapper.map(obj)!!
        assertTrue(result.messages.any { it.contains("(300, 500)") })
    }

    @Test
    fun `map exec_swipe_start includes from and to`() {
        val obj = JSONObject("""{"event":"exec_swipe_start","task_id":"t1","x1":100,"y1":200,"x2":300,"y2":400,"duration":500}""")
        val result = TraceEventMapper.map(obj)!!
        assertTrue(result.messages.any { it.contains("SWIPE") })
        assertTrue(result.messages.any { it.contains("500ms") })
    }

    @Test
    fun `map exec_input_start includes text`() {
        val obj = JSONObject("""{"event":"exec_input_start","task_id":"t1","text":"hello"}""")
        val result = TraceEventMapper.map(obj)!!
        assertTrue(result.messages.any { it.contains("hello") })
    }

    @Test
    fun `map vision_action_loop_detected produces FAILED runtime`() {
        val obj = JSONObject("""{"event":"vision_action_loop_detected","task_id":"t1"}""")
        val result = TraceEventMapper.map(obj)!!
        assertEquals("FAILED", result.runtimeUpdate!!.phase)
        assertTrue(result.runtimeUpdate!!.stopAfter)
    }

    @Test
    fun `map fsm_cancel_requested produces message`() {
        val obj = JSONObject("""{"event":"fsm_cancel_requested","task_id":"t1"}""")
        val result = TraceEventMapper.map(obj)!!
        assertTrue(result.messages.any { it.contains("Cancel") })
    }

    @Test
    fun `map fsm_task_cancelled has CANCELLED runtime`() {
        val obj = JSONObject("""{"event":"fsm_task_cancelled","task_id":"t1"}""")
        val result = TraceEventMapper.map(obj)!!
        assertEquals("CANCELLED", result.runtimeUpdate!!.phase)
        assertTrue(result.runtimeUpdate!!.stopAfter)
    }

    @Test
    fun `map unknown event returns event name in messages`() {
        val obj = JSONObject("""{"event":"custom_event","task_id":"t1"}""")
        val result = TraceEventMapper.map(obj)!!
        assertEquals("custom_event", result.event)
        // Unknown events produce no messages and no runtime update
        assertTrue(result.messages.isEmpty())
        assertNull(result.runtimeUpdate)
    }

    @Test
    fun `map extracts taskId`() {
        val obj = JSONObject("""{"event":"exec_back_start","task_id":"my-task-42"}""")
        val result = TraceEventMapper.map(obj)!!
        assertEquals("my-task-42", result.taskId)
    }
}
