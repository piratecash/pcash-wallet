package cash.p.terminal.core.storage

import cash.p.terminal.wallet.managers.logIfBelowSessionRoot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * One database for everything, so no account is ever invisible: [account] does not look the row up.
 * Restore settings of a new account are written before its row on purpose (duplicate, backup import).
 */
class SingleDatabaseStorageRouter(private val databaseProvider: AppDatabaseProvider) : StorageRouter {
    private val database: AppDatabase get() = databaseProvider.database

    override val shared: SharedDaos get() = database
    override val rootLevel: Int = 0

    override fun level(level: Int): LevelDaos {
        logIfBelowSessionRoot(level, rootLevel)
        return database
    }

    override fun account(accountId: String): AccountDaos = database

    override val accounts: List<AccountDaos> get() = listOf(database)

    override val accountStores: Flow<List<AccountDaos>> get() = flowOf(accounts)

    override fun prepareAccountStore(accountId: String) = Unit

    override fun levelOf(accountId: String): Int? = database.accountsDao().getLevel(accountId)

    override fun setLevel(accountIds: List<String>, level: Int) {
        database.accountsDao().updateLevels(accountIds, level)
    }

    override fun collapseLevelsAbove(level: Int) {
        database.accountsDao().updateMaxLevel(level)
    }
}
