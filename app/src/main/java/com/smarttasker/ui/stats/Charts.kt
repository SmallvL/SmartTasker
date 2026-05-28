package com.smarttasker.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttasker.ui.theme.*

/**
 * 成功率环形图
 */
@Composable
fun SuccessRateChart(
    successRate: Float,
    modifier: Modifier = Modifier
) {
    val percentage = (successRate * 100).toInt()
    val sweepAngle = successRate * 360f
    
    Box(
        modifier = modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 12.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val center = Offset(size.width / 2, size.height / 2)
            
            // 背景圆环
            drawCircle(
                color = LinearBgSurface,
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidth)
            )
            
            // 成功率弧线
            drawArc(
                color = LinearGreen,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        
        // 百分比文字
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$percentage%",
                color = LinearTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            )
            Text(
                text = "成功率",
                color = LinearTextTertiary,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * 运行趋势折线图
 */
@Composable
fun TrendChart(
    dailyStats: List<DailyStat>,
    modifier: Modifier = Modifier
) {
    if (dailyStats.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "暂无数据",
                color = LinearTextTertiary
            )
        }
        return
    }
    
    val maxRuns = dailyStats.maxOf { it.totalRuns }.toFloat().coerceAtLeast(1f)
    
    Column(modifier = modifier.fillMaxWidth()) {
        // 图例
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            ChartLegend(color = LinearGreen, label = "成功")
            Spacer(modifier = Modifier.width(16.dp))
            ChartLegend(color = LinearRed, label = "失败")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 图表
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            val width = size.width
            val height = size.height
            val stepWidth = width / (dailyStats.size.coerceAtLeast(1))
            
            // 绘制网格线
            for (i in 0..4) {
                val y = height * i / 4
                drawLine(
                    color = LinearBgSurface,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
            
            // 绘制成功线
            val successPoints = dailyStats.mapIndexed { index, stat ->
                Offset(
                    x = stepWidth * index + stepWidth / 2,
                    y = height - (stat.successRuns / maxRuns * height)
                )
            }
            
            for (i in 0 until successPoints.size - 1) {
                drawLine(
                    color = LinearGreen,
                    start = successPoints[i],
                    end = successPoints[i + 1],
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            
            // 绘制失败线
            val failedPoints = dailyStats.mapIndexed { index, stat ->
                Offset(
                    x = stepWidth * index + stepWidth / 2,
                    y = height - (stat.failedRuns / maxRuns * height)
                )
            }
            
            for (i in 0 until failedPoints.size - 1) {
                drawLine(
                    color = LinearRed,
                    start = failedPoints[i],
                    end = failedPoints[i + 1],
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            
            // 绘制数据点
            successPoints.forEach { point ->
                drawCircle(
                    color = LinearGreen,
                    radius = 4.dp.toPx(),
                    center = point
                )
            }
            
            failedPoints.forEach { point ->
                drawCircle(
                    color = LinearRed,
                    radius = 4.dp.toPx(),
                    center = point
                )
            }
        }
        
        // X 轴标签
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            dailyStats.forEach { stat ->
                Text(
                    text = stat.date,
                    color = LinearTextTertiary,
                    fontSize = 10.sp
                )
            }
        }
    }
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
            color = LinearTextTertiary,
            fontSize = 12.sp
        )
    }
}

/**
 * 柱状图
 */
@Composable
fun BarChart(
    data: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    barColor: Color = LinearBrandIndigo
) {
    if (data.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "暂无数据",
                color = LinearTextTertiary
            )
        }
        return
    }
    
    val maxValue = data.maxOf { it.second }.toFloat().coerceAtLeast(1f)
    
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            val width = size.width
            val height = size.height
            val barWidth = width / data.size * 0.6f
            val gap = width / data.size * 0.4f
            
            data.forEachIndexed { index, (label, value) ->
                val barHeight = (value / maxValue) * height
                val x = (barWidth + gap) * index + gap / 2
                val y = height - barHeight
                
                drawRect(
                    color = barColor,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight)
                )
            }
        }
        
        // X 轴标签
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            data.forEach { (label, _) ->
                Text(
                    text = label,
                    color = LinearTextTertiary,
                    fontSize = 10.sp
                )
            }
        }
    }
}
