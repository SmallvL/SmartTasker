package com.smarttasker.ui.safety

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.core.parser.TaskSpec
import com.smarttasker.ui.common.*
import com.smarttasker.ui.theme.SmartColors

/**
 * Safety Guard - intercepts high-risk tasks and requires user confirmation.
 */
object SafetyGuard {

    /**
     * Check if a task requires safety confirmation.
     * Returns null if safe, or a SafetyCheck result if risky.
     */
    fun checkTask(spec: TaskSpec): SafetyCheck? {
        return when (spec.risk.level) {
            "critical", "forbidden" -> SafetyCheck(
                level = SafetyLevel.FORBIDDEN,
                title = "⛔ 禁止执行",
                message = "该任务包含禁止操作：${spec.risk.reason}",
                actions = listOf(SafetyAction.CANCEL)
            )
            "high" -> SafetyCheck(
                level = SafetyLevel.HIGH_RISK,
                title = "⚠️ 高风险操作",
                message = "该任务包含高风险操作：${spec.risk.reason}",
                actions = listOf(SafetyAction.CONFIRM_ONCE, SafetyAction.ALWAYS_CONFIRM, SafetyAction.CANCEL)
            )
            "medium" -> SafetyCheck(
                level = SafetyLevel.MEDIUM_RISK,
                title = "注意",
                message = "该任务可能涉及敏感操作",
                actions = listOf(SafetyAction.CONFIRM_ONCE, SafetyAction.CANCEL)
            )
            else -> null
        }
    }

    /**
     * Check if a step action is high-risk.
     */
    fun isStepRisky(stepType: String, summary: String): Boolean {
        val riskyKeywords = listOf("发送", "删除", "提交", "下单", "支付", "转账", "注销")
        return riskyKeywords.any { summary.contains(it) }
    }
}

enum class SafetyLevel { LOW, MEDIUM_RISK, HIGH_RISK, FORBIDDEN }

enum class SafetyAction { CONFIRM_ONCE, ALWAYS_CONFIRM, CANCEL }

data class SafetyCheck(
    val level: SafetyLevel,
    val title: String,
    val message: String,
    val actions: List<SafetyAction>
)

/**
 * Safety confirmation dialog.
 */
@Composable
fun SafetyConfirmDialog(
    check: SafetyCheck,
    onAction: (SafetyAction) -> Unit
) {
    AlertDialog(
        onDismissRequest = { onAction(SafetyAction.CANCEL) },
        icon = {
            Icon(
                when (check.level) {
                    SafetyLevel.FORBIDDEN -> Icons.Outlined.Block
                    SafetyLevel.HIGH_RISK -> Icons.Outlined.Warning
                    SafetyLevel.MEDIUM_RISK -> Icons.Outlined.Info
                    SafetyLevel.LOW -> Icons.Outlined.CheckCircle
                },
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = when (check.level) {
                    SafetyLevel.FORBIDDEN -> SmartColors.danger()
                    SafetyLevel.HIGH_RISK -> SmartColors.warning()
                    SafetyLevel.MEDIUM_RISK -> SmartColors.accent()
                    SafetyLevel.LOW -> SmartColors.success()
                }
            )
        },
        title = {
            Text(
                check.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
        },
        text = {
            Column {
                Text(
                    check.message,
                    fontSize = 15.sp,
                    color = SmartColors.textSecondary()
                )
                if (check.level == SafetyLevel.HIGH_RISK) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "请确认你了解这个操作的后果。本次确认仅对当前执行有效。",
                        fontSize = 13.sp,
                        color = SmartColors.textTertiary()
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (SafetyAction.CONFIRM_ONCE in check.actions) {
                    Button(
                        onClick = { onAction(SafetyAction.CONFIRM_ONCE) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (check.level) {
                                SafetyLevel.FORBIDDEN -> SmartColors.danger()
                                SafetyLevel.HIGH_RISK -> SmartColors.warning()
                                else -> SmartColors.accent()
                            }
                        ),
                        shape = RoundedCornerShape(12)
                    ) {
                        Text("确认执行")
                    }
                }
                if (SafetyAction.ALWAYS_CONFIRM in check.actions) {
                    OutlinedButton(
                        onClick = { onAction(SafetyAction.ALWAYS_CONFIRM) },
                        shape = RoundedCornerShape(12)
                    ) {
                        Text("始终允许")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(SafetyAction.CANCEL) }) {
                Text("取消")
            }
        }
    )
}

/**
 * Safety status badge for task cards.
 */
@Composable
fun SafetyBadge(riskLevel: String) {
    val (text, color) = when (riskLevel) {
        "forbidden" -> "禁止" to SmartColors.danger()
        "critical" -> "禁止" to SmartColors.danger()
        "high" -> "高风险" to SmartColors.warning()
        "medium" -> "中风险" to SmartColors.accent()
        else -> "安全" to SmartColors.success()
    }
    StatusPill(text, color)
}
