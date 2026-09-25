package cash.p.terminal.modules.coin.indicators

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.modules.chart.ChartIndicatorSetting
import cash.p.terminal.ui_compose.components.HudHelper
import kotlinx.parcelize.Parcelize

class IndicatorSettingsPage(val input: Input) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val indicatorSetting = App.chartIndicatorManager.getChartIndicatorSetting(input.indicatorId)

        if (indicatorSetting == null) {
            HudHelper.showErrorMessage(LocalView.current, R.string.Error_ParameterNotSet)
            navigation.navigateUp()
        } else {
            when (indicatorSetting.type) {
                ChartIndicatorSetting.IndicatorType.MA -> {
                    EmaSettingsScreen(
                        navigation = navigation,
                        indicatorSetting = indicatorSetting
                    )
                }

                ChartIndicatorSetting.IndicatorType.RSI -> {
                    RsiSettingsScreen(
                        navigation = navigation,
                        indicatorSetting = indicatorSetting
                    )
                }

                ChartIndicatorSetting.IndicatorType.MACD -> {
                    MacdSettingsScreen(
                        navigation = navigation,
                        indicatorSetting = indicatorSetting
                    )
                }
            }
        }
    }

    @Parcelize
    data class Input(val indicatorId: String) : Parcelable
}
