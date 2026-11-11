package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.core.StorageLockedException
import cash.p.terminal.core.storage.AccountDaos
import cash.p.terminal.core.storage.LevelDaos
import cash.p.terminal.core.storage.SharedDaos
import cash.p.terminal.core.storage.StorageRouter
import cash.p.terminal.premium.data.database.PremiumDatabase
import cash.p.terminal.premium.data.database.PremiumDatabaseRouter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

private val DesktopVault.session: DesktopStorageSession get() = openSession() ?: throw StorageLockedException()

private val DesktopVault.rootLevel: Int get() = openSession()?.rootLevel ?: 0

/** [setLevel] and [collapseLevelsAbove] block on the vault lock and file I/O: never call them on the UI thread. */
class DesktopStorageRouter(private val vault: DesktopVault) : StorageRouter {
    override val shared: SharedDaos get() = vault.session.shared

    override val rootLevel: Int get() = vault.rootLevel

    override fun level(level: Int): LevelDaos = vault.session.level(level)

    override fun account(accountId: String): AccountDaos? = vault.session.account(accountId)

    override val accounts: List<AccountDaos> get() = vault.session.accounts

    @OptIn(ExperimentalCoroutinesApi::class)
    override val accountStores: Flow<List<AccountDaos>> =
        vault.sessions.flatMapLatest { it?.accountStores ?: flowOf(emptyList()) }

    override fun prepareAccountStore(accountId: String) {
        if (vault.session.account(accountId) == null) {
            vault.exclusive { vault.session.prepareAccountStore(accountId) }
        }
    }

    override fun levelOf(accountId: String): Int? = vault.session.levelOf(accountId)

    override fun setLevel(accountIds: List<String>, level: Int) =
        vault.exclusive { vault.session.setLevel(accountIds, level) }

    override fun collapseLevelsAbove(level: Int) = vault.exclusive { vault.session.collapseLevelsAbove(level) }
}

class DesktopPremiumDatabaseRouter(private val vault: DesktopVault) : PremiumDatabaseRouter {
    override val rootLevel: Int get() = vault.rootLevel

    override fun forLevel(level: Int): PremiumDatabase = vault.session.premium(level)
}
