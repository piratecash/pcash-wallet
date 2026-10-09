package io.horizontalsystems.chartview.models

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import cash.p.terminal.ui_compose.theme.appColors
import io.horizontalsystems.chartview.ChartData
import java.math.BigDecimal

class ChartConfig(private val context: Context) {

    var curveColor = context.appColors().statusSuccess.toArgb()

    val strokeWidth = dp2px(1f)

    fun setTrendColor(chartData: ChartData) {
        val colors = context.appColors()
        curveColor = when {
            chartData.disabled -> colors.iconDisabled
            !chartData.isMovementChart -> colors.brandDefault
            chartData.diff() < BigDecimal.ZERO -> colors.statusError
            else -> colors.statusSuccess
        }.toArgb()
    }

    private fun dp2px(dps: Float) = dps * context.resources.displayMetrics.density + 0.5f
}
