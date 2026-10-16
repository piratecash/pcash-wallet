package cash.p.terminal.modules.zcashnetwork

import androidx.compose.runtime.Composable
import cash.p.terminal.modules.blockchainstatus.blockchainStatusOpener
import cash.p.terminal.modules.zcashnetwork.addserver.AddZcashServerPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.EvmNetwork_Added
import cash.p.terminal.resources.Hud_Removed
import cash.p.terminal.resources.Res
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import io.horizontalsystems.core.entities.Blockchain
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

class ZcashNetworkPage(private val blockchain: Blockchain) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: ZcashNetworkViewModel = koinViewModel()
        val hudMessenger = rememberHudMessenger()
        val removedText = stringResource(Res.string.Hud_Removed)
        val addedText = stringResource(Res.string.EvmNetwork_Added)
        val openStatus = blockchainStatusOpener(navigation)
        ZcashNetworkScreen(
            uiState = viewModel.uiState,
            blockchain = blockchain,
            serverActions = ZcashServerListActions(
                onSelectAuto = viewModel::onSelectAuto,
                onSelect = viewModel::onSelect,
                onDelete = {
                    viewModel.onDelete(it)
                    hudMessenger.showError(removedText)
                },
            ),
            onAddServerClick = {
                navigation.slideFromBottomForResult<AddZcashServerPage.Result>(AddZcashServerPage()) {
                    hudMessenger.showSuccess(addedText)
                }
            },
            onStatusClick = openStatus?.let { { it(blockchain) } },
            onClose = navigation::navigateUpSafely,
        )
    }
}
