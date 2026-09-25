package cash.p.terminal.modules.coin.indicators

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.R
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.modules.chart.ChartIndicatorSetting
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsIconButton
import cash.p.terminal.ui_compose.components.HsSwitch
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

class IndicatorsPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        IndicatorsScreen(navigation = navigation)
    }
}

@Composable
fun IndicatorsScreen(navigation: HSNavigation) {
    val chartIndicatorsViewModel = viewModel<ChartIndicatorsViewModel>(factory = ChartIndicatorsViewModel.Factory())

    val uiState = chartIndicatorsViewModel.uiState
    val toggleIndicator = { indicator: ChartIndicatorSetting, checked: Boolean ->
        if (checked) {
            chartIndicatorsViewModel.enable(indicator)
        } else {
            chartIndicatorsViewModel.disable(indicator)
        }
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.CoinPage_Indicators),
                navigationIcon = {
                    HsBackButton(onClick = { navigation.navigateUpSafely() })
                }
            )
        }
    ) {
        Column(Modifier.padding(it)) {
            HeaderText(
                stringResource(R.string.CoinPage_MovingAverages).uppercase()
            )
            CellUniversalLawrenceSection(uiState.maIndicators) { indicator: ChartIndicatorSetting ->
                val indicatorDataMa = indicator.getTypedDataMA()
                IndicatorCell(
                    title = indicator.name,
                    checked = indicator.enabled,
                    leftIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_chart_type_2_24),
                            tint = Color(indicatorDataMa.color),
                            contentDescription = null,
                        )
                    },
                    onCheckedChange = {
                        toggleIndicator.invoke(indicator, it)
                    },
                    onEditClick = {
                        navigation.slideFromRight(
                            IndicatorSettingsPage(IndicatorSettingsPage.Input(indicator.id))
                        )
                    }
                )
            }
            VSpacer(24.dp)
            HeaderText(
                stringResource(R.string.CoinPage_OscillatorsSettings).uppercase()
            )
            CellUniversalLawrenceSection(uiState.oscillatorIndicators) { indicator ->
                IndicatorCell(
                    title = indicator.name,
                    checked = indicator.enabled,
                    onCheckedChange = {
                        toggleIndicator.invoke(indicator, it)
                    },
                    onEditClick = {
                        navigation.slideFromRight(
                            IndicatorSettingsPage(IndicatorSettingsPage.Input(indicator.id))
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun IndicatorCell(
    title: String,
    checked: Boolean,
    leftIcon: (@Composable () -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit,
    onEditClick: () -> Unit
) {
    RowUniversal(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        leftIcon?.invoke()
        HSpacer(16.dp)
        body_leah(
            text = title,
            modifier = Modifier.weight(1f)
        )
        HsIconButton(
            modifier = Modifier.size(20.dp),
            onClick = onEditClick
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_edit_20),
                contentDescription = null,
                tint = ComposeAppTheme.colors.grey
            )
        }
        HSpacer(16.dp)
        HsSwitch(
            modifier = Modifier.padding(0.dp),
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Preview
@Composable
private fun Preview_Indicators() {
    ComposeAppTheme {
        IndicatorsScreen(HSNavigation(NavBackStack()))
    }
}
