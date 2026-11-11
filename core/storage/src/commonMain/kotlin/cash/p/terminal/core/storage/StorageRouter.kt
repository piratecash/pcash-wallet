package cash.p.terminal.core.storage

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** Finds the database that holds a table: consumers resolve it per call, the answer changes with the session. */
interface StorageRouter {
    val shared: SharedDaos

    /** The lowest level this session can open; a lower one is served by this one. */
    val rootLevel: Int

    fun level(level: Int): LevelDaos

    /** Null when the account is not visible from this session; treat it like an absent row. */
    fun account(accountId: String): AccountDaos?

    /** The stores of all visible accounts, for queries not bound to one account. */
    val accounts: List<AccountDaos>

    /** [accounts] as it changes while the session lives. */
    val accountStores: Flow<List<AccountDaos>>

    /** For flows that write before the account row exists: afterwards [account] answers for this id. */
    fun prepareAccountStore(accountId: String)

    fun levelOf(accountId: String): Int?

    fun setLevel(accountIds: List<String>, level: Int)

    fun collapseLevelsAbove(level: Int)
}

val StorageRouter.sessionLevel: LevelDaos get() = level(rootLevel)

inline fun <T : Any> StorageRouter.firstInAccounts(query: AccountDaos.() -> T?): T? =
    accounts.firstNotNullOfOrNull { it.query() }

inline fun <T> StorageRouter.acrossAccounts(query: AccountDaos.() -> List<T>): List<T> =
    accounts.flatMap { it.query() }

inline fun StorageRouter.forEachAccount(action: AccountDaos.() -> Unit) =
    accounts.forEach { it.action() }

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> StorageRouter.observeAcrossAccounts(query: AccountDaos.() -> Flow<List<T>>): Flow<List<T>> =
    accountStores.flatMapLatest { stores ->
        val flows = stores.map { it.query() }
        if (flows.isEmpty()) flowOf(emptyList()) else combine(flows) { it.asList().flatten() }
    }

/** Runs [write] once per store, in one call, so a batch stays one transaction within a store. */
inline fun <T> StorageRouter.writePerStore(
    rows: List<T>,
    accountIdOf: (T) -> String,
    write: AccountDaos.(List<T>) -> Unit,
) {
    rows.groupBy { account(accountIdOf(it)) }.forEach { (store, storeRows) -> store?.write(storeRows) }
}

fun StorageRouter.liveAccountIds(): List<String> = acrossAccounts { accountsDao().liveAccountIds() }
