package cash.p.terminal.modules.nft

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import cash.p.terminal.core.nft.NftCollectionsRepository
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.modules.nft.renew.rememberDomainRenewal
import cash.p.terminal.modules.nft.transfer.NftTransferPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.PageResumeEffect
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class NftPreviewPage(
    private val item: NftItemRecord,
    private val source: NftCollectionSource,
) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: NftPreviewViewModel = koinViewModel { parametersOf(source) }
        val repository: NftCollectionsRepository = koinInject()
        val hud = rememberHudMessenger()
        val uiState = viewModel.uiState
        val renewDomain = navigation.rememberDomainRenewal(
            namesRevealed = uiState.canReveal && !uiState.hidden,
            onFinish = { viewModel.onResume(item) },
        )

        PageResumeEffect(onResume = { viewModel.onResume(item) }, onPause = {})

        NftPreviewScreen(
            item = item,
            uiState = uiState,
            network = remember(item) {
                NftNetworkInfo(
                    title = repository.networkTitle(item.blockchainType),
                    explorerUrl = repository.explorerUrl(item.blockchainType, item.address),
                )
            },
            onArtworkClick = {
                hud.vibrate()
                viewModel.onToggleReveal()
            },
            onSend = {
                navigation.slideFromRight(NftTransferPage(item, it.wallet, it.domain, viewModel.uiState.domainExpiry))
            },
            onRenew = { renewDomain(listOf(it)) },
            onBack = navigation::navigateUpSafely,
        )
    }
}
