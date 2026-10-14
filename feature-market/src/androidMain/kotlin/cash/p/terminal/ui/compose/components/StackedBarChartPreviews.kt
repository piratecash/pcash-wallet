package cash.p.terminal.ui.compose.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
private fun StackedBarChartPreview() {
    val slices = listOf(
        StackBarSlice(value = 60f, color = Color(0xFF6B7196)),
        StackBarSlice(value = 31f, color = Color(0xFFF3BA2F)),
        StackBarSlice(value = 8f, color = Color(0xFF8247E5)),
        StackBarSlice(value = 1f, color = Color(0xFFD74F49))
    )
    ComposeAppTheme {
        StackedBarChart(
            slices,
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}
