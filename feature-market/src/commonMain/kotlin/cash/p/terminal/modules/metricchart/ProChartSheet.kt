package cash.p.terminal.modules.metricchart

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import cash.p.terminal.navigation.HSBottomSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_chart_24
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.chartview.chart.ChartViewModel
import io.horizontalsystems.chartview.ui.Chart
import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize
import org.jetbrains.compose.resources.painterResource
import io.horizontalsystems.core.IAppNumberFormatter
import org.koin.compose.getKoin
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class ProChartSheet(val input: Input) : HSBottomSheet() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val koin = getKoin()
        val numberFormatter = koinInject<IAppNumberFormatter>()
        val chartType = enumValues<ProChartModule.ChartType>()[input.chartType]
        val chartViewModel = koinViewModel<ChartViewModel> {
            parametersOf(
                koin.get<ProChartService> { parametersOf(input.coinUid, chartType) },
                chartType.numberFormatter(numberFormatter),
            )
        }

        ComposeAppTheme {
            BottomSheetHeader(
                iconPainter = painterResource(Res.drawable.ic_chart_24),
                iconTint = ColorFilter.tint(ComposeAppTheme.colors.brandDefault),
                title = input.title,
                onCloseClick = { navigation.navigateUpSafely() }
            ) {
                Chart(
                    uiState = chartViewModel.uiState,
                    getSelectedPointCallback = chartViewModel::getSelectedPoint,
                    onSelectChartInterval = chartViewModel::onSelectChartInterval
                )
                VSpacer(32.dp)
            }
        }
    }

    @CommonParcelize
    data class Input(
        val coinUid: String,
        val title: String,
        val chartType: Int,
    ) : CommonParcelable
}
