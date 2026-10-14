package cash.p.terminal.ui.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.modules.market.search.MarketSearchModule.DiscoveryItem
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
private fun ListErrorViewPreview() {
    ComposeAppTheme {
        ListErrorView(errorText = "Sync error. Try again") {
        }
    }
}
@Preview(showBackground = true)
@Composable
private fun CardPreview() {
    ComposeAppTheme {
        CategoryCard(DiscoveryItem.TopCoins, { })
    }
}
