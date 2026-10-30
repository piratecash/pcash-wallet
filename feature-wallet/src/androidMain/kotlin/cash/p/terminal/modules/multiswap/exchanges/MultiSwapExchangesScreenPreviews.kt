package cash.p.terminal.modules.multiswap.exchanges

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
private fun MultiSwapExchangesScreenPreview() {
    ComposeAppTheme {
        MultiSwapExchangesScreen(
            uiState = MultiSwapExchangesUiState(
                items = listOf(
                    ExchangeCardItem(
                        id = "1",
                        coinIconUrlIn = null,
                        coinIconUrlIntermediate = null,
                        coinIconUrlOut = null,
                        amountInFormatted = "1.5 ETH",
                        amountIntermediateFormatted = "3000 USDT",
                        amountOutFormatted = "--- BTC",
                        activeLine = 1,
                        statusText = "Continue exchange",
                    ),
                    ExchangeCardItem(
                        id = "2",
                        coinIconUrlIn = null,
                        coinIconUrlIntermediate = null,
                        coinIconUrlOut = null,
                        amountInFormatted = "0.5 BTC",
                        amountIntermediateFormatted = "--- USDT",
                        amountOutFormatted = "--- SOL",
                        activeLine = 0,
                        statusText = "Exchanging",
                    ),
                )
            ),
            onSelect = {},
            onDelete = {},
            onBack = {},
        )
    }
}
