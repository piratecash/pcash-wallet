package cash.p.terminal.modules.enablecoin.restoresettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import cash.p.terminal.modules.moneroconfigure.MoneroConfigurePage
import cash.p.terminal.modules.mwebconfigure.MwebConfigurePage
import cash.p.terminal.modules.zcashconfigure.ZcashConfigurePage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.rememberResultLauncher
import cash.p.terminal.navigation.slideFromBottom
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

    val zcashLauncher = rememberResultLauncher<ZcashConfigurePage.Result> { handleResult(it.config) }
    val moneroLauncher = rememberResultLauncher<MoneroConfigurePage.Result> { handleResult(it.config) }
    val mwebLauncher = rememberResultLauncher<MwebConfigurePage.Result> { handleResult(it.config) }

    LaunchedEffect(token) {
        val tokenToConfigure = token ?: return@LaunchedEffect
        keyboard?.hide()
        // Let IME finish closing before opening the bottom sheet.
        delay(300)

        restoreSettingsViewModel.tokenConfigureOpened()
        val initialConfig = restoreSettingsViewModel.consumeInitialConfig()

        when (tokenToConfigure.blockchainType) {
            BlockchainType.Zcash ->
                zcashLauncher.slideFromBottom(ZcashConfigurePage(ZcashConfigurePage.Input(initialConfig)))

            BlockchainType.Monero ->
                moneroLauncher.slideFromBottom(MoneroConfigurePage(MoneroConfigurePage.Input(initialConfig)))

            BlockchainType.Litecoin -> {
                if (tokenToConfigure.isLitecoinMweb) {
                    mwebLauncher.slideFromBottom(MwebConfigurePage(MwebConfigurePage.Input(initialConfig)))
                }
            }

            else -> Unit
        }
    }
}
