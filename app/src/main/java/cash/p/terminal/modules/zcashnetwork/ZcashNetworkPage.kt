package cash.p.terminal.modules.zcashnetwork

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import cash.p.terminal.R
import cash.p.terminal.modules.blockchainstatus.BlockchainStatusPage
import cash.p.terminal.modules.zcashnetwork.addserver.AddZcashServerPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui_compose.components.HudHelper
import io.horizontalsystems.core.entities.Blockchain
import org.koin.compose.viewmodel.koinViewModel

class ZcashNetworkPage(private val blockchain: Blockchain) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: ZcashNetworkViewModel = koinViewModel()
        val view = LocalView.current
        ZcashNetworkScreen(
            uiState = viewModel.uiState,
            blockchain = blockchain,
            onSelect = viewModel::onSelect,
            onDelete = {
                viewModel.onDelete(it)
                HudHelper.showErrorMessage(view, R.string.Hud_Removed)
            },
            onAddServerClick = {
                navigation.slideFromBottomForResult<AddZcashServerPage.Result>(AddZcashServerPage()) {
                    HudHelper.showSuccessMessage(view, R.string.EvmNetwork_Added)
                }
            },
            onStatusClick = { navigation.slideFromRight(BlockchainStatusPage(blockchain)) },
            onClose = navigation::navigateUpSafely,
        )
    }
}
