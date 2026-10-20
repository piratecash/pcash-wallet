package cash.p.terminal.modules.zcashnetwork

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType

@Preview
@Composable
private fun ZcashNetworkScreenPreview() {
    ComposeAppTheme {
        ZcashNetworkScreen(
            uiState = ZcashNetworkViewModel.UiState(
                isAuto = true,
                autoServerName = "zec.rocks (global)",
                defaultItems = listOf(
                    ZcashNetworkViewModel.ViewItem("zec.rocks (global)", "https://zec.rocks:443", selected = false),
                    ZcashNetworkViewModel.ViewItem("zec.rocks (EU)", "https://eu.zec.rocks:443", selected = false),
                ),
                customItems = listOf(
                    ZcashNetworkViewModel.ViewItem("my.server.example:443", "https://my.server.example:443", false),
                ),
            ),
            blockchain = Blockchain(BlockchainType.Zcash, "Zcash", null),
            serverActions = ZcashServerListActions({}, {}, {}),
            onAddServerClick = {},
            onStatusClick = {},
            onClose = {},
        )
    }
}
