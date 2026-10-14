package cash.p.terminal.modules.settings.appearance

import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely

class AppearancePage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = koinViewModel<AppearanceViewModel>()

        AppearanceScreen(
            uiState = viewModel.uiState,
            onThemeSelect = viewModel::onEnterTheme,
            onLaunchScreenSelect = viewModel::onEnterLaunchPage,
            onBalanceViewTypeSelect = viewModel::onEnterBalanceViewType,
            onPriceChangeIntervalSelect = viewModel::onSetPriceChangeInterval,
            onMarketTabsHiddenChange = viewModel::onSetMarketTabsHidden,
            onBalanceTabButtonsHiddenChange = viewModel::onSetBalanceTabButtonsHidden,
            onAppIconClick = { navigation.slideFromRight(AppIconPage()) },
            onClose = navigation::navigateUpSafely
        )
    }
}
