package com.rynekryz.sniffsniff.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.rynekryz.sniffsniff.core.common.model.RatePoint

@Composable
fun TrafficChart(points: List<RatePoint>, modifier: Modifier = Modifier) {
    val download = MaterialTheme.colorScheme.primary
    val upload = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    Canvas(modifier.fillMaxWidth().height(120.dp)) {
        val maxValue = maxOf(1L, points.maxOfOrNull { maxOf(it.upload, it.download) } ?: 1L).toFloat()
        for (i in 1..3) {
            val y = size.height * i / 4f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        if (points.size < 2) return@Canvas
        val step = size.width / (MAX_POINTS - 1)
        val startX = size.width - step * (points.size - 1)
        fun x(index: Int) = startX + step * index
        fun y(value: Long) = size.height - (value / maxValue) * (size.height - 4.dp.toPx()) - 2.dp.toPx()
        val area = Path().apply {
            moveTo(x(0), size.height)
            points.forEachIndexed { i, p -> lineTo(x(i), y(p.download)) }
            lineTo(x(points.lastIndex), size.height)
            close()
        }
        drawPath(area, download.copy(alpha = 0.18f))
        val downloadLine = Path().apply {
            points.forEachIndexed { i, p -> if (i == 0) moveTo(x(i), y(p.download)) else lineTo(x(i), y(p.download)) }
        }
        val uploadLine = Path().apply {
            points.forEachIndexed { i, p -> if (i == 0) moveTo(x(i), y(p.upload)) else lineTo(x(i), y(p.upload)) }
        }
        drawPath(downloadLine, download, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        drawPath(uploadLine, upload, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

private const val MAX_POINTS = 60
