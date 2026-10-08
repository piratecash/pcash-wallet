package cash.p.terminal.modules.btcblockchainsettings

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.modules.blockchainstatus.BlockchainStatusPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import io.horizontalsystems.core.entities.Blockchain

class BtcBlockchainSettingsPage(val input: Blockchain) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = viewModel<BtcBlockchainSettingsViewModel>(
            factory = BtcBlockchainSettingsModule.Factory(input)
        )
        BtcBlockchainSettingsScreen(
            uiState = viewModel.uiState,
            navigation = navigation,
            onSaveClick = viewModel::onSaveClick,
            onSelectRestoreMode = viewModel::onSelectRestoreMode,
            onCustomPeersChange = viewModel::onCustomPeersChange,
            onBlockchainStatusClick = { navigation.slideFromRight(BlockchainStatusPage(input)) }
        )
    }
}
