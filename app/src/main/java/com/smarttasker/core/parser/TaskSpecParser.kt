package com.smarttasker.core.parser

import java.util.UUID

/**
 * TaskSpec Parser - converts natural language to structured TaskSpec.
 * MVP uses rule-based parsing; LLM integration is a future enhancement.
 */
object TaskSpecParser {

    // Known app mappings
    private val APP_DATABASE = mapOf(
        // 购物
        "淘宝" to TaskSpec.AppInfo("淘宝", "com.taobao.taobao", 0.95f),
        "天猫" to TaskSpec.AppInfo("天猫", "com.tmall.wireless", 0.95f),
        "京东" to TaskSpec.AppInfo("京东", "com.jingdong.app.mall", 0.95f),
        "拼多多" to TaskSpec.AppInfo("拼多多", "com.xunmeng.pinduoduo", 0.95f),
        "闲鱼" to TaskSpec.AppInfo("闲鱼", "com.taobao.idlefish", 0.95f),
        "苏宁" to TaskSpec.AppInfo("苏宁易购", "com.suning.mobile.ebuy", 0.90f),
        "唯品会" to TaskSpec.AppInfo("唯品会", "com.achievo.vipshop", 0.90f),

        // 社交通讯
        "微信" to TaskSpec.AppInfo("微信", "com.tencent.mm", 0.95f),
        "qq" to TaskSpec.AppInfo("QQ", "com.tencent.mobileqq", 0.95f),
        "钉钉" to TaskSpec.AppInfo("钉钉", "com.alibaba.android.rimet", 0.95f),
        "企业微信" to TaskSpec.AppInfo("企业微信", "com.tencent.wework", 0.95f),
        "飞书" to TaskSpec.AppInfo("飞书", "com.ss.android.lark", 0.95f),
        "微博" to TaskSpec.AppInfo("微博", "com.sina.weibo", 0.95f),
        "知乎" to TaskSpec.AppInfo("知乎", "com.zhihu.android", 0.95f),

        // 支付金融
        "支付宝" to TaskSpec.AppInfo("支付宝", "com.eg.android.AlipayGphone", 0.95f),

        // 娱乐
        "抖音" to TaskSpec.AppInfo("抖音", "com.ss.android.ugc.aweme", 0.95f),
        "快手" to TaskSpec.AppInfo("快手", "com.smile.gifmaker", 0.95f),
        "小红书" to TaskSpec.AppInfo("小红书", "com.xingin.xhs", 0.95f),
        "哔哩哔哩" to TaskSpec.AppInfo("哔哩哔哩", "tv.danmaku.bili", 0.95f),
        "b站" to TaskSpec.AppInfo("哔哩哔哩", "tv.danmaku.bili", 0.90f),
        "bilibili" to TaskSpec.AppInfo("哔哩哔哩", "tv.danmaku.bili", 0.90f),
        "爱奇艺" to TaskSpec.AppInfo("爱奇艺", "com.qiyi.video", 0.95f),
        "优酷" to TaskSpec.AppInfo("优酷", "com.youku.phone", 0.95f),
        "腾讯视频" to TaskSpec.AppInfo("腾讯视频", "com.tencent.qqlive", 0.95f),
        "网易云音乐" to TaskSpec.AppInfo("网易云音乐", "com.netease.cloudmusic", 0.95f),
        "qq音乐" to TaskSpec.AppInfo("QQ音乐", "com.tencent.qqmusic", 0.95f),
        "酷狗" to TaskSpec.AppInfo("酷狗音乐", "com.kugou.android", 0.90f),
        "喜马拉雅" to TaskSpec.AppInfo("喜马拉雅", "com.ximalaya.ting.android", 0.90f),

        // 外卖生活
        "美团" to TaskSpec.AppInfo("美团", "com.sankuai.meituan", 0.95f),
        "饿了么" to TaskSpec.AppInfo("饿了么", "me.ele", 0.95f),
        "大众点评" to TaskSpec.AppInfo("大众点评", "com.dianping.v1", 0.90f),
        "滴滴" to TaskSpec.AppInfo("滴滴出行", "com.sdu.didi.psnger", 0.95f),
        "高德" to TaskSpec.AppInfo("高德地图", "com.autonavi.minimap", 0.95f),
        "百度地图" to TaskSpec.AppInfo("百度地图", "com.baidu.BaiduMap", 0.95f),

        // 工具
        "设置" to TaskSpec.AppInfo("设置", "com.android.settings", 0.99f),
        "系统设置" to TaskSpec.AppInfo("设置", "com.android.settings", 0.99f),
        "浏览器" to TaskSpec.AppInfo("浏览器", "com.android.browser", 0.80f),
        "相册" to TaskSpec.AppInfo("相册", "com.android.gallery3d", 0.80f),
        "图库" to TaskSpec.AppInfo("图库", "com.android.gallery3d", 0.80f),
        "时钟" to TaskSpec.AppInfo("时钟", "com.android.deskclock", 0.80f),
        "闹钟" to TaskSpec.AppInfo("闹钟", "com.android.deskclock", 0.80f),
        "计算器" to TaskSpec.AppInfo("计算器", "com.android.calculator2", 0.80f),
        "日历" to TaskSpec.AppInfo("日历", "com.android.calendar", 0.80f),
        "相机" to TaskSpec.AppInfo("相机", "com.android.camera", 0.80f),
        "文件管理" to TaskSpec.AppInfo("文件管理", "com.android.filemanager", 0.80f),

        // 出行
        "菜鸟" to TaskSpec.AppInfo("菜鸟", "com.cainiao.wireless", 0.95f),
        "12306" to TaskSpec.AppInfo("铁路12306", "com.MobileTicket", 0.95f),
        "携程" to TaskSpec.AppInfo("携程", "ctrip.android.view", 0.90f),
        "去哪儿" to TaskSpec.AppInfo("去哪儿", "com.Qunar", 0.90f),

        // 百度贴吧
        "百度贴吧" to TaskSpec.AppInfo("百度贴吧", "com.baidu.tieba", 0.95f),
        "贴吧" to TaskSpec.AppInfo("百度贴吧", "com.baidu.tieba", 0.90f)
    )

    // High-risk keywords
    private val HIGH_RISK_KEYWORDS = listOf(
        // 支付相关
        "转账", "支付", "付款", "充值", "提现", "红包", "发红包",
        // 删除相关
        "删除", "注销", "退出登录", "解绑", "卸载", "清除数据", "格式化",
        // 购买相关
        "提交订单", "下单", "购买", "秒杀", "抢购", "加购物车", "结算",
        // 消息相关
        "发送消息", "回复", "自动回复", "群发", "转发",
        // 敏感操作
        "授权", "同意", "确认", "提交", "发布", "公开",
        // 文件操作
        "移动文件", "重命名", "覆盖"
    )

    // Forbidden keywords
    private val FORBIDDEN_KEYWORDS = listOf(
        "转账给", "自动付款", "贷款", "借贷", "信用卡还款",
        "投资", "理财", "炒股", "买基金",
        "借钱", "分期", "白条", "花呗",
        "删除所有", "清空", "批量删除",
        "修改密码", "重置密码", "找回密码"
    )

    // Schedule patterns
    private val TIME_PATTERNS = listOf(
        // 每天早上9点, 每天晚上10点30分
        Regex("""每天[早上晚上中午]*(\d{1,2})[点时:](\d{0,2})[分]?"""),
        // 9点, 9:30, 9时30分
        Regex("""(\d{1,2})[点时:](\d{0,2})"""),
        // 每周一早上9点
        Regex("""每[周星期](\d)[早上晚上中午]*(\d{1,2})[点时]?"""),
        // 5分钟后
        Regex("""(\d{1,2})分钟后"""),
        // 每天
        Regex("""每天"""),
        // 定时
        Regex("""定时"""),
        // 早上, 晚上 (without specific time)
        Regex("""[早上晚上中午]"""),
        // am/pm format
        Regex("""(\d{1,2})\s*(am|pm)""", RegexOption.IGNORE_CASE),
        // every morning/evening
        Regex("""every\s+(morning|evening|night|afternoon)""", RegexOption.IGNORE_CASE),
        // every day at 9am
        Regex("""every\s+day\s+at\s+(\d{1,2})\s*(am|pm)?""", RegexOption.IGNORE_CASE),
        // at 9am, at 9:30pm
        Regex("""at\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""", RegexOption.IGNORE_CASE)
    )

    /**
     * Parse natural language input into a TaskSpec.
     */
    fun parse(input: String): ParseResult {
        val trimmed = input.replace(Regex("[\\r\\n]+"), "").trim()
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
        val lowerInput = input.lowercase()

        // Check for notification triggers
        if (input.contains("通知") || input.contains("收到") || input.contains("消息") ||
            lowerInput.contains("when") || lowerInput.contains("notification") ||
            lowerInput.contains("收到")) {
            return TaskSpec.TriggerConfig(type = "notification")
        }

        // Check time patterns
        for (pattern in TIME_PATTERNS) {
            val match = pattern.find(input) ?: pattern.find(lowerInput)
            if (match != null) {
                val groups = match.groupValues
                val time = when {
                    // Handle am/pm format
                    groups.size >= 3 && groups[2].lowercase() in listOf("am", "pm") -> {
                        var h = groups[1].toIntOrNull() ?: 9
                        val m = if (groups.size > 3 && groups[3].isNotEmpty()) groups[3] else "00"
                        if (groups[2].lowercase() == "pm" && h < 12) h += 12
                        if (groups[2].lowercase() == "am" && h == 12) h = 0
                        "${h.toString().padStart(2, '0')}:${m.padStart(2, '0')}"
                    }
                    // Handle Chinese format
                    groups.size >= 3 && groups[1].isNotEmpty() -> {
                        val h = groups[1].padStart(2, '0')
                        val m = groups[2].ifEmpty { "00" }.padStart(2, '0')
                        "$h:$m"
                    }
                    else -> ""
                }

                // Determine repeat type
                val repeat = when {
                    input.contains("每天") || lowerInput.contains("every day") ||
                    lowerInput.contains("daily") || lowerInput.contains("every morning") ||
                    lowerInput.contains("every evening") -> "daily"
                    input.contains("每周") || input.contains("星期") ||
                    lowerInput.contains("weekly") -> "weekly"
                    else -> "once"
                }

                return TaskSpec.TriggerConfig(
                    type = "schedule",
                    time = time,
                    repeat = repeat
                )
            }
        }

        // Default to manual
        return TaskSpec.TriggerConfig(type = "manual")
    }

    private fun detectApp(input: String): TaskSpec.AppInfo? {
        val lowerInput = input.lowercase()

        // Try exact match first (longer names first)
        val sortedApps = APP_DATABASE.entries.sortedByDescending { it.key.length }
        for ((name, info) in sortedApps) {
            if (input.contains(name, ignoreCase = true) || lowerInput.contains(name.lowercase())) {
                return info
            }
        }

        // Try English app name matching
        val englishAppMap = mapOf(
            "taobao" to "淘宝",
            "tmall" to "天猫",
            "jd" to "京东",
            "pinduoduo" to "拼多多",
            "pdd" to "拼多多",
            "wechat" to "微信",
            "weixin" to "微信",
            "alipay" to "支付宝",
            "douyin" to "抖音",
            "tiktok" to "抖音",
            "xiaohongshu" to "小红书",
            "redbook" to "小红书",
            "bilibili" to "哔哩哔哩",
            "meituan" to "美团",
            "eleme" to "饿了么",
            "didi" to "滴滴",
            "gaode" to "高德",
            "amap" to "高德",
            "settings" to "设置",
            "camera" to "相机",
            "gallery" to "相册",
            "clock" to "时钟",
            "calculator" to "计算器",
            "calendar" to "日历",
            "browser" to "浏览器",
            "dingtalk" to "钉钉",
            "feishu" to "飞书",
            "lark" to "飞书",
            "wework" to "企业微信",
            "weibo" to "微博",
            "zhihu" to "知乎",
            "iqiyi" to "爱奇艺",
            "youku" to "优酷",
            "qq" to "qq",
            "kugou" to "酷狗",
            "ximalaya" to "喜马拉雅"
        )

        for ((english, chinese) in englishAppMap) {
            if (lowerInput.contains(english)) {
                return APP_DATABASE[chinese]
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
        // Remove time-related patterns
        var name = input
            .replace(Regex("""每天[早上晚上中午]*\d*[点时:]\d*[分]?"""), "")
            .replace(Regex("""收到.*?后"""), "")
            .replace(Regex("""every\s+(morning|evening|night|day)\s*(at\s+\d+\s*(am|pm)?)?""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""at\s+\d+\s*(am|pm)?""", RegexOption.IGNORE_CASE), "")
            .replace("帮我", "")
            .replace("自动", "")
            .replace("please", "")
            .replace("auto", "")
            .trim()

        // If name is too short, use app name + action
        if (name.length < 4) {
            val appName = app?.name ?: ""
            val action = when {
                input.contains("打开") || input.contains("open") -> "打开"
                input.contains("签到") || input.contains("check in") -> "签到"
                input.contains("查看") || input.contains("check") -> "查看"
                input.contains("收") || input.contains("collect") -> "收取"
                input.contains("刷") || input.contains("browse") -> "浏览"
                else -> "任务"
            }
            name = if (appName.isNotEmpty()) "${appName}$action" else "自动化任务"
        }

        return name.take(20)
    }

    sealed class ParseResult {
        data class Success(val spec: TaskSpec) : ParseResult()
        data class Error(val message: String) : ParseResult()
        data class Forbidden(val reason: String) : ParseResult()
    }
}
