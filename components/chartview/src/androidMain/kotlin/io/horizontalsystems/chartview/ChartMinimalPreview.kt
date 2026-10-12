package io.horizontalsystems.chartview

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.chartview.models.ChartPoint

@Preview
@Composable
private fun ChartMinimalPreview() {
    ComposeAppTheme {
        ChartMinimal(
            data = ChartData(
                items = listOf(ChartPoint(1f, 1000L), ChartPoint(3f, 2000L), ChartPoint(2f, 3000L)),
                isMovementChart = true,
            ),
            modifier = Modifier.width(120.dp).height(40.dp),
        )
    }
}
