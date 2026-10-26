package cash.p.terminal.featureStacking.ui.stackingCoinScreen

import android.content.res.Configuration
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.featureStacking.ui.entities.PayoutViewItem
import cash.p.terminal.network.pirate.domain.enity.PayoutType
import cash.p.terminal.premium.domain.usecase.PremiumType
import cash.p.terminal.ui_compose.entities.SectionItemPosition
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.chartview.ChartViewType
import io.horizontalsystems.chartview.chart.ChartModule
import io.horizontalsystems.chartview.chart.ChartUiState
import java.math.BigDecimal

@Preview(
    showBackground = true,
    backgroundColor = 0xFF888888,
    uiMode = Configuration.UI_MODE_NIGHT_NO
)
@Preview(
    showBackground = true,
    backgroundColor = 0,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun PirateCoinScreenContentPreview() {
    ComposeAppTheme {
        PirateCoinScreenContent(
            uiState = StackingCoinUIState(
                balance = BigDecimal(10),
                unpaidStr = "",
                loading = false,
                balanceHidden = false
            ),
            premiumType = PremiumType.PIRATE,
            daysPremiumLeft = 3,
            onBuyClicked = {},
            onChartClicked = {},
            graphUIState = ChartUiState(
                chartHeaderView = ChartModule.ChartHeaderView(
                    value = "Value",
                    valueHint = "Value hint",
                    date = "Date",
                    diff = null,
                    extraData = ChartModule.ChartHeaderExtraData.Volume("Volume")
                ),
                tabItems = listOf(),
                loading = false,
                viewState = ViewState.Success,
                hasVolumes = false,
                chartViewType = ChartViewType.Line,
                chartInfoData = null,
                considerAlwaysPositive = true,
                titleHidden = false
            ),
            getSelectedPointCallback = {
                ChartModule.ChartHeaderView(
                    value = "Value",
                    valueHint = "Value hint",
                    date = "Date",
                    diff = null,
                    extraData = ChartModule.ChartHeaderExtraData.Volume("Volume")
                )
            },
            onSelectChartInterval = {},
            onCalculatorClicked = {},
            onToggleBalanceVisibility = {}
        )
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF888888,
    uiMode = Configuration.UI_MODE_NIGHT_NO
)
@Preview(
    showBackground = true,
    backgroundColor = 0,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun PayoutCellPreview() {
    ComposeAppTheme {
        PayoutCell(
            item = PayoutViewItem(
                id = 1,
                date = "DECEMBER 15, 2023",
                time = "2021-09-01 12:00",
                payoutType = PayoutType.INCOME,
                amount = 123.45.toBigDecimal(),
                amountSecondary = "$123.4"
            ),
            position = SectionItemPosition.Single
        )
    }
}


@Preview(
    showBackground = true,
    backgroundColor = 0xFF888888,
    uiMode = Configuration.UI_MODE_NIGHT_NO
)
@Preview(
    showBackground = true,
    backgroundColor = 0,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun PayoutListPreview() {
    ComposeAppTheme {
        LazyColumn {
            payoutList(
                payoutItemsMap = mapOf(
                    "DECEMBER 15, 2023" to listOf(
                        PayoutViewItem(
                            id = 1,
                            date = "DECEMBER 15, 2023",
                            time = "10:00",
                            payoutType = PayoutType.INCOME,
                            amount = 123.45.toBigDecimal(),
                            amountSecondary = "$123.4"
                        ),
                        PayoutViewItem(
                            id = 2,
                            date = "DECEMBER 15, 2023",
                            time = "09:55",
                            payoutType = PayoutType.INCOME,
                            amount = 123.45.toBigDecimal(),
                            amountSecondary = "$123.4"
                        )
                    ),
                    "DECEMBER 10, 2023" to listOf(
                        PayoutViewItem(
                            id = 3,
                            date = "DECEMBER 10, 2023",
                            time = "09:55",
                            payoutType = PayoutType.INCOME,
                            amount = 123.45.toBigDecimal(),
                            amountSecondary = "$123.4"
                        ),
                        PayoutViewItem(
                            id = 4,
                            date = "DECEMBER 10, 2023",
                            time = "06:55",
                            payoutType = PayoutType.WITHDRAWAL,
                            amount = 123.45.toBigDecimal(),
                            amountSecondary = "$123.4"
                        )
                    )
                )
            )
        }
    }
}
