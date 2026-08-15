package com.rodrigo.misprecios.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.rodrigo.misprecios.data.PriceHistoryEntry

@Composable
fun PriceHistoryChart(history: List<PriceHistoryEntry>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier.fillMaxWidth().height(100.dp)) {
        if (history.size < 2) return@Canvas

        val prices = history.map { it.price }
        val minPrice = prices.min()
        val maxPrice = prices.max()
        val range = (maxPrice - minPrice).takeIf { it > 0 } ?: 1.0

        val stepX = size.width / (history.size - 1)
        val points = history.mapIndexed { index, entry ->
            val x = index * stepX
            val normalized = (entry.price - minPrice) / range
            val y = size.height - (normalized * size.height).toFloat()
            Offset(x, y)
        }

        for (i in 0 until points.size - 1) {
            drawLine(color = lineColor, start = points[i], end = points[i + 1], strokeWidth = 6f)
        }
        drawCircle(color = lineColor, radius = 10f, center = points.last())
    }
}
