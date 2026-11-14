package cash.p.terminal.core.managers

import io.horizontalsystems.databasenaming.DatabaseNameResolver
import io.horizontalsystems.tonkit.PlatformContext

/** Where TON Connect keeps sessions: Android shares one database, desktop gives each account its own. */
interface TonConnectStorage {
    fun scopeOf(accountId: String): String

    /** One database holds several accounts' sessions, so a deleted account's ones must be pruned from it. */
    val sharedByAccounts: Boolean

    /** Scopes allowed right now, read from the current accounts; a scope outside it is closed. */
    fun liveScopes(): Set<String>

    /** True when the scope's database exists: such a kit opens at start, others on first use. */
    fun isCreated(scope: String): Boolean

    fun platformContext(scope: String): PlatformContext

    fun nameResolver(scope: String): DatabaseNameResolver

    suspend fun databaseKey(scope: String): DatabaseKey

    /** Failures are logged, never thrown: a key that stays only costs a later database recreation. */
    fun removeDatabaseKey(scope: String)
}
