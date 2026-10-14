package cash.p.terminal.modules.market.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.logo_ethereum_24
import cash.p.terminal.ui_compose.components.ImageSource
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
private fun HeaderContentPreview() {
    ComposeAppTheme {
        HeaderContent(
            "Solana Ecosystem",
            "Market cap of all protocols on the Solana chain",
            ImageSource.Local(Res.drawable.logo_ethereum_24)
        )
    }
}
