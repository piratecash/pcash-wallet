package cash.p.terminal.ui.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.balance.formatProgressPercent
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
private fun SyncProgressTextPreview() {
    ComposeAppTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.05, 1.0, 10.0, 50.0, 99.0, 100.0, 100000.0).forEach { progress ->
                val progressF = (progress.coerceAtLeast(10.0) / 100.0).toFloat()
                val angle = 360f * progressF
                val ringColor = ComposeAppTheme.colors.iconPrimary
                val circleColor = ComposeAppTheme.colors.surfacePlaceholder

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .drawBehind {
                            inset(-1.dp.toPx()) {
                                drawArc(
                                    color = circleColor,
                                    startAngle = 0f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                                )
                                rotate(degrees = -90f) {
                                    drawArc(
                                        color = ringColor,
                                        startAngle = 0f,
                                        sweepAngle = angle,
                                        useCenter = false,
                                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    SyncProgressText("${formatProgressPercent(progress)}%")
                }
            }
        }
    }
}
