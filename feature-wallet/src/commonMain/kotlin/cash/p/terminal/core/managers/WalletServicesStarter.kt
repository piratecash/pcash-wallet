package cash.p.terminal.core.managers

import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.wallet.IAdapterManager
import co.touchlab.kermit.Logger
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val logger = Logger.withTag("WalletServicesStarter")
private const val DELETED_ACCOUNTS_CLEANUP_DELAY_MS = 3000L

/** Wallet services both platforms start once per process; Android interleaves the steps with its own startup work. */
class WalletServicesStarter(
    private val chainAccountManagersStarter: ChainAccountManagersStarter,
    private val adapterManager: IAdapterManager,
    private val locallyCreatedTransactionRepository: LocallyCreatedTransactionRepository,
    private val addressLabelManager: AddressLabelManager,
    private val contactsRepository: ContactsRepository,
    private val evmLabelManager: EvmLabelManager,
    private val dispatcherProvider: DispatcherProvider,
) {
    /** Every step in the order Android runs them. */
    suspend fun start() {
        startChainAccountManagers()
        startAdapters()
        scheduleAccountDataCleanup()
        syncLabels()
    }

    fun startChainAccountManagers() = chainAccountManagersStarter.start()

    fun startAdapters() = adapterManager.startAdapterManager()

    /** Returns at once: both jobs run on the application scope, the cleanup after a delay. */
    fun scheduleAccountDataCleanup() {
        dispatcherProvider.applicationScope.launch { locallyCreatedTransactionRepository.trimAllAccounts() }
        dispatcherProvider.applicationScope.launch { clearDeletedAccounts() }
    }

    suspend fun syncLabels() {
        addressLabelManager.initialize()
        contactsRepository.initialize()
        evmLabelManager.sync()
    }

    private suspend fun clearDeletedAccounts() {
        delay(DELETED_ACCOUNTS_CLEANUP_DELAY_MS)
        try {
            // Resolved only now: the cleaner's graph is not needed for startup.
            getKoinInstance<DeletedAccountsCleanup>().invoke()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "Failed to clear deleted accounts; retrying on next start" }
        }
    }
}
