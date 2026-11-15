package cash.p.terminal.modules.nft.manage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import cash.p.terminal.modules.nft.NftCollectionSource
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.nft_toast_hidden
import cash.p.terminal.resources.nft_toast_not_spam
import cash.p.terminal.resources.nft_toast_visible
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class NftManagePage(private val source: NftCollectionSource) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: NftManageViewModel = koinViewModel { parametersOf(source) }
        val hud = rememberHudMessenger()
        val scope = rememberCoroutineScope()

        NftManageScreen(
            uiState = viewModel.uiState,
            onVisibleChange = { row, visible, name ->
                viewModel.onVisibleChange(row, visible)
                val toast = if (visible) Res.string.nft_toast_visible else Res.string.nft_toast_hidden
                scope.launch { hud.showSuccess(getString(toast, name)) }
            },
            onNotSpam = { row, name ->
                viewModel.onNotSpam(row)
                scope.launch { hud.showSuccess(getString(Res.string.nft_toast_not_spam, name)) }
            },
            onToggleShowAll = viewModel::onToggleShowAllVisible,
            onBack = navigation::navigateUpSafely,
        )
    }
}
