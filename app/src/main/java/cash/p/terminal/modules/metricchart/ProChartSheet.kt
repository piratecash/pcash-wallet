package cash.p.terminal.modules.metricchart

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.navigation.HSBottomSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely
import io.horizontalsystems.chartview.chart.ChartViewModel
import io.horizontalsystems.chartview.ui.Chart
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.parcelize.Parcelize

class ProChartSheet(val input: Input) : HSBottomSheet() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val chartViewModel = viewModel<ChartViewModel>(
            factory = ProChartModule.Factory(
                input.coinUid,
                enumValues<ProChartModule.ChartType>()[input.chartType]
            )
        )

        ComposeAppTheme {
            BottomSheetHeader(
                iconPainter = painterResource(R.drawable.ic_chart_24),
                iconTint = ColorFilter.tint(ComposeAppTheme.colors.jacob),
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

    @Parcelize
    data class Input(
        val coinUid: String,
        val title: String,
        val chartType: Int,
    ) : Parcelable
}
