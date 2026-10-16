package cash.p.terminal.modules.btcblockchainsettings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.modules.btcblockchainsettings.BtcBlockchainSettingsModule.BlockchainSettingsIcon
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_api_hybrid
import cash.p.terminal.resources.ic_blockchair
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType

@Composable
@Preview(showBackground = true)
private fun BtcBlockchainSettingsScreenPreview() {
    ComposeAppTheme {
        BtcBlockchainSettingsScreen(
            page = BtcBlockchainSettingsPage(Blockchain(BlockchainType.Bitcoin, "Bitcoin", null)),
            uiState = BtcBlockchainSettingsUIState(
                title = "Bitcoin",
                blockchainIconUrl = "https://bitcoin.org/favicon.png",
                restoreSources = listOf(
                    BtcBlockchainSettingsModule.ViewItem(
                        id = "1",
                        title = "Blockchair",
                        subtitle = "Blockchair is a blockchain search and analytics engine",
                        selected = true,
                        icon = BlockchainSettingsIcon.ApiIcon(Res.drawable.ic_blockchair)
                    ),
                    BtcBlockchainSettingsModule.ViewItem(
                        id = "2",
                        title = "Hybrid",
                        subtitle = "Hybrid is a blockchain search and analytics engine",
                        selected = false,
                        icon = BlockchainSettingsIcon.ApiIcon(Res.drawable.ic_api_hybrid)
                    )
                ),
                saveButtonEnabled = true,
                closeScreen = false,
                customPeers = ""
            ),
            onSaveClick = {},
            onSelectRestoreMode = {},
            onCustomPeersChange = {},
            navigation = HSNavigation(NavBackStack()),
            onBlockchainStatusClick = {}
        )
    }
}
