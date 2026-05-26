package com.smarttasker.core.parser

import org.junit.Assert.*
import org.junit.Test

class TaskSpecParserTest {

    @Test
    fun `parse basic quick task`() {
        val result = TaskSpecParser.parse("打开设置进入Wi-Fi页面")
        assertTrue(result is TaskSpecParser.ParseResult.Success)
        val spec = (result as TaskSpecParser.ParseResult.Success).spec
        assertEquals("manual", spec.trigger.type)
        assertNotNull(spec.targetApp)
        assertEquals("com.android.settings", spec.targetApp!!.packageName)
        assertEquals("low", spec.risk.level)
    }

    @Test
    fun `parse schedule task with time`() {
        val result = TaskSpecParser.parse("每天早上9点打开淘宝收金币")
        assertTrue(result is TaskSpecParser.ParseResult.Success)
        val spec = (result as TaskSpecParser.ParseResult.Success).spec
        assertEquals("schedule", spec.trigger.type)
        assertEquals("09:00", spec.trigger.time)
        assertEquals("daily", spec.trigger.repeat)
        assertEquals("com.taobao.taobao", spec.targetApp!!.packageName)
        assertEquals("low", spec.risk.level)
    }

    @Test
    fun `parse notification trigger task`() {
        val result = TaskSpecParser.parse("收到微信消息后打开微信查看")
        assertTrue(result is TaskSpecParser.ParseResult.Success)
        val spec = (result as TaskSpecParser.ParseResult.Success).spec
        assertEquals("notification", spec.trigger.type)
        assertEquals("com.tencent.mm", spec.targetApp!!.packageName)
    }

    @Test
    fun `detect high risk task - send message`() {
        val result = TaskSpecParser.parse("收到老板微信后自动回复收到")
        assertTrue(result is TaskSpecParser.ParseResult.Success)
        val spec = (result as TaskSpecParser.ParseResult.Success).spec
        assertEquals("high", spec.risk.level)
        assertTrue(spec.risk.requiresConfirmation)
    }

    @Test
    fun `detect high risk task - submit order`() {
        val result = TaskSpecParser.parse("帮我提交订单")
        assertTrue(result is TaskSpecParser.ParseResult.Success)
        val spec = (result as TaskSpecParser.ParseResult.Success).spec
        assertEquals("high", spec.risk.level)
        assertTrue(spec.risk.requiresConfirmation)
    }

    @Test
    fun `detect high risk task - delete`() {
        val result = TaskSpecParser.parse("帮我删除相册里的截图")
        assertTrue(result is TaskSpecParser.ParseResult.Success)
        val spec = (result as TaskSpecParser.ParseResult.Success).spec
        assertEquals("high", spec.risk.level)
        assertTrue(spec.risk.requiresConfirmation)
    }

    @Test
    fun `forbidden task - transfer money`() {
        val result = TaskSpecParser.parse("帮我自动转账给张三")
        assertTrue(result is TaskSpecParser.ParseResult.Forbidden)
    }

    @Test
    fun `forbidden task - loan`() {
        val result = TaskSpecParser.parse("帮我申请贷款")
        assertTrue(result is TaskSpecParser.ParseResult.Forbidden)
    }

    @Test
    fun `parse tieba sign in task`() {
        val result = TaskSpecParser.parse("打开百度贴吧，帮我一键签到")
        assertTrue(result is TaskSpecParser.ParseResult.Success)
        val spec = (result as TaskSpecParser.ParseResult.Success).spec
        assertEquals("com.baidu.tieba", spec.targetApp!!.packageName)
        assertEquals("low", spec.risk.level)
    }

    @Test
    fun `parse bilibili task`() {
        val result = TaskSpecParser.parse("打开b站看热门视频")
        assertTrue(result is TaskSpecParser.ParseResult.Success)
        val spec = (result as TaskSpecParser.ParseResult.Success).spec
        assertEquals("tv.danmaku.bili", spec.targetApp!!.packageName)
    }

    @Test
    fun `empty input returns error`() {
        val result = TaskSpecParser.parse("")
        assertTrue(result is TaskSpecParser.ParseResult.Error)
    }

    @Test
    fun `blank input returns error`() {
        val result = TaskSpecParser.parse("   ")
        assertTrue(result is TaskSpecParser.ParseResult.Error)
    }

    @Test
    fun `task spec serialization`() {
        val result = TaskSpecParser.parse("每天早上9点打开淘宝收金币")
        val spec = (result as TaskSpecParser.ParseResult.Success).spec
        val json = spec.toJson()
        assertEquals(spec.taskId, json.getString("task_id"))
        assertEquals(spec.name, json.getString("name"))
        assertEquals("schedule", json.getJSONObject("trigger").getString("type"))
        assertEquals("09:00", json.getJSONObject("trigger").getString("time"))
    }
}
