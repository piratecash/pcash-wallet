package cash.p.terminal.modules.enablecoin.restoresettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import cash.p.terminal.modules.moneroconfigure.MoneroConfigurePage
import cash.p.terminal.modules.mwebconfigure.MwebConfigurePage
import cash.p.terminal.modules.zcashconfigure.ZcashConfigurePage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.isLitecoinMweb
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.delay

@Composable
fun HSNavigation.openRestoreSettingsDialog(
    token: Token?,
    restoreSettingsViewModel: IRestoreSettingsUi
) {
    val keyboard = LocalSoftwareKeyboardController.current

    fun handleResult(config: TokenConfig?) {
        if (config != null) {
            restoreSettingsViewModel.onEnter(config)
        } else {
            restoreSettingsViewModel.onCancelEnterBirthdayHeight()
        }
    }

    LaunchedEffect(token) {
        val tokenToConfigure = token ?: return@LaunchedEffect
        keyboard?.hide()
        // Let IME finish closing before opening the bottom sheet.
        delay(300)

        restoreSettingsViewModel.tokenConfigureOpened()
        val initialConfig = restoreSettingsViewModel.consumeInitialConfig()

        when (tokenToConfigure.blockchainType) {
            BlockchainType.Zcash -> {
                slideFromBottomForResult<ZcashConfigurePage.Result>(
                    ZcashConfigurePage(ZcashConfigurePage.Input(initialConfig))
                ) { result ->
                    handleResult(result.config)
                }
            }

            BlockchainType.Monero -> {
                slideFromBottomForResult<MoneroConfigurePage.Result>(
                    MoneroConfigurePage(MoneroConfigurePage.Input(initialConfig))
                ) { result ->
                    handleResult(result.config)
                }
            }

            BlockchainType.Litecoin -> {
                if (tokenToConfigure.isLitecoinMweb) {
                    slideFromBottomForResult<MwebConfigurePage.Result>(
                        MwebConfigurePage(MwebConfigurePage.Input(initialConfig))
                    ) { result ->
                        handleResult(result.config)
                    }
                }
            }

            else -> Unit
        }
    }
}
