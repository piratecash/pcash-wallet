package io.horizontalsystems.chartview.models

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import cash.p.terminal.ui_compose.theme.Colors
import io.horizontalsystems.chartview.ChartData
import java.math.BigDecimal

class ChartConfig(private val colors: Colors, density: Density) {

    val strokeWidth = density.density + 0.5f

    fun curveColor(chartData: ChartData): Color = when {
        chartData.disabled -> colors.iconDisabled
        !chartData.isMovementChart -> colors.brandDefault
        chartData.diff() < BigDecimal.ZERO -> colors.statusError
        else -> colors.statusSuccess
    }
}
