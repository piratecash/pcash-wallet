package cash.p.terminal.modules.nft

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import cash.p.terminal.core.nft.NftCollectionsRepository
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.modules.manageaccount.ui.rememberCopyWithHud
import cash.p.terminal.modules.nft.burn.rememberNftBurn
import cash.p.terminal.modules.nft.renew.rememberDomainRenewal
import cash.p.terminal.modules.nft.transfer.NftTransferPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.PageResumeEffect
import cash.p.terminal.navigation.navigateUpFrom
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.rememberResultLauncher
import cash.p.terminal.navigation.slideFromBottom
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.nft_toast_hidden_undo
import cash.p.terminal.resources.nft_toast_not_spam
import cash.p.terminal.resources.nft_toast_reported
import cash.p.terminal.resources.nft_undo
import cash.p.terminal.ui_compose.components.HudAction
import cash.p.terminal.ui_compose.components.LocalUrlOpener
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class NftPreviewPage(
    private val item: NftItemRecord,
    private val source: NftCollectionSource,
) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: NftPreviewViewModel = koinViewModel { parametersOf(source, item) }
        val repository: NftCollectionsRepository = koinInject()
        val hud = rememberHudMessenger()
        val scope = rememberCoroutineScope()
        val urlOpener = LocalUrlOpener.current
        val copyWithHud = rememberCopyWithHud()
        val uiState = viewModel.uiState
        val namesRevealed = uiState.canReveal && !uiState.hidden
        val managedTitle = item.managedTitle(uiState.hidden)
        val network = remember(item) {
            NftNetworkInfo(
                title = repository.networkTitle(item.blockchainType),
                explorerUrl = repository.explorerUrl(item.blockchainType, item.address),
            )
        }
        val renewDomain = navigation.rememberDomainRenewal(
            namesRevealed = namesRevealed,
            onFinish = viewModel::onResume,
        )
        val burn = navigation.rememberNftBurn(
            namesRevealed = namesRevealed,
            onFinish = { navigation.navigateUpFrom(this) },
        )

        fun hide() {
            viewModel.onHide()
            scope.launch {
                val undo = HudAction(getString(Res.string.nft_undo), viewModel::onUndoHide)
                hud.showMessage(getString(Res.string.nft_toast_hidden_undo, managedTitle), undo)
                navigation.navigateUpFrom(this@NftPreviewPage)
            }
        }

        fun reportSpam() {
            viewModel.onReportSpam()
            scope.launch { hud.showSuccess(getString(Res.string.nft_toast_reported)) }
        }

        fun notSpam() {
            viewModel.onNotSpam()
            scope.launch { hud.showSuccess(getString(Res.string.nft_toast_not_spam, managedTitle)) }
        }

        // Results arrive later than this composition: they must see the latest state.
        val currentReport by rememberUpdatedState(::reportSpam)
        val reportLauncher = navigation.rememberResultLauncher<NftReportSpamSheet.Confirmed> { currentReport() }
        fun openReport() = reportLauncher.slideFromBottom(NftReportSpamSheet(managedTitle))

        fun onAction(action: NftAction) = when (action) {
            NftAction.HideCollection, NftAction.HideNft -> hide()
            NftAction.HideAndReport -> openReport()
            NftAction.OpenExplorer -> network.explorerUrl?.let(urlOpener::open) ?: Unit
            NftAction.CopyAddress -> copyWithHud(item.address)
            NftAction.Burn -> burn(item, (uiState.transferAvailability as? NftTransferAvailability.Available)?.domain)
        }

        val currentAction by rememberUpdatedState(::onAction)
        // The open sheet holds a snapshot; eligibility (e.g. burn) may have been revoked since.
        val actionsLauncher = navigation.rememberResultLauncher<NftActionsSheet.Result> {
            if (it.action in viewModel.uiState.menuActions) currentAction(it.action)
        }

        PageResumeEffect(onResume = viewModel::onResume, onPause = {})

        NftPreviewScreen(
            item = item,
            uiState = uiState,
            network = network,
            actions = NftPreviewActions(
                onArtworkClick = {
                    hud.vibrate()
                    viewModel.onToggleReveal()
                },
                onSend = {
                    val page = NftTransferPage(item, it.wallet, it.domain, viewModel.uiState.domainExpiry)
                    navigation.slideFromRight(page)
                },
                onRenew = { renewDomain(listOf(it)) },
                onMenu = { actionsLauncher.slideFromBottom(NftActionsSheet(uiState.menuActions)) },
                onReportSpam = ::openReport,
                onNotSpam = ::notSpam,
                onBack = navigation::navigateUpSafely,
            ),
        )
    }
}
