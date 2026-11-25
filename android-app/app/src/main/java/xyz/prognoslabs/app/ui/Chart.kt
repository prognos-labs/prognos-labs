package xyz.prognoslabs.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Price chart for the market detail screen. Real points only, with the
 * dashed win line when the window has started. Never a made-up shape.
 *
 * v8: the fill is lime when the price sits above the win line (as the
 * preview draws it) and the end dot is lime with a dark ring, not plain
 * ink. Height 150dp, as the preview's chart area.
 */

/**
 * Only the chart points a chart can safely draw: each point is a
 * [time, price] pair with finite values. One malformed point from the
 * server used to crash the whole market detail screen, because the
 * chart read point[1] with no check. Points that fail the check are
 * dropped, never drawn, never crashed on.
 */
fun validChartPoints(points: List<List<Double>>): List<List<Double>> =
    points.filter { it.size >= 2 && it[0].isFinite() && it[1].isFinite() }

@Composable
fun PriceChart(
    points: List<List<Double>>,
    line: Double?,
    modifier: Modifier = Modifier
) {
    val valid = validChartPoints(points)
    val ink = appColors().ink
    val greyFill = appColors().grey2
    Canvas(modifier = modifier.fillMaxWidth().height(150.dp)) {
        if (valid.size < 2) return@Canvas
        val values = valid.map { it[1] }
        var minV = values.minOrNull() ?: return@Canvas
        var maxV = values.maxOrNull() ?: return@Canvas
        if (line != null) {
            minV = minOf(minV, line)
            maxV = maxOf(maxV, line)
        }
        if (maxV - minV < 0.0001) {
            maxV = minV + 1.0
        }
        val pad = (maxV - minV) * 0.08
        minV -= pad
        maxV += pad
        val w = size.width
        val h = size.height
        fun x(i: Int): Float = if (valid.size <= 1) 0f else w * i / (valid.size - 1).toFloat()
        fun y(v: Double): Float = (h - ((v - minV) / (maxV - minV) * h).toFloat())

        // Soft fill under the price line: lime above the win line, as the
        // preview draws it; red below it; grey when there is no line yet.
        val fillColor = when {
            line == null -> greyFill.copy(alpha = 0.12f)
            (values.lastOrNull() ?: 0.0) >= line -> Lime.copy(alpha = 0.18f)
            else -> Color(0xFFC62828).copy(alpha = 0.12f)
        }
        val fillPath = Path().apply {
            moveTo(0f, y(values[0]))
            for (i in values.indices) lineTo(x(i), y(values[i]))
            lineTo(x(values.size - 1), h)
            lineTo(0f, h)
            close()
        }
        drawPath(fillPath, fillColor)

        // Dashed win line.
        if (line != null) {
            val ly = y(line)
            var dx = 0f
            while (dx < w) {
                drawLine(
                    color = ink,
                    start = Offset(dx, ly),
                    end = Offset(minOf(dx + 10f, w), ly),
                    strokeWidth = 2f
                )
                dx += 18f
            }
        }

        // Price line.
        val path = Path().apply {
            moveTo(0f, y(values[0]))
            for (i in values.indices) lineTo(x(i), y(values[i]))
        }
        drawPath(path, ink, style = Stroke(width = 3f))
        // End dot: lime with a dark ring, as the preview draws it.
        drawCircle(ink, radius = 8f, center = Offset(x(values.size - 1), y(values.last())))
        drawCircle(Lime, radius = 5f, center = Offset(x(values.size - 1), y(values.last())))
    }
}
