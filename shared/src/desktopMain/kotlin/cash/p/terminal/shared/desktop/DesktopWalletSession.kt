package cash.p.terminal.shared.desktop

import cash.p.terminal.core.ITermsManager
import cash.p.terminal.core.StorageLockedException
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.DesktopVaultState
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.managers.UserManager
import co.touchlab.kermit.Logger
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Loads the wallet on every unlock and drops it on wipe; a lock hides the UI only and changes nothing here. */
class DesktopWalletSession(
    private val vault: DesktopVault,
    private val userManager: UserManager,
    private val accountManager: IAccountManager,
    private val termsManager: ITermsManager,
    private val checkPremiumUseCase: CheckPremiumUseCase,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val logger = Logger.withTag("DesktopWalletSession")

    fun start(scope: CoroutineScope): Job = scope.launch(dispatcherProvider.io) {
        vault.state.collect { state ->
            when (state) {
                is DesktopVaultState.Unlocked -> onUnlocked()
                DesktopVaultState.NoVault -> onWiped()
                DesktopVaultState.Locked -> Unit
            }
        }
    }

    private fun onUnlocked() {
        try {
            userManager.setUserLevel(0)
            checkPremiumUseCase.startWarmUp()
        } catch (e: StorageLockedException) {
            // A wipe right after the unlock closed the session; the NoVault state that follows cleans up.
            logger.w(e) { "Session closed while loading the wallet" }
        }
    }

    private suspend fun onWiped() {
        checkPremiumUseCase.stopWarmUp()
        accountManager.unload()
        userManager.resetUserLevel()
        termsManager.reload()
    }
}
