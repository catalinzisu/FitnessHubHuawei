package ro.fitnesshub.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.max

enum class ChartKind { Line, Bars }

/**
 * A small dependency-free chart: a line (trend of a value) or bars (amount per item).
 * Shows the max (and, for lines, the min) on the left and the first/last x label below.
 */
@Composable
fun SimpleChart(
    values: List<Double>,
    kind: ChartKind,
    formatValue: (Double) -> String,
    startLabel: String,
    endLabel: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()

    Column(modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .semantics { contentDescription = description },
        ) {
            if (values.isEmpty()) return@Canvas
            val maxValue = values.max()
            val minValue = if (kind == ChartKind.Bars) 0.0 else values.min()
            // A flat line still gets some range, so it sits in the middle instead of on an edge.
            val span = (maxValue - minValue).takeIf { it > 0 } ?: max(1.0, maxValue * 0.1)
            val low = if (kind == ChartKind.Bars) 0.0 else minValue - span * 0.15
            val high = maxValue + span * 0.15

            val topLabel = measurer.measure(formatValue(maxValue), labelStyle)
            val bottomLabel = measurer.measure(formatValue(minValue), labelStyle)
            val plotLeft = max(topLabel.size.width, bottomLabel.size.width) + 8.dp.toPx()
            val plotWidth = size.width - plotLeft
            val plotHeight = size.height
            fun y(value: Double): Float = (plotHeight - (value - low) / (high - low) * plotHeight).toFloat()

            listOf(maxValue to topLabel, minValue to bottomLabel).forEach { (value, label) ->
                drawLine(gridColor, Offset(plotLeft, y(value)), Offset(size.width, y(value)), strokeWidth = 1.dp.toPx())
                val top = (y(value) - label.size.height / 2f).coerceIn(0f, size.height - label.size.height)
                drawText(label, topLeft = Offset(0f, top))
            }

            when (kind) {
                ChartKind.Line -> {
                    val radius = 3.dp.toPx()
                    val left = plotLeft + radius
                    val width = plotWidth - 2 * radius
                    val points = values.mapIndexed { index, value ->
                        val x = if (values.size == 1) left + width / 2 else left + width * index / (values.size - 1)
                        Offset(x, y(value))
                    }
                    if (points.size > 1) {
                        val path = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            points.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
                    }
                    points.forEach { drawCircle(color, radius, it) }
                }
                ChartKind.Bars -> {
                    val slot = plotWidth / values.size
                    val barWidth = slot * 0.6f
                    values.forEachIndexed { index, value ->
                        val top = y(value)
                        drawRect(
                            color,
                            topLeft = Offset(plotLeft + index * slot + (slot - barWidth) / 2, top),
                            size = Size(barWidth, plotHeight - top),
                        )
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(startLabel, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Text(endLabel, style = MaterialTheme.typography.labelSmall)
        }
    }
}
