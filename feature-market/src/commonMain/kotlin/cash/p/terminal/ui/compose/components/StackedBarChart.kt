package cash.p.terminal.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun StackedBarChart(
    slices: List<StackBarSlice>,
    modifier: Modifier = Modifier,
) {
    val colors = ComposeAppTheme.colors
    Row(
        modifier = modifier
            .height(40.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        slices
            .filter { it.value >= 1f }
            .forEach {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(it.color ?: if (it.dimmed) colors.statusWarning50 else colors.statusWarning)
                        .weight(it.value),
                )
            }
    }
}

data class StackBarSlice(
    val value: Float,
    val color: Color?,
    val dimmed: Boolean = false,
)
