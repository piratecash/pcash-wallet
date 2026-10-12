package cash.p.terminal.ui.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.logo_ethereum_24
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
private fun MarketCoinPreview() {
    ComposeAppTheme {
        MarketCoin(
            title = "ETH",
            subtitle = "Ethereum With very long name for token",
            coinIconUrl = "eth.png",
            coinIconPlaceholder = Res.drawable.logo_ethereum_24,
            value = "$2600",
        )
    }
}
