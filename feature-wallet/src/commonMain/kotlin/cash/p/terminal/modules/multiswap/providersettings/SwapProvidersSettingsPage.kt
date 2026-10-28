package cash.p.terminal.modules.multiswap.providersettings

import androidx.compose.runtime.Composable
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import org.koin.compose.viewmodel.koinViewModel

class SwapProvidersSettingsPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: SwapProvidersSettingsViewModel = koinViewModel()
        SwapProvidersSettingsScreen(
            uiState = viewModel.uiState,
            onToggle = viewModel::setProviderEnabled,
            onClose = navigation::navigateUpSafely,
        )
    }
}
