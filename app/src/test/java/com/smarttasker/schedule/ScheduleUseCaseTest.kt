package com.smarttasker.schedule

import org.junit.Assert.*
import org.junit.Test

class ScheduleUseCaseTest {

    private val nowMs = 1000000L
    private val repeatOnce = "once"
    private val repeatWeekly = "weekly"
    private val repeatDaily = "daily"

    private fun validInput(runAt: String = "2000000") = ScheduleFormInput(
        name = "测试调度",
        task = "打开微信",
        packageName = "com.tencent.mm",
        playbook = "",
        enabled = true,
        recordEnabled = false,
        taskMapMode = "off",
        runAtRaw = runAt,
        repeatModeRaw = repeatOnce,
        repeatWeekdays = 0
    )

    @Test
    fun `buildDraft succeeds with valid input`() {
        val result = ScheduleUseCase.buildDraft(validInput(), nowMs, repeatOnce, repeatWeekly)
        assertTrue(result.isSuccess)
        val draft = result.getOrNull()!!
        assertEquals("打开微信", draft.task)
        assertEquals(2000000L, draft.runAt)
    }

    @Test
    fun `buildDraft fails with empty task`() {
        val input = validInput().copy(task = "  ")
        val result = ScheduleUseCase.buildDraft(input, nowMs, repeatOnce, repeatWeekly)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("empty", ignoreCase = true))
    }

    @Test
    fun `buildDraft fails with invalid runAt`() {
        val input = validInput().copy(runAtRaw = "abc")
        val result = ScheduleUseCase.buildDraft(input, nowMs, repeatOnce, repeatWeekly)
        assertTrue(result.isFailure)
    }

    @Test
    fun `buildDraft fails with zero runAt`() {
        val input = validInput().copy(runAtRaw = "0")
        val result = ScheduleUseCase.buildDraft(input, nowMs, repeatOnce, repeatWeekly)
        assertTrue(result.isFailure)
    }

    @Test
    fun `buildDraft fails when once schedule runAt is in the past`() {
        val input = validInput().copy(runAtRaw = "500000") // < nowMs
        val result = ScheduleUseCase.buildDraft(input, nowMs, repeatOnce, repeatWeekly)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("future", ignoreCase = true))
    }

    @Test
    fun `buildDraft fails when weekly with no weekdays selected`() {
        val input = validInput().copy(repeatModeRaw = repeatWeekly, repeatWeekdays = 0)
        val result = ScheduleUseCase.buildDraft(input, nowMs, repeatOnce, repeatWeekly)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("weekday", ignoreCase = true))
    }

    @Test
    fun `buildDraft succeeds for weekly with weekdays`() {
        val input = validInput().copy(repeatModeRaw = repeatWeekly, repeatWeekdays = 0b0010010) // Mon+Wed
        val result = ScheduleUseCase.buildDraft(input, nowMs, repeatOnce, repeatWeekly)
        assertTrue(result.isSuccess)
        assertEquals(0b0010010, result.getOrNull()!!.repeatWeekdays)
    }

    @Test
    fun `buildDraft masks repeatWeekdays to 7 bits`() {
        val input = validInput().copy(repeatModeRaw = repeatWeekly, repeatWeekdays = 0xFF)
        val result = ScheduleUseCase.buildDraft(input, nowMs, repeatOnce, repeatWeekly)
        assertTrue(result.isSuccess)
        assertEquals(0x7F, result.getOrNull()!!.repeatWeekdays)
    }

    @Test
    fun `buildDraft defaults empty taskMapMode to off`() {
        val input = validInput().copy(taskMapMode = "")
        val result = ScheduleUseCase.buildDraft(input, nowMs, repeatOnce, repeatWeekly)
        assertTrue(result.isSuccess)
        assertEquals("off", result.getOrNull()!!.taskMapMode)
    }

    @Test
    fun `buildUpsertPayload includes all fields`() {
        val draft = ScheduleDraft(
            name = "test", task = "打开微信", packageName = "com.tencent.mm",
            playbook = "", enabled = true, recordEnabled = false, taskMapMode = "off",
            runAt = 2000000L, repeatMode = repeatDaily, repeatWeekdays = 0
        )
        val payload = ScheduleUseCase.buildUpsertPayload(draft, traceUdpPort = 9090, repeatDaily = repeatDaily)
        val text = payload.toString(Charsets.UTF_8)
        assertTrue(text.contains("\"user_task\":\"打开微信\""))
        assertTrue(text.contains("\"trace_udp_port\":9090"))
        assertTrue(text.contains("\"repeat_daily\":true"))
    }

    @Test
    fun `buildUpsertPayload includes scheduleId when provided`() {
        val draft = ScheduleDraft(
            name = "test", task = "task", packageName = "", playbook = "",
            enabled = true, recordEnabled = false, taskMapMode = "off",
            runAt = 2000000L, repeatMode = "once", repeatWeekdays = 0
        )
        val payload = ScheduleUseCase.buildUpsertPayload(draft, 9090, repeatDaily, "sch-123")
        val text = payload.toString(Charsets.UTF_8)
        assertTrue(text.contains("\"schedule_id\":\"sch-123\""))
    }

    @Test
    fun `buildUpsertPayload omits scheduleId when null`() {
        val draft = ScheduleDraft(
            name = "test", task = "task", packageName = "", playbook = "",
            enabled = true, recordEnabled = false, taskMapMode = "off",
            runAt = 2000000L, repeatMode = "once", repeatWeekdays = 0
        )
        val payload = ScheduleUseCase.buildUpsertPayload(draft, 9090, repeatDaily, null)
        val text = payload.toString(Charsets.UTF_8)
        assertFalse(text.contains("schedule_id"))
    }
}
