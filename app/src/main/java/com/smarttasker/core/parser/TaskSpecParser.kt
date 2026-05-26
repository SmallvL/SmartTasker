package com.smarttasker.core.parser

import java.util.UUID

/**
 * TaskSpec Parser - converts natural language to structured TaskSpec.
 * MVP uses rule-based parsing; LLM integration is a future enhancement.
 */
object TaskSpecParser {

    // Known app mappings
    private val APP_DATABASE = mapOf(
        "淘宝" to TaskSpec.AppInfo("淘宝", "com.taobao.taobao", 0.95f),
        "天猫" to TaskSpec.AppInfo("天猫", "com.tmall.wireless", 0.95f),
        "支付宝" to TaskSpec.AppInfo("支付宝", "com.eg.android.AlipayGphone", 0.95f),
        "微信" to TaskSpec.AppInfo("微信", "com.tencent.mm", 0.95f),
        "抖音" to TaskSpec.AppInfo("抖音", "com.ss.android.ugc.aweme", 0.95f),
        "小红书" to TaskSpec.AppInfo("小红书", "com.xingin.xhs", 0.95f),
        "京东" to TaskSpec.AppInfo("京东", "com.jingdong.app.mall", 0.95f),
        "拼多多" to TaskSpec.AppInfo("拼多多", "com.xunmeng.pinduoduo", 0.95f),
        "百度贴吧" to TaskSpec.AppInfo("百度贴吧", "com.baidu.tieba", 0.95f),
        "贴吧" to TaskSpec.AppInfo("百度贴吧", "com.baidu.tieba", 0.90f),
        "哔哩哔哩" to TaskSpec.AppInfo("哔哩哔哩", "tv.danmaku.bili", 0.95f),
        "b站" to TaskSpec.AppInfo("哔哩哔哩", "tv.danmaku.bili", 0.90f),
        "美团" to TaskSpec.AppInfo("美团", "com.sankuai.meituan", 0.95f),
        "饿了么" to TaskSpec.AppInfo("饿了么", "me.ele", 0.95f),
        "网易云音乐" to TaskSpec.AppInfo("网易云音乐", "com.netease.cloudmusic", 0.95f),
        "qq音乐" to TaskSpec.AppInfo("QQ音乐", "com.tencent.qqmusic", 0.95f),
        "设置" to TaskSpec.AppInfo("设置", "com.android.settings", 0.99f),
        "系统设置" to TaskSpec.AppInfo("设置", "com.android.settings", 0.99f),
        "浏览器" to TaskSpec.AppInfo("浏览器", "com.android.browser", 0.80f),
        "相册" to TaskSpec.AppInfo("相册", "com.android.gallery3d", 0.80f),
        "时钟" to TaskSpec.AppInfo("时钟", "com.android.deskclock", 0.80f),
        "计算器" to TaskSpec.AppInfo("计算器", "com.android.calculator2", 0.80f),
        "快手" to TaskSpec.AppInfo("快手", "com.smile.gifmaker", 0.95f),
        "滴滴" to TaskSpec.AppInfo("滴滴出行", "com.sdu.didi.psnger", 0.95f),
        "高德" to TaskSpec.AppInfo("高德地图", "com.autonavi.minimap", 0.95f),
        "菜鸟" to TaskSpec.AppInfo("菜鸟", "com.cainiao.wireless", 0.95f),
        "闲鱼" to TaskSpec.AppInfo("闲鱼", "com.taobao.idlefish", 0.95f),
        "微博" to TaskSpec.AppInfo("微博", "com.sina.weibo", 0.95f),
        "qq" to TaskSpec.AppInfo("QQ", "com.tencent.mobileqq", 0.95f)
    )

    // High-risk keywords
    private val HIGH_RISK_KEYWORDS = listOf(
        "转账", "支付", "付款", "充值", "提现", "红包",
        "删除", "注销", "退出登录", "解绑",
        "提交订单", "下单", "购买", "秒杀", "抢购",
        "发送消息", "回复", "自动回复", "群发"
    )

    // Forbidden keywords
    private val FORBIDDEN_KEYWORDS = listOf(
        "转账给", "自动付款", "贷款", "借贷", "信用卡还款"
    )

    // Schedule patterns
    private val TIME_PATTERNS = listOf(
        Regex("""每天[早上晚上中午]*(\d{1,2})[点时:](\d{0,2})[分]?"""),
        Regex("""(\d{1,2})[点时:](\d{0,2})"""),
        Regex("""每[周星期](\d)[早上晚上中午]*(\d{1,2})[点时]?"""),
        Regex("""(\d{1,2})分钟后"""),
        Regex("""每天"""),
        Regex("""定时""")
    )

    /**
     * Parse natural language input into a TaskSpec.
     */
    fun parse(input: String): ParseResult {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return ParseResult.Error("请输入任务描述")

        // Check forbidden
        for (keyword in FORBIDDEN_KEYWORDS) {
            if (trimmed.contains(keyword)) {
                return ParseResult.Forbidden("包含禁止操作: $keyword")
            }
        }

        // Detect trigger type
        val trigger = detectTrigger(trimmed)

        // Detect target app
        val app = detectApp(trimmed)

        // Detect risk level
        val risk = detectRisk(trimmed)

        // Generate task name
        val name = generateName(trimmed, app)

        // Generate playbook
        val playbook = trimmed

        val spec = TaskSpec(
            taskId = UUID.randomUUID().toString().take(8),
            name = name,
            description = trimmed,
            targetApp = app,
            trigger = trigger,
            execution = TaskSpec.ExecutionConfig(),
            risk = risk,
            playbook = playbook
        )

        return ParseResult.Success(spec)
    }

    private fun detectTrigger(input: String): TaskSpec.TriggerConfig {
        for (pattern in TIME_PATTERNS) {
            val match = pattern.find(input)
            if (match != null) {
                val groups = match.groupValues
                val time = if (groups.size >= 3 && groups[1].isNotEmpty()) {
                    val h = groups[1].padStart(2, '0')
                    val m = groups[2].ifEmpty { "00" }.padStart(2, '0')
                    "$h:$m"
                } else ""
                return TaskSpec.TriggerConfig(
                    type = "schedule",
                    time = time,
                    repeat = if (input.contains("每天")) "daily" else "once"
                )
            }
        }

        if (input.contains("通知") || input.contains("收到") || input.contains("消息")) {
            return TaskSpec.TriggerConfig(type = "notification")
        }

        return TaskSpec.TriggerConfig(type = "manual")
    }

    private fun detectApp(input: String): TaskSpec.AppInfo? {
        // Try exact match first (longer names first)
        val sortedApps = APP_DATABASE.entries.sortedByDescending { it.key.length }
        for ((name, info) in sortedApps) {
            if (input.contains(name, ignoreCase = true)) {
                return info
            }
        }
        return null
    }

    private fun detectRisk(input: String): TaskSpec.RiskConfig {
        for (keyword in FORBIDDEN_KEYWORDS) {
            if (input.contains(keyword)) {
                return TaskSpec.RiskConfig(
                    level = "critical",
                    requiresConfirmation = true,
                    reason = "包含禁止操作: $keyword"
                )
            }
        }

        for (keyword in HIGH_RISK_KEYWORDS) {
            if (input.contains(keyword)) {
                return TaskSpec.RiskConfig(
                    level = "high",
                    requiresConfirmation = true,
                    reason = "包含高风险操作: $keyword"
                )
            }
        }

        return TaskSpec.RiskConfig(level = "low")
    }

    private fun generateName(input: String, app: TaskSpec.AppInfo?): String {
        // Take first meaningful part
        val name = input
            .replace(Regex("""每天[早上晚上中午]*\d*[点时:]\d*[分]?"""), "")
            .replace(Regex("""收到.*?后"""), "")
            .replace("帮我", "")
            .replace("自动", "")
            .trim()
            .take(20)

        return if (name.isNotEmpty()) name else app?.let { "${it.name}任务" } ?: "自动化任务"
    }

    sealed class ParseResult {
        data class Success(val spec: TaskSpec) : ParseResult()
        data class Error(val message: String) : ParseResult()
        data class Forbidden(val reason: String) : ParseResult()
    }
}
