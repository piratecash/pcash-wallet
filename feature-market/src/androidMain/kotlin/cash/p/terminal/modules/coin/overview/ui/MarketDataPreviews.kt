package cash.p.terminal.modules.coin.overview.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.modules.coin.CoinDataItem

@Preview
@Composable
private fun MarketDataPreview() {
    val marketData = listOf(
        CoinDataItem(title = "Market Cap", value = "$123.34 B", rankLabel = "#555"),
        CoinDataItem(
            title = "Trading Volume", value = "112,112,112,112,112,112,112,112,112,112,112,112,112,112,112,112 ETH"
        ),
        CoinDataItem(title = "Inception Date", value = "Jul 23, 2012"),
    )

    ComposeAppTheme(darkTheme = false) {
        MarketData(marketData)
    }
}
