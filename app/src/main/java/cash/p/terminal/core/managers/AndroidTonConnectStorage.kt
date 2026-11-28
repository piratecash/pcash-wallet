package cash.p.terminal.core.managers

import android.content.Context
import co.touchlab.kermit.Logger
import io.horizontalsystems.databasenaming.DatabaseNameResolver
import io.horizontalsystems.tonkit.PlatformContext

/** One database for every account, as Android has always stored it: names and key id never change. */
class AndroidTonConnectStorage(
    private val context: Context,
    private val keyProvider: TonConnectDatabaseKeyProvider,
) : TonConnectStorage {
    private val logger = Logger.withTag("AndroidTonConnectStorage")

    override fun scopeOf(accountId: String) = TON_CONNECT_DATABASE_ID

    override val sharedByAccounts = true

    override fun liveScopes() = setOf(TON_CONNECT_DATABASE_ID)

    override fun isCreated(scope: String) = true

    override fun platformContext(scope: String): PlatformContext = context

    override fun nameResolver(scope: String) = DatabaseNameResolver.Identity

    override suspend fun databaseKey(scope: String) = keyProvider.awaitDatabaseKey(TON_CONNECT_DATABASE_ID)

    override fun removeDatabaseKey(scope: String) {
        try {
            keyProvider.remove(TON_CONNECT_DATABASE_ID)
        } catch (e: KitDatabaseKeyException) {
            logger.w(e) { "Failed to remove TON Connect database key" }
        }
    }

    companion object {
        const val TON_CONNECT_DATABASE_ID = "ton-connect"
    }
}
