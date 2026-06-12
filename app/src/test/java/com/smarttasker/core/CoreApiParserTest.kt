package com.smarttasker.core

import org.junit.Assert.*
import org.junit.Test

class CoreApiParserTest {

    // --- parseTaskSubmit ---

    @Test
    fun `parseTaskSubmit success`() {
        val payload = """{"ok":true,"status":"submitted","task_id":"t-123"}""".toByteArray()
        val result = CoreApiParser.parseTaskSubmit(payload)
        assertTrue(result.message.contains("t-123"))
        assertEquals("t-123", result.taskId)
    }

    @Test
    fun `parseTaskSubmit failure returns empty taskId`() {
        val payload = """{"ok":false,"status":"error"}""".toByteArray()
        val result = CoreApiParser.parseTaskSubmit(payload)
        assertEquals("", result.taskId)
    }

    @Test
    fun `parseTaskSubmit invalid JSON returns error message`() {
        val payload = "not json".toByteArray()
        val result = CoreApiParser.parseTaskSubmit(payload)
        assertTrue(result.message.contains("Invalid response"))
        assertEquals("", result.taskId)
    }

    // --- parseInstalledApps ---

    @Test
    fun `parseInstalledApps success with dedup and sort`() {
        val json = """[{"package":"com.tencent.mm","label":"微信"},{"package":"com.tencent.mm","label":""},{"package":"com.taobao.taobao","label":"淘宝"}]"""
        val payload = byteArrayOf(1) + // status = 1
            byteArrayOf(0, json.toByteArray().size.toByte()) + // jsonLen (truncated but safe)
            json.toByteArray()
        val (msg, apps) = CoreApiParser.parseInstalledApps(payload)
        assertTrue(msg.contains("2 items"))
        assertEquals(2, apps.size)
        // Dedup: first entry with label wins
        assertEquals("微信", apps.find { it.packageName == "com.tencent.mm" }!!.label)
        // Sorted by label
        assertEquals("淘宝", apps[0].label)
    }

    @Test
    fun `parseInstalledApps empty payload returns error`() {
        val (msg, apps) = CoreApiParser.parseInstalledApps(ByteArray(0))
        assertTrue(msg.contains("empty"))
        assertTrue(apps.isEmpty())
    }

    @Test
    fun `parseInstalledApps short payload returns error`() {
        val (msg, apps) = CoreApiParser.parseInstalledApps(ByteArray(2))
        assertTrue(msg.contains("short"))
        assertTrue(apps.isEmpty())
    }

    // --- parseScheduleAdd ---

    @Test
    fun `parseScheduleAdd success`() {
        val payload = """{"ok":true,"schedule":{"schedule_id":"sch-1"}}""".toByteArray()
        val result = CoreApiParser.parseScheduleAdd(payload)
        assertTrue(result.contains("sch-1"))
    }

    @Test
    fun `parseScheduleAdd failure`() {
        val payload = """{"ok":false}""".toByteArray()
        val result = CoreApiParser.parseScheduleAdd(payload)
        assertTrue(result.contains("failed"))
    }

    // --- parseScheduleRemove ---

    @Test
    fun `parseScheduleRemove success`() {
        val payload = """{"ok":true,"removed":true}""".toByteArray()
        val result = CoreApiParser.parseScheduleRemove(payload, "sch-1")
        assertTrue(result.contains("removed"))
    }

    @Test
    fun `parseScheduleRemove not found`() {
        val payload = """{"ok":true,"removed":false}""".toByteArray()
        val result = CoreApiParser.parseScheduleRemove(payload, "sch-1")
        assertTrue(result.contains("not found"))
    }

    // --- parseSystemControl ---

    @Test
    fun `parseSystemControl empty payload returns false`() {
        val result = CoreApiParser.parseSystemControl(ByteArray(0))
        assertFalse(result.ok)
    }

    @Test
    fun `parseSystemControl short payload returns false`() {
        val result = CoreApiParser.parseSystemControl(ByteArray(2))
        assertFalse(result.ok)
    }

    @Test
    fun `parseSystemControl success with stdout`() {
        val json = """{"ok":true,"stdout":"hello"}"""
        val jsonBytes = json.toByteArray()
        val payload = byteArrayOf(1) +
            byteArrayOf((jsonBytes.size shr 8).toByte(), jsonBytes.size.toByte()) +
            jsonBytes
        val result = CoreApiParser.parseSystemControl(payload)
        assertTrue(result.ok)
        assertTrue(result.detail.contains("hello"))
    }

    @Test
    fun `parseSystemControl failure status returns false`() {
        val json = """{"ok":true}"""
        val jsonBytes = json.toByteArray()
        val payload = byteArrayOf(0) + // status != 1
            byteArrayOf((jsonBytes.size shr 8).toByte(), jsonBytes.size.toByte()) +
            jsonBytes
        val result = CoreApiParser.parseSystemControl(payload)
        assertFalse(result.ok)
    }

    // --- parseNotifyRuleUpsert ---

    @Test
    fun `parseNotifyRuleUpsert update`() {
        val payload = """{"ok":true,"updated":true,"rule":{"id":"r1"}}""".toByteArray()
        val (msg, id) = CoreApiParser.parseNotifyRuleUpsert(payload)
        assertTrue(msg.contains("updated"))
        assertEquals("r1", id)
    }

    @Test
    fun `parseNotifyRuleUpsert add`() {
        val payload = """{"ok":true,"updated":false,"rule":{"id":"r2"}}""".toByteArray()
        val (msg, id) = CoreApiParser.parseNotifyRuleUpsert(payload)
        assertTrue(msg.contains("added"))
        assertEquals("r2", id)
    }
}
