package cash.p.terminal.modules.coin.indicators

import androidx.compose.runtime.Composable
import cash.p.terminal.modules.chart.ChartIndicatorManager
import cash.p.terminal.modules.chart.ChartIndicatorSetting
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.resources.Error_ParameterNotSet
import cash.p.terminal.resources.Res
import cash.p.terminal.ui_compose.components.LocalHudMessenger
import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

class IndicatorSettingsPage(val input: Input) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val indicatorSetting = koinInject<ChartIndicatorManager>().getChartIndicatorSetting(input.indicatorId)

        if (indicatorSetting == null) {
            LocalHudMessenger.current.showError(stringResource(Res.string.Error_ParameterNotSet))
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

    @CommonParcelize
    data class Input(val indicatorId: String) : CommonParcelable
}
