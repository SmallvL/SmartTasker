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

    // ===== Edge cases added in v0.9.8 =====

    @Test
    fun `input with only whitespace is error`() {
        val result = TaskSpecParser.parse("\t   \n  ")
        assertTrue(result is TaskSpecParser.ParseResult.Error)
    }

    @Test
    fun `input with newlines is normalised and parsed`() {
        val result = TaskSpecParser.parse("打开\n淘宝")
        assertTrue(result is TaskSpecParser.ParseResult.Success)
    }

    @Test
    fun `payment keyword is high risk`() {
        val result = TaskSpecParser.parse("帮我用支付宝付款")
        val spec = (result as TaskSpecParser.ParseResult.Success).spec
        assertEquals("high", spec.risk.level)
    }

    @Test
    fun `purchase keyword is high risk`() {
        val result = TaskSpecParser.parse("帮我加购物车并结算")
        val spec = (result as TaskSpecParser.ParseResult.Success).spec
        assertEquals("high", spec.risk.level)
    }

    @Test
    fun `forbidden critical risk case 投资`() {
        val result = TaskSpecParser.parse("帮我投资理财")
        assertTrue(result is TaskSpecParser.ParseResult.Forbidden)
    }

    @Test
    fun `forbidden critical risk case 改密码`() {
        val result = TaskSpecParser.parse("帮我修改密码")
        assertTrue(result is TaskSpecParser.ParseResult.Forbidden)
    }

    @Test
    fun `weekly schedule pattern`() {
        val result = TaskSpecParser.parse("每周一早上8点30分打开滴滴")
        val spec = (result as TaskSpecParser.ParseResult.Success).spec
        assertEquals("schedule", spec.trigger.type)
        assertEquals("08:30", spec.trigger.time)
        assertEquals("weekly", spec.trigger.repeat)
    }

    @Test
    fun `english every day at 9am is schedule daily`() {
        val result = TaskSpecParser.parse("every day at 9am open taobao")
        val spec = (result as TaskSpecParser.ParseResult.Success).spec
        assertEquals("schedule", spec.trigger.type)
        assertEquals("09:00", spec.trigger.time)
        assertEquals("daily", spec.trigger.repeat)
    }

    @Test
    fun `12pm is normalised to 12 not 24`() {
        val result = TaskSpecParser.parse("every day at 12pm open wechat")
        val spec = (result as TaskSpecParser.ParseResult.Success).spec
        assertEquals("12:00", spec.trigger.time)
    }

    @Test
    fun `12am is normalised to 00`() {
        val result = TaskSpecParser.parse("every day at 12am open wechat")
        val spec = (result as TaskSpecParser.ParseResult.Success).spec
        assertEquals("00:00", spec.trigger.time)
    }

    @Test
    fun `unknown app yields null targetApp but still parses`() {
        val result = TaskSpecParser.parse("打开我的不存在的app")
        assertTrue(result is TaskSpecParser.ParseResult.Success)
        val spec = (result as TaskSpecParser.ParseResult.Success).spec
        assertNull(spec.targetApp)
        // No app + no specific time → manual trigger
        assertEquals("manual", spec.trigger.type)
    }

    @Test
    fun `english app name maps to chinese package`() {
        val result = TaskSpecParser.parse("open wechat")
        val spec = (result as TaskSpecParser.ParseResult.Success).spec
        assertNotNull(spec.targetApp)
        assertEquals("com.tencent.mm", spec.targetApp!!.packageName)
    }

    @Test
    fun `5 minutes from now is one-shot schedule`() {
        val result = TaskSpecParser.parse("5分钟后帮我打开微信")
        val spec = (result as TaskSpecParser.ParseResult.Success).spec
        assertEquals("schedule", spec.trigger.type)
        assertEquals("once", spec.trigger.repeat)
    }

    @Test
    fun `task name is stripped of time prefixes`() {
        val result = TaskSpecParser.parse("每天早上9点打开淘宝收金币")
        val spec = (result as TaskSpecParser.ParseResult.Success).spec
        assertFalse("name should not start with 每天: '${spec.name}'", spec.name.startsWith("每天"))
    }

    @Test
    fun `task name is truncated to 20 chars`() {
        val longInput = "打开淘宝" + "abcdefghijklmnopqrstuvwxyz123456".repeat(3)
        val result = TaskSpecParser.parse(longInput)
        val spec = (result as TaskSpecParser.ParseResult.Success).spec
        assertTrue("name length: ${spec.name.length}", spec.name.length <= 20)
    }

    @Test
    fun `case insensitive english app name`() {
        val result = TaskSpecParser.parse("Open WeChat")
        val spec = (result as TaskSpecParser.ParseResult.Success).spec
        assertNotNull(spec.targetApp)
        assertEquals("com.tencent.mm", spec.targetApp!!.packageName)
    }

    @Test
    fun `notification trigger detected on 收到 keyword`() {
        val result = TaskSpecParser.parse("收到微信消息后帮我查看")
        val spec = (result as TaskSpecParser.ParseResult.Success).spec
        assertEquals("notification", spec.trigger.type)
    }

    @Test
    fun `two apps in input picks longer match`() {
        // "微信" should win over "QQ" if both appear
        val result = TaskSpecParser.parse("帮我打开微信和QQ")
        val spec = (result as TaskSpecParser.ParseResult.Success).spec
        // First match in sorted-descending list — both should be present
        assertNotNull(spec.targetApp)
    }
}
