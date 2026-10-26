package cash.p.terminal.shared.desktop

import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.core.ITermsManager
import cash.p.terminal.core.StorageLockedException
import cash.p.terminal.core.managers.WalletServicesStarter
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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Loads the wallet on every unlock and drops it on wipe; a lock hides the UI only and changes nothing here.
 * The wallet services start on the first unlock only: a wipe ends the process, so one process serves one storage.
 */
class DesktopWalletSession(
    private val vault: DesktopVault,
    private val userManager: UserManager,
    private val accountManager: IAccountManager,
    private val termsManager: ITermsManager,
    private val contactsRepository: ContactsRepository,
    private val checkPremiumUseCase: Lazy<CheckPremiumUseCase>,
    private val walletServicesStarter: Lazy<WalletServicesStarter>,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val logger = Logger.withTag("DesktopWalletSession")
    private val servicesStarted = AtomicBoolean(false)

    fun start(scope: CoroutineScope): Job = scope.launch(dispatcherProvider.io) {
        vault.state.collect { state ->
            when (state) {
                is DesktopVaultState.Unlocked -> {
                    onUnlocked()
                    // Its own coroutine: the label sync waits on the network and must not hold up a wipe.
                    if (servicesStarted.compareAndSet(false, true)) launch { startWalletServices() }
                }
                DesktopVaultState.NoVault -> onWiped()
                DesktopVaultState.Locked -> Unit
            }
        }
    }

    private fun onUnlocked() = whileSessionOpen {
        userManager.setUserLevel(0)
        checkPremiumUseCase.value.startWarmUp()
    }

    private suspend fun startWalletServices() = whileSessionOpen { walletServicesStarter.value.start() }

    private inline fun whileSessionOpen(block: () -> Unit) {
        try {
            block()
        } catch (e: StorageLockedException) {
            // A wipe right after the unlock closed the session; the NoVault state that follows cleans up.
            logger.w(e) { "Session closed while loading the wallet" }
        }
    }

    private suspend fun onWiped() {
        // Never built before an unlock: it reads the storage.
        if (checkPremiumUseCase.isInitialized()) checkPremiumUseCase.value.stopWarmUp()
        accountManager.unload()
        contactsRepository.clear()
        userManager.resetUserLevel()
        termsManager.reload()
    }
}
