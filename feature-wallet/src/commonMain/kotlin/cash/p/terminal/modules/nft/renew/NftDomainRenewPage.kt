package cash.p.terminal.modules.nft.renew

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import cash.p.terminal.core.nft.NftDomainExpiry
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.PageResumeEffect
import cash.p.terminal.navigation.navigateUpFrom
import cash.p.terminal.navigation.navigateUpSafely
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class NftDomainRenewPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: NftDomainRenewalViewModel = koinViewModel()
        val state = viewModel.uiState
        val selected = state.selected
        val renew = navigation.rememberDomainRenewal(onFinish = { navigation.navigateUpFrom(this) })

        PageResumeEffect(onResume = viewModel::onResume, onPause = {})

        NftDomainRenewScreen(
            state = state,
            totals = selected.takeIf { it.isNotEmpty() }?.let { SelectionTotals(it) },
            renewUntil = remember { renewUntilText() },
            onToggle = viewModel::onToggle,
            onRenew = { renew(selected) },
            onBack = navigation::navigateUpSafely,
        )
    }
}

/** Cost and fee of exactly this selection: one renewal view model per distinct selection. */
@Composable
private fun SelectionTotals(selected: List<NftDomainExpiry>): NftDomainRenewUiState =
    koinViewModel<NftDomainRenewViewModel>(viewModelStoreOwner = rememberSelectionStoreOwner(selected)) {
        parametersOf(selected, false)
    }.uiState

// The page store would keep every visited selection's view model, and its stale estimate, until the page pops.
@Composable
private fun rememberSelectionStoreOwner(selected: List<NftDomainExpiry>): ViewModelStoreOwner {
    val owner = remember(selected.map { it.nftAddress }) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) {
        onDispose { owner.viewModelStore.clear() }
    }
    return owner
}
