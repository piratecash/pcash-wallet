package cash.p.terminal.shared.desktop

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.ActiveAccountState
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.EnabledWallet
import io.reactivex.Flowable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/** Desktop has no accounts yet: reads are empty, writes are ignored. */
class DesktopWalletManager : IWalletManager {
    override val activeWallets: List<Wallet> = emptyList()
    override val activeWalletsFlow: StateFlow<List<Wallet>> = MutableStateFlow(emptyList())

    override fun save(wallets: List<Wallet>) = Unit
    override suspend fun saveSuspended(wallets: List<Wallet>) = Unit
    override suspend fun saveEnabledWallets(enabledWallets: List<EnabledWallet>) = Unit
    override fun delete(wallets: List<Wallet>) = Unit
    override suspend fun deleteByWallet(wallet: Wallet) = Unit
    override suspend fun deleteByTokenQueryIds(accountId: String, tokenQueryIds: Collection<String>) = Unit
    override fun clear() = Unit
    override fun handle(newWallets: List<Wallet>, deletedWallets: List<Wallet>) = Unit
    override suspend fun getWallets(account: Account): List<Wallet> = emptyList()
}

class DesktopAccountManager : IAccountManager {
    override val hasNonStandardAccount = false
    override val activeAccount: Account? = null
    override val activeAccountStateFlow: Flow<ActiveAccountState> =
        flowOf(ActiveAccountState.ActiveAccount(null))
    override val isAccountsEmpty = true
    override val accounts: List<Account> = emptyList()
    override val accountsFlow: Flow<List<Account>> = flowOf(emptyList())
    override val accountsDeletedFlowable: Flowable<Unit> = Flowable.never()
    override val newAccountBackupRequiredFlow: StateFlow<Account?> = MutableStateFlow(null)

    override fun setActiveAccountId(activeAccountId: String?) = Unit
    override fun updateSignedHashes(signedHashes: Int) = Unit
    override fun account(id: String): Account? = null
    override fun save(account: Account, updateActive: Boolean) = Unit
    override fun import(accounts: List<Account>) = Unit
    override fun update(account: Account) = Unit
    override fun updateName(id: String, name: String) = Unit
    override fun markAsBackedUp(id: String) = Unit
    override fun markAsFileBackedUp(id: String) = Unit
    override suspend fun delete(id: String) = Unit
    override fun clear() = Unit
    override fun getDeletedAccountIds(): List<String> = emptyList()
    override fun clearDeleted(accountIds: List<String>) = Unit
    override fun onHandledBackupRequiredNewAccount() = Unit
    override fun setLevel(level: Int) = Unit
    override fun updateAccountLevels(accountIds: List<String>, level: Int) = Unit
    override fun updateMaxLevel(level: Int) = Unit
    override fun accountsAtLevel(level: Int): List<Account> = emptyList()
}
