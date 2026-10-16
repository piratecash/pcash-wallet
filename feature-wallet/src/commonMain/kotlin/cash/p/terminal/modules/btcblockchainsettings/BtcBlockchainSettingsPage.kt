package cash.p.terminal.modules.btcblockchainsettings

import androidx.compose.runtime.Composable
import cash.p.terminal.modules.blockchainstatus.blockchainStatusOpener
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import io.horizontalsystems.core.entities.Blockchain
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class BtcBlockchainSettingsPage(val input: Blockchain) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: BtcBlockchainSettingsViewModel = koinViewModel { parametersOf(input) }
        val openStatus = blockchainStatusOpener(navigation)
        BtcBlockchainSettingsScreen(
            page = this,
            uiState = viewModel.uiState,
            navigation = navigation,
            onSaveClick = viewModel::onSaveClick,
            onSelectRestoreMode = viewModel::onSelectRestoreMode,
            onCustomPeersChange = viewModel::onCustomPeersChange,
            onBlockchainStatusClick = openStatus?.let { { it(input) } }
        )
    }
}
