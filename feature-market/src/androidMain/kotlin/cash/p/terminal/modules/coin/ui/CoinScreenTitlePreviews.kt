package cash.p.terminal.modules.coin.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
private fun CoinScreenTitleNoRankPreview() {
    ComposeAppTheme {
        CoinScreenTitle(
            coinName = "Synthetix Network TokenSynthetix Network Token",
            marketCapRank = null,
            coinIconUrl = "https://cdn.blocksdecoded.com/coin-icons/32px/bitcoin@3x.png",
            alternativeCoinIconUrl = null,
            iconPlaceholder = null
        )
    }
}
@Preview
@Composable
private fun CoinScreenTitleLongTitlePreview() {
    ComposeAppTheme {
        CoinScreenTitle(
            coinName = "Synthetix Network Token Synthetix Network Token Synthetix Network Token Synthetix Network " +
                    "Token",
            marketCapRank = 123,
            coinIconUrl = "https://cdn.blocksdecoded.com/coin-icons/32px/bitcoin@3x.png",
            alternativeCoinIconUrl = null,
            iconPlaceholder = null
        )
    }
}
@Preview
@Composable
private fun CoinScreenTitleShortTitlePreview() {
    ComposeAppTheme {
        CoinScreenTitle(
            coinName = "Bitcoin",
            marketCapRank = 1,
            coinIconUrl = "https://cdn.blocksdecoded.com/coin-icons/32px/bitcoin@3x.png",
            alternativeCoinIconUrl = null,
            iconPlaceholder = null
        )
    }
}
