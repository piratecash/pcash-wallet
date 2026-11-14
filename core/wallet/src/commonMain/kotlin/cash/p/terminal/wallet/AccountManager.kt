package cash.p.terminal.wallet

import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.useCases.IGetMoneroWalletFilesNameUseCase
import cash.p.terminal.wallet.useCases.IRemoveMoneroWalletFilesUseCase
import co.touchlab.kermit.Logger
import io.horizontalsystems.core.DispatcherProvider
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

class AccountManager(
    private val storage: IAccountsStorage,
    private val getMoneroWalletFilesNameUseCase: IGetMoneroWalletFilesNameUseCase,
    private val removeMoneroWalletFilesUseCase: IRemoveMoneroWalletFilesUseCase,
    private val balanceHiddenManager: IBalanceHiddenManager,
    private val deletionPreflight: AccountDeletionPreflight,
    private val dispatcherProvider: DispatcherProvider,
) : IAccountManager {
    private val logger = Logger.withTag("AccountManager")

    /** The accounts of one load; [generation] changes with every load, so a write from an earlier one is told apart. */
    private data class CacheState(val level: Int, val accounts: Map<String, Account>, val generation: Long)

    private val cache = AtomicReference(CacheState(LEVEL_NOT_SET, emptyMap(), 0))
    private val accountsDeletedSubject = PublishSubject.create<Unit>()
    private val _activeAccountStateFlow =
        MutableStateFlow<ActiveAccountState>(ActiveAccountState.NotLoaded)
    private var currentLevel = LEVEL_NOT_SET

    override val visibleAccounts: VisibleAccounts?
        get() = cache.get().let { state ->
            state.takeIf { it.level != LEVEL_NOT_SET }?.let { VisibleAccounts(it.level, it.accounts.keys) }
        }

    override val activeAccountStateFlow = _activeAccountStateFlow

    override val hasNonStandardAccount: Boolean
        get() = cache.get().accounts.any { it.value.nonStandard }

    override var activeAccount: Account? = null

    override val isAccountsEmpty: Boolean
        get() = storage.isAccountsEmpty

    override val accounts: List<Account>
        get() = cache.get().accounts.values.toList()

    private val _accountsSharedFlow = MutableSharedFlow<List<Account>>(
        replay = 0,
        extraBufferCapacity = 64
    )

    override val accountsFlow: Flow<List<Account>>
        get() = _accountsSharedFlow.asSharedFlow()

    override val accountsDeletedFlowable: Flowable<Unit>
        get() = accountsDeletedSubject.toFlowable(BackpressureStrategy.BUFFER)

    private val _newAccountBackupRequiredFlow = MutableStateFlow<Account?>(null)
    override val newAccountBackupRequiredFlow = _newAccountBackupRequiredFlow.asStateFlow()

    private val generation: Long get() = cache.get().generation

    /** Applies only to the load the operation started against; false when a later one replaced it. */
    private fun putAccount(startGeneration: Long, account: Account, mustExist: Boolean): Boolean {
        var applied = false
        cache.updateAndGet { state ->
            applied = state.generation == startGeneration && (!mustExist || account.id in state.accounts)
            if (applied) state.copy(accounts = LinkedHashMap(state.accounts).apply { put(account.id, account) }) else state
        }
        return applied
    }

    override fun setActiveAccountId(activeAccountId: String?) {
        if (activeAccount?.id != activeAccountId || (activeAccount == null && activeAccountId == null)) {
            storage.setActiveAccountId(currentLevel, activeAccountId)
            activeAccount = activeAccountId?.let { account(it) }
            balanceHiddenManager.clearSessionState()
            _activeAccountStateFlow.update {
                ActiveAccountState.ActiveAccount(activeAccount)
            }
        }
    }

    override fun updateSignedHashes(signedHashes: Int) {
        activeAccount?.let {
            val hardwareCard = it.type as? AccountType.HardwareCard ?: return
            val startGeneration = generation
            val updatedAccount = it.copy(
                type = hardwareCard.copy(
                    signedHashes = signedHashes
                )
            )
            storage.update(updatedAccount)
            if (!putAccount(startGeneration, updatedAccount, mustExist = true)) return
            activeAccount = updatedAccount
            _activeAccountStateFlow.update {
                ActiveAccountState.ActiveAccount(updatedAccount)
            }
        }
    }

    override fun account(id: String): Account? {
        return cache.get().accounts[id]
    }

    override fun onHandledBackupRequiredNewAccount() {
        _newAccountBackupRequiredFlow.update { null }
    }

    override fun save(account: Account, updateActive: Boolean) {
        val startGeneration = generation
        storage.save(account)

        if (!putAccount(startGeneration, account, mustExist = false)) return
        _accountsSharedFlow.tryEmit(accounts)

        if (updateActive) {
            setActiveAccountId(account.id)
        }
        if (!account.isBackedUp && !account.isFileBackedUp) {
            _newAccountBackupRequiredFlow.update {
                account
            }
        }
    }

    override fun import(accounts: List<Account>) {
        val startGeneration = generation
        val landed = accounts.filter { account ->
            storage.save(account)
            putAccount(startGeneration, account, mustExist = false)
        }

        _accountsSharedFlow.tryEmit(accounts)

        if (activeAccount == null) {
            landed.minByOrNull { it.name.lowercase() }?.let { account ->
                setActiveAccountId(account.id)
                if (!account.isBackedUp && !account.isFileBackedUp) {
                    _newAccountBackupRequiredFlow.update {
                        account
                    }
                }
            }
        }
    }

    override fun update(account: Account) {
        val startGeneration = generation
        storage.update(account)
        modifyCachedAccount(startGeneration, account.id) { account }
    }

    override fun updateName(id: String, name: String) {
        val startGeneration = generation
        storage.updateName(id, name)
        modifyCachedAccount(startGeneration, id) { it.copy(name = name) }
    }

    override fun markAsBackedUp(id: String) {
        val state = cache.get()
        val current = state.accounts[id] ?: storage.loadAccount(id) ?: return
        storage.updateBackupFlags(id, isBackedUp = true, isFileBackedUp = current.isFileBackedUp)
        modifyCachedAccount(state.generation, id) { it.copy(isBackedUp = true) }
    }

    override fun markAsFileBackedUp(id: String) {
        val state = cache.get()
        val current = state.accounts[id] ?: storage.loadAccount(id) ?: return
        storage.updateBackupFlags(id, isBackedUp = current.isBackedUp, isFileBackedUp = true)
        modifyCachedAccount(state.generation, id) { it.copy(isFileBackedUp = true) }
    }

    private fun modifyCachedAccount(startGeneration: Long, id: String, transform: (Account) -> Account) {
        var updated: Account? = null
        cache.updateAndGet { state ->
            val cached = state.accounts[id]
            updated = cached?.takeIf { state.generation == startGeneration }?.let(transform)
            updated?.let { state.copy(accounts = LinkedHashMap(state.accounts).apply { put(id, it) }) } ?: state
        }
        updated?.let { account ->
            _accountsSharedFlow.tryEmit(accounts)

            if (activeAccount?.id == id) {
                activeAccount = account
                _activeAccountStateFlow.update { ActiveAccountState.ActiveAccount(account) }
            }
        }
    }

    override suspend fun delete(id: String) {
        withContext(dispatcherProvider.io) {
            deletionPreflight.ensureCanDelete(listOf(id))
            val accountToDelete = storage.loadAccount(id)
            try {
                storage.delete(id)
            } catch (error: Exception) {
                throw AccountDeletionBlockedException(error)
            }
            // Before the active-account switch retires the deleted account's adapters.
            deletionPreflight.fenceDeleted(listOf(id))
            removeFromSession(id)
            dispatcherProvider.applicationScope.launch { cleanupDeleted(id, accountToDelete) }
        }
    }

    // The tombstone is durable, so DeletedAccountsCleanup finishes whatever fails here on the next start.
    private suspend fun cleanupDeleted(id: String, account: Account?) {
        try {
            deletionPreflight.cleanupDeleted(listOf(id))
            val walletFiles = account?.let { getMoneroWalletFilesNameUseCase(it) } ?: return
            if (!removeMoneroWalletFilesUseCase(walletFiles)) {
                logger.w { "Monero wallet files remain; retried on next start" }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logger.w { "Deleted account cleanup deferred to next start: ${error.javaClass.simpleName}" }
        }
    }

    private fun removeFromSession(id: String) {
        cache.updateAndGet { state ->
            if (id in state.accounts) state.copy(accounts = LinkedHashMap(state.accounts).apply { remove(id) }) else state
        }
        _newAccountBackupRequiredFlow.update { account ->
            account?.takeUnless { it.id == id }
        }

        _accountsSharedFlow.tryEmit(accounts)
        accountsDeletedSubject.onNext(Unit)

        if (id == activeAccount?.id) {
            setActiveAccountId(accounts.firstOrNull()?.id)
        }
    }

    override fun clear() {
        storage.clear()
        cache.updateAndGet { it.copy(accounts = emptyMap()) }
        _accountsSharedFlow.tryEmit(listOf())
        accountsDeletedSubject.onNext(Unit)
        setActiveAccountId(null)
    }

    override fun setLevel(level: Int) {
        val loaded = replaceCache(level, storage.allAccounts(level))
        // The alert carries the whole account, seed included: it must not outlive the account's visibility.
        _newAccountBackupRequiredFlow.update { pending -> pending?.takeIf { it.id in loaded } }
        val activeAccountIdForLevel = storage.getActiveAccountId(level)

        if (activeAccountIdForLevel != null && loaded.isEmpty() && currentLevel != LEVEL_NOT_SET) {
            logger.i { "Keystore problems, can't decode accounts, ignore account changing level" }
            return // looks like we can't decode accounts due to Keystore problems(found on Android 11 Oppo devices)
        }
        currentLevel = level

        if (activeAccount == null || activeAccount?.id != activeAccountIdForLevel) {
            activeAccount = loaded[activeAccountIdForLevel] ?: loaded.values.firstOrNull()
            _activeAccountStateFlow.update {
                ActiveAccountState.ActiveAccount(activeAccount)
            }
        }
        _accountsSharedFlow.tryEmit(accounts)
    }

    override fun unload() {
        replaceCache(LEVEL_NOT_SET, emptyList())
        currentLevel = LEVEL_NOT_SET
        activeAccount = null
        _newAccountBackupRequiredFlow.update { null }
        _activeAccountStateFlow.update { ActiveAccountState.NotLoaded }
        _accountsSharedFlow.tryEmit(accounts)
    }

    private fun replaceCache(level: Int, accounts: List<Account>): Map<String, Account> {
        val loaded = accounts.associateBy { it.id }
        cache.updateAndGet { CacheState(level, loaded, it.generation + 1) }
        return loaded
    }

    override fun getDeletedAccountIds() = storage.getDeletedAccountIds()
    override fun clearDeleted(accountIds: List<String>) = storage.clearDeleted(accountIds)

    override fun accountsAtLevel(level: Int): List<Account> {
        return storage.allAccounts(level)
    }

    private companion object {
        const val LEVEL_NOT_SET = Int.MAX_VALUE
    }
}

class NoActiveAccount : Exception()

sealed class ActiveAccountState {
    class ActiveAccount(val account: Account?) : ActiveAccountState()
    object NotLoaded : ActiveAccountState()
}
