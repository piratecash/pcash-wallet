package cash.p.terminal.modules.settings.appearance

import androidx.compose.runtime.Composable
import cash.p.terminal.feature.wallet.WalletPlatformPages
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

class AppearancePage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = koinViewModel<AppearanceViewModel>()
        val appIconPage = koinInject<WalletPlatformPages>().appIcon()

        AppearanceScreen(
            uiState = viewModel.uiState,
            actions = AppearanceActions(
                onThemeSelect = viewModel::onEnterTheme,
                onLaunchScreenSelect = viewModel::onEnterLaunchPage,
                onBalanceViewTypeSelect = viewModel::onEnterBalanceViewType,
                onPriceChangeIntervalSelect = viewModel::onSetPriceChangeInterval,
                onMarketTabsHiddenChange = viewModel::onSetMarketTabsHidden,
                onBalanceTabButtonsHiddenChange = viewModel::onSetBalanceTabButtonsHidden,
            ),
            onClose = navigation::navigateUpSafely,
            onAppIconClick = appIconPage?.let { page -> { navigation.slideFromRight(page) } },
        )
    }
}
