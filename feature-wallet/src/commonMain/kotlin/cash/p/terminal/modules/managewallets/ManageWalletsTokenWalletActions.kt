package cash.p.terminal.modules.managewallets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.core.supports
import cash.p.terminal.modules.coin.overview.TokenWalletActions
import cash.p.terminal.modules.enablecoin.restoresettings.RestoreSettingsViewModel
import cash.p.terminal.modules.enablecoin.restoresettings.openRestoreSettingsDialog
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.isHardwareWalletType
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import cash.p.terminal.wallet.policy.PlatformChainSupport

class ManageWalletsTokenWalletActions(
    private val platformChainSupport: PlatformChainSupport,
    private val hardwareWalletTokenPolicy: HardwareWalletTokenPolicy,
) : TokenWalletActions {
    // Bound to the screen's ViewModelStoreOwner by Dialogs(), which the screen composes before any enable/disable click.
    private var manageWalletsViewModel: ManageWalletsViewModel? = null

    override fun supports(token: Token, accountType: AccountType): Boolean =
        platformChainSupport.isSupported(token.blockchainType) &&
            (!accountType.isHardwareWalletType || hardwareWalletTokenPolicy.isSupported(token)) &&
            token.blockchainType.supports(accountType) &&
            token.supports(accountType)

    override fun enable(token: Token) {
        manageWalletsViewModel?.enable(token)
    }

    override fun disable(token: Token) {
        manageWalletsViewModel?.disable(token)
    }

    @Composable
    override fun Dialogs(navigation: HSNavigation) {
        val factory = remember { ManageWalletsModule.Factory() }
        val restoreSettingsViewModel = viewModel<RestoreSettingsViewModel>(factory = factory)
        manageWalletsViewModel = viewModel<ManageWalletsViewModel>(factory = factory)

        navigation.openRestoreSettingsDialog(
            token = restoreSettingsViewModel.openTokenConfigure,
            restoreSettingsViewModel = restoreSettingsViewModel
        )
    }
}
