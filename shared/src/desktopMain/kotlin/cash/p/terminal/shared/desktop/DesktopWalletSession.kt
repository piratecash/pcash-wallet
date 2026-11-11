package cash.p.terminal.shared.desktop

import cash.p.terminal.core.ITermsManager
import cash.p.terminal.core.StorageLockedException
import cash.p.terminal.core.managers.AmlStatusManager
import cash.p.terminal.core.managers.BitcoinKitDatabaseManager
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.core.managers.TransactionAdapterManager
import cash.p.terminal.core.managers.WalletServicesStarter
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.shared.desktop.vault.DesktopStorageSession
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.DesktopVaultState
import cash.p.terminal.shared.desktop.vault.DesktopWalletLayer
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.managers.UserManager
import co.touchlab.kermit.Logger
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Loads the wallet for every new storage session and drops it on wipe; a lock hides the UI only and changes nothing
 * here. The wallet services start on the first unlock only and then follow the accounts, whichever session holds them.
 */
class DesktopWalletSession(
    private val vault: DesktopVault,
    private val userManager: UserManager,
    private val accountManager: IAccountManager,
    private val termsManager: ITermsManager,
    private val contactsRepository: ContactsRepository,
    private val checkPremiumUseCase: Lazy<CheckPremiumUseCase>,
    private val walletServicesStarter: Lazy<WalletServicesStarter>,
    private val accountHolders: DesktopWalletAccountHolders,
    private val dispatcherProvider: DispatcherProvider,
) : DesktopWalletLayer {
    private val logger = Logger.withTag("DesktopWalletSession")
    private val servicesStarted = AtomicBoolean(false)

    // Touched by the state collector only.
    private var loadedSession: DesktopStorageSession? = null

    fun start(scope: CoroutineScope): Job = scope.launch(dispatcherProvider.io) {
        vault.state.collect { state ->
            when (state) {
                is DesktopVaultState.Unlocked -> {
                    if (loadedSession !== state.session) {
                        loadedSession = state.session
                        onSessionOpened(state.session)
                    }
                    // Its own coroutine: the label sync waits on the network and must not hold up a wipe.
                    if (servicesStarted.compareAndSet(false, true)) launch { startWalletServices() }
                }
                DesktopVaultState.NoVault -> onWiped()
                DesktopVaultState.Locked -> Unit
            }
        }
    }

    override fun onLevelNarrowing() {
        accountHolders.onLevelNarrowing()
        if (checkPremiumUseCase.isInitialized()) checkPremiumUseCase.value.cancelWarmUp()
    }

    override suspend fun onLevelNarrowed(level: Int) {
        stopPremiumWarmUp()
        whileSessionOpen {
            userManager.setUserLevel(level)
            dropActiveAccountOfEmptyLevel(level)
            accountHolders.onLevelChanged()
            // The premium of a session lives in its root: it restarts from the new one.
            checkPremiumUseCase.value.startWarmUp()
        }
    }

    override suspend fun awaitReleased(keptAccountIds: Set<String>) {
        try {
            // Never built before the first unlock event: nothing of theirs can be running.
            if (!servicesStarted.get()) return
            accountHolders.awaitReleased(keptAccountIds)
        } finally {
            accountHolders.onReleaseEnded()
        }
    }

    /** Contacts stay: their file and the labels belong to the shared safe, the same for the session that follows. */
    override suspend fun onSessionClosing() {
        stopPremiumWarmUp()
        accountManager.unload()
        userManager.resetUserLevel()
        accountHolders.onSessionClosed()
    }

    /** From the session's own root: a narrowing may have overtaken this collector. */
    private suspend fun onSessionOpened(session: DesktopStorageSession) = whileSessionOpen {
        // Before the level: its accounts bring adapters that read the offline flags at once.
        accountHolders.onSessionOpened()
        userManager.setUserLevel(session.rootLevel)
        accountHolders.onLevelChanged()
        checkPremiumUseCase.value.startWarmUp()
    }

    /** A saved selection of a deleted account makes setLevel keep the current one; a new load ignores it. */
    private fun dropActiveAccountOfEmptyLevel(level: Int) {
        if (accountManager.accounts.isEmpty() && accountManager.activeAccount != null) {
            accountManager.unload()
            accountManager.setLevel(level)
        }
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
        loadedSession = null
        stopPremiumWarmUp()
        accountManager.unload()
        contactsRepository.clear()
        userManager.resetUserLevel()
        termsManager.reload()
    }

    private suspend fun stopPremiumWarmUp() {
        // Never built before an unlock: it reads the storage.
        if (checkPremiumUseCase.isInitialized()) checkPremiumUseCase.value.stopWarmUp()
    }
}

/** The wallet services that keep per-account state beyond what follows the account flows on its own. */
class DesktopWalletAccountHolders(
    private val adapterManager: Lazy<IAdapterManager>,
    private val transactionAdapterManager: Lazy<TransactionAdapterManager>,
    private val offlineModeManager: Lazy<OfflineModeManager>,
    private val bitcoinKitDatabaseManager: Lazy<BitcoinKitDatabaseManager>,
    private val amlStatusManager: Lazy<AmlStatusManager>,
    private val logGate: DesktopLogGate,
) {
    /** Until the release ends the kits above still log, and the router already points to the level below. */
    fun onLevelNarrowing() = logGate.releaseStarted()

    fun onReleaseEnded() = logGate.releaseEnded()

    suspend fun awaitReleased(keptAccountIds: Set<String>) {
        adapterManager.value.awaitReleased(keptAccountIds)
        transactionAdapterManager.value.awaitReleased(keptAccountIds)
        offlineModeManager.value.retainAccounts(keptAccountIds)
        bitcoinKitDatabaseManager.value.retainAccounts(keptAccountIds)
    }

    /** A narrowing that fails before any release leaves no one to end it. */
    fun onSessionClosed() = logGate.reset()

    suspend fun onSessionOpened() = offlineModeManager.value.reload()

    /** Reads for the current level, so it comes after the level is set. */
    fun onLevelChanged() = amlStatusManager.value.reloadEnabled()
}
