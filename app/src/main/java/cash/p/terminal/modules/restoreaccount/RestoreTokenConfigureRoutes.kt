package cash.p.terminal.modules.restoreaccount

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import cash.p.terminal.R
import cash.p.terminal.core.title
import cash.p.terminal.modules.enablecoin.restoresettings.TokenConfig
import cash.p.terminal.modules.moneroconfigure.MoneroConfigureRoute
import cash.p.terminal.modules.moneroconfigure.MoneroConfigureViewModel
import cash.p.terminal.modules.mwebconfigure.MwebConfigureViewModel
import cash.p.terminal.modules.zcashconfigure.ZcashConfigureScreen
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpFrom
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.isLitecoinMweb
import io.horizontalsystems.core.entities.BlockchainType
import org.koin.compose.viewmodel.koinViewModel
import kotlin.reflect.KClass

internal fun HSNavigation.openRestoreTokenConfigure(
    token: Token,
    initialConfig: TokenConfig?,
    mainViewModel: RestoreViewModel,
    owner: KClass<out HSPage>,
) {
    mainViewModel.setTokenInitialConfig(initialConfig)
    when (token.blockchainType) {
        BlockchainType.Zcash -> slideFromBottom(RestoreZcashConfigurePage(owner))
        BlockchainType.Monero -> slideFromBottom(RestoreMoneroConfigurePage(owner))
        BlockchainType.Litecoin -> {
            if (token.isLitecoinMweb) {
                slideFromBottom(RestoreMwebConfigurePage(owner))
            }
        }

        else -> Unit
    }
}

class RestoreZcashConfigurePage(val owner: KClass<out HSPage>) : HSPage(screenshotEnabled = false) {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val mainViewModel = navigation.restoreViewModel(owner)
        ZcashConfigureScreen(
            initialConfig = mainViewModel.tokenInitialConfig,
            onCloseWithResult = { config -> navigation.closeWithTokenConfig(this, mainViewModel, config) },
            onCloseClick = { navigation.cancelTokenConfig(mainViewModel) }
        )
    }
}

class RestoreMoneroConfigurePage(val owner: KClass<out HSPage>) : HSPage(screenshotEnabled = false) {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val mainViewModel = navigation.restoreViewModel(owner)
        val viewModel: MoneroConfigureViewModel = koinViewModel()
        LaunchedEffect(mainViewModel.tokenInitialConfig) {
            viewModel.setInitialConfig(mainViewModel.tokenInitialConfig)
        }
        MoneroConfigureRoute(
            onCloseWithResult = { navigation.closeWithTokenConfig(this, mainViewModel, it) },
            onCloseClick = { navigation.cancelTokenConfig(mainViewModel) },
            onModeSelect = viewModel::onModeSelect,
            onSetBirthdayHeight = viewModel::setBirthdayHeight,
            onDatePick = viewModel::onDatePicked,
            onDoneClick = viewModel::onDoneClick,
            uiState = viewModel.uiState,
        )
    }
}

class RestoreMwebConfigurePage(val owner: KClass<out HSPage>) : HSPage(screenshotEnabled = false) {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val mainViewModel = navigation.restoreViewModel(owner)
        val viewModel: MwebConfigureViewModel = koinViewModel()
        LaunchedEffect(mainViewModel.tokenInitialConfig) {
            viewModel.setInitialConfig(mainViewModel.tokenInitialConfig)
        }
        MoneroConfigureRoute(
            title = TokenType.Mweb.title,
            blockchainType = BlockchainType.Litecoin,
            heightHintRes = R.string.restoreheight_hint_block_only,
            onCloseWithResult = { navigation.closeWithTokenConfig(this, mainViewModel, it) },
            onCloseClick = { navigation.cancelTokenConfig(mainViewModel) },
            onModeSelect = viewModel::onModeSelect,
            onSetBirthdayHeight = viewModel::setBirthdayHeight,
            onDoneClick = viewModel::onDoneClick,
            uiState = viewModel.uiState,
        )
    }
}

// Raised by a LaunchedEffect, so it must not pop the page beneath once this one is gone.
private fun HSNavigation.closeWithTokenConfig(page: HSPage, mainViewModel: RestoreViewModel, config: TokenConfig) {
    mainViewModel.setTokenConfig(config)
    navigateUpFrom(page)
}

private fun HSNavigation.cancelTokenConfig(mainViewModel: RestoreViewModel) {
    mainViewModel.cancelTokenConfig()
    navigateUpSafely()
}
