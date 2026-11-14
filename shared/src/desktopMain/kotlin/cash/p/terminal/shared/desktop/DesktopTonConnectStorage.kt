package cash.p.terminal.shared.desktop

import cash.p.terminal.core.managers.DatabaseKey
import cash.p.terminal.core.managers.TonConnectStorage
import cash.p.terminal.shared.desktop.vault.DesktopKitDatabaseKeyProvider
import cash.p.terminal.shared.desktop.vault.DesktopKitPlatformContexts
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.supportsTonConnect
import io.horizontalsystems.databasenaming.DatabaseNameResolver
import io.horizontalsystems.tonkit.PlatformContext

/**
 * Each account keeps its own sessions in its store: one shared database would hold every level's dApps,
 * while a session sees only the accounts of its own level.
 */
class DesktopTonConnectStorage(
    private val vault: DesktopVault,
    private val platformContexts: DesktopKitPlatformContexts,
    private val accountManager: IAccountManager,
) : TonConnectStorage {
    private val keyProvider = DesktopKitDatabaseKeyProvider(vault, KIT_NAME)

    override fun scopeOf(accountId: String) = accountId

    // The account's store, its sessions included, is deleted with the account.
    override val sharedByAccounts = false

    override fun liveScopes(): Set<String> =
        accountManager.accounts.filter { it.supportsTonConnect() }.mapTo(mutableSetOf()) { it.id }

    override fun isCreated(scope: String) = vault.openSession()?.account(scope)?.hasKitKey(KIT_NAME) == true

    override fun platformContext(scope: String): PlatformContext = platformContexts.tonConnect(scope)

    override fun nameResolver(scope: String): DatabaseNameResolver = platformContexts.resolverFor(scope)

    override suspend fun databaseKey(scope: String): DatabaseKey = keyProvider.awaitDatabaseKey(scope)

    // The key dies with the account's store.
    override fun removeDatabaseKey(scope: String) = Unit

    private companion object {
        const val KIT_NAME = "ton-connect"
    }
}
