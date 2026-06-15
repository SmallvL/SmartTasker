package com.smarttasker.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.ui.theme.SmartColors

/**
 * 成功率环形图 — 大尺寸、动画、渐变弧线
 */
@Composable
fun SuccessRateChart(
    successRate: Float,
    modifier: Modifier = Modifier
) {
    val animatedSweep = remember { Animatable(0f) }

    LaunchedEffect(successRate) {
        animatedSweep.animateTo(
            targetValue = successRate,
            animationSpec = tween(durationMillis = 1200, easing = { it })
        )
    }

    val accentColor = SmartColors.accent()
    val successColor = SmartColors.success()
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textTertiary = SmartColors.textTertiary()

    val percentage = (animatedSweep.value * 100).toInt()

    Box(
        modifier = modifier.size(180.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 16.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val center = Offset(size.width / 2, size.height / 2)

            // 背景圆环
            drawCircle(
                color = trackColor,
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidth)
            )

            // 成功率弧线（渐变）
            val sweepAngle = animatedSweep.value * 360f
            if (sweepAngle > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(accentColor, successColor, accentColor),
                        center = center
                    ),
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // 端点发光圆点
            if (animatedSweep.value > 0f) {
                val endAngleRad = Math.toRadians((-90.0 + sweepAngle))
                val dotX = center.x + radius * kotlin.math.cos(endAngleRad).toFloat()
                val dotY = center.y + radius * kotlin.math.sin(endAngleRad).toFloat()
                drawCircle(
                    color = Color.White,
                    radius = 5.dp.toPx(),
                    center = Offset(dotX, dotY),
                    style = Fill
                )
                drawCircle(
                    color = successColor,
                    radius = 3.dp.toPx(),
                    center = Offset(dotX, dotY),
                    style = Fill
                )
            }
        }

        // 百分比文字
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$percentage%",
                color = textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                lineHeight = 40.sp
            )
            Text(
                text = "成功率",
                color = textTertiary,
                fontSize = 13.sp
            )
        }
    }
}

/**
 * 运行趋势折线图 — 平滑贝塞尔曲线 + 渐变填充 + 动画
 */
@Composable
fun TrendChart(
    dailyStats: List<DailyStat>,
    modifier: Modifier = Modifier
) {
    if (dailyStats.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().height(220.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "暂无数据",
                color = SmartColors.textTertiary(),
                fontSize = 14.sp
            )
        }
        return
    }

    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(dailyStats) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000)
        )
    }

    val successColor = SmartColors.success()
    val dangerColor = SmartColors.danger()
    val textTertiary = SmartColors.textTertiary()
    val gridColor = MaterialTheme.colorScheme.surfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface

    Column(modifier = modifier.fillMaxWidth()) {
        // 图例
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChartLegend(color = successColor, label = "成功")
            Spacer(modifier = Modifier.width(16.dp))
            ChartLegend(color = dangerColor, label = "失败")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 图表
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            val width = size.width
            val height = size.height
            val padding = 8.dp.toPx()
            val chartWidth = width - padding * 2
            val chartHeight = height - padding * 2
            val stepWidth = chartWidth / (dailyStats.size.coerceAtLeast(1))

            val maxRuns = dailyStats.maxOf { it.totalRuns }.toFloat().coerceAtLeast(1f)

            // 绘制网格线
            for (i in 0..4) {
                val y = padding + chartHeight * i / 4
                drawLine(
                    color = gridColor,
                    start = Offset(padding, y),
                    end = Offset(width - padding, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // 计算成功点坐标
            val successPoints = dailyStats.mapIndexed { index, stat ->
                Offset(
                    x = padding + stepWidth * index + stepWidth / 2,
                    y = padding + chartHeight - (stat.successRuns / maxRuns * chartHeight)
                )
            }

            // 计算失败点坐标
            val failedPoints = dailyStats.mapIndexed { index, stat ->
                Offset(
                    x = padding + stepWidth * index + stepWidth / 2,
                    y = padding + chartHeight - (stat.failedRuns / maxRuns * chartHeight)
                )
            }

            val progress = animatedProgress.value

            // 绘制成功曲线 + 渐变填充
            if (successPoints.size >= 2) {
                val visibleCount = (successPoints.size * progress).toInt().coerceIn(1, successPoints.size)
                val visibleSuccess = successPoints.take(visibleCount)

                val successPath = buildSmoothCurvePath(visibleSuccess)
                val fillPath = Path().apply {
                    addPath(successPath)
                    lineTo(visibleSuccess.last().x, padding + chartHeight)
                    lineTo(visibleSuccess.first().x, padding + chartHeight)
                    close()
                }

                // 渐变填充
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            successColor.copy(alpha = 0.25f),
                            successColor.copy(alpha = 0.02f)
                        ),
                        startY = padding,
                        endY = padding + chartHeight
                    )
                )

                // 曲线
                drawPath(
                    path = successPath,
                    color = successColor,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                // 数据点
                visibleSuccess.forEach { point ->
                    drawCircle(
                        color = surfaceColor,
                        radius = 5.dp.toPx(),
                        center = point
                    )
                    drawCircle(
                        color = successColor,
                        radius = 3.5.dp.toPx(),
                        center = point
                    )
                }
            }

            // 绘制失败曲线 + 渐变填充
            if (failedPoints.size >= 2) {
                val visibleCount = (failedPoints.size * progress).toInt().coerceIn(1, failedPoints.size)
                val visibleFailed = failedPoints.take(visibleCount)

                val failedPath = buildSmoothCurvePath(visibleFailed)
                val fillPath = Path().apply {
                    addPath(failedPath)
                    lineTo(visibleFailed.last().x, padding + chartHeight)
                    lineTo(visibleFailed.first().x, padding + chartHeight)
                    close()
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            dangerColor.copy(alpha = 0.15f),
                            dangerColor.copy(alpha = 0.02f)
                        ),
                        startY = padding,
                        endY = padding + chartHeight
                    )
                )

                drawPath(
                    path = failedPath,
                    color = dangerColor,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                visibleFailed.forEach { point ->
                    drawCircle(
                        color = surfaceColor,
                        radius = 5.dp.toPx(),
                        center = point
                    )
                    drawCircle(
                        color = dangerColor,
                        radius = 3.5.dp.toPx(),
                        center = point
                    )
                }
            }
        }

        // X 轴标签
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            dailyStats.forEach { stat ->
                Text(
                    text = stat.date,
                    color = textTertiary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * 构建平滑贝塞尔曲线路径
 */
private fun buildSmoothCurvePath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path

    path.moveTo(points.first().x, points.first().y)

    if (points.size == 1) return path

    for (i in 0 until points.size - 1) {
        val current = points[i]
        val next = points[i + 1]

        val controlX1 = current.x + (next.x - current.x) / 3f
        val controlY1 = current.y
        val controlX2 = next.x - (next.x - current.x) / 3f
        val controlY2 = next.y

        path.cubicTo(
            controlX1, controlY1,
            controlX2, controlY2,
            next.x, next.y
        )
    }

    return path
}

/**
 * 图例组件
 */
@Composable
private fun ChartLegend(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(color = color)
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = SmartColors.textTertiary(),
            fontSize = 12.sp
        )
    }
}

/**
 * 柱状图 — 圆角柱体 + 渐变 + 动画
 */
@Composable
fun BarChart(
    data: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    barColor: Color = Color.Unspecified
) {
    val resolvedBarColor = if (barColor == Color.Unspecified) SmartColors.accent() else barColor
    if (data.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "暂无数据",
                color = SmartColors.textTertiary(),
                fontSize = 14.sp
            )
        }
        return
    }

    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800)
        )
    }

    val maxValue = data.maxOf { it.second }.toFloat().coerceAtLeast(1f)
    val textTertiary = SmartColors.textTertiary()
    val surfaceColor = MaterialTheme.colorScheme.surface

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            val width = size.width
            val height = size.height
            val barSlotWidth = width / data.size
            val barWidth = barSlotWidth * 0.55f
            val cornerRadius = 6.dp.toPx()
            val progress = animatedProgress.value

            data.forEachIndexed { index, (_, value) ->
                val barHeight = (value / maxValue * height * progress).coerceAtLeast(cornerRadius * 2)
                val x = barSlotWidth * index + (barSlotWidth - barWidth) / 2
                val y = height - barHeight

                // 渐变柱体
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            resolvedBarColor,
                            resolvedBarColor.copy(alpha = 0.6f)
                        ),
                        startY = y,
                        endY = height
                    ),
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius)
                )
            }
        }

        // X 轴标签
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            data.forEach { (label, _) ->
                Text(
                    text = label,
                    color = textTertiary,
                    fontSize = 10.sp
                )
            }
        }
    }
}
