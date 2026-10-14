package io.horizontalsystems.chartview

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.chartview.models.ChartConfig

@Composable
fun ChartMinimal(data: ChartData, modifier: Modifier = Modifier) {
    val config = ChartConfig(ComposeAppTheme.colors, LocalDensity.current)
    val color = config.curveColor(data)

    Spacer(
        modifier.drawWithCache {
            val points = curveAnimator(data, size).getFramePoints(config.strokeWidth / 2f)
            val path = Path().apply {
                points.firstOrNull()?.let { moveTo(it.x, it.y) }
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            onDrawBehind {
                when (points.size) {
                    0 -> Unit
                    1 -> drawLine(color, Offset(0f, points.first().y), Offset(size.width, points.first().y), config.strokeWidth)
                    else -> drawPath(path, color, style = Stroke(width = config.strokeWidth))
                }
            }
        }
    )
}

private fun curveAnimator(data: ChartData, size: Size): CurveAnimator {
    val values = data.valuesByTimestamp()
    return CurveAnimator(
        values,
        data.startTimestamp,
        data.endTimestamp,
        values.values.minOrNull() ?: 0f,
        values.values.maxOrNull() ?: 0f,
        null,
        size.width,
        size.height,
        0f,
        0f,
        0f,
    ).apply { nextFrame(1f) }
}
