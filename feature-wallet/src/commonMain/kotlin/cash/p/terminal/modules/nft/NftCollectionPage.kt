package cash.p.terminal.modules.nft

import androidx.compose.runtime.Composable
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.modules.nft.manage.NftManagePage
import cash.p.terminal.modules.receive.ReceivePage
import cash.p.terminal.navigation.PageResumeEffect
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class NftCollectionPage(private val source: NftCollectionSource) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: NftCollectionsViewModel = koinViewModel { parametersOf(source) }
        val hud = rememberHudMessenger()

        PageResumeEffect(onResume = viewModel::onResume, onPause = {})

        NftCollectionScreen(
            uiState = viewModel.uiState,
            onEyeClick = {
                hud.vibrate()
                viewModel.toggleHidden()
            },
            onItemClick = { navigation.slideFromRight(NftPreviewPage(it, source)) },
            onManage = { navigation.slideFromRight(NftManagePage(source)) },
            onBack = navigation::navigateUpSafely,
            onReceive = viewModel.receiveWallet?.let { wallet ->
                { navigation.slideFromRight(ReceivePage(ReceivePage.Input(wallet))) }
            },
        )
    }
}
