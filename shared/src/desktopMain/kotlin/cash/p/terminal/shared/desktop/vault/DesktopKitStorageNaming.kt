package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.core.StorageLockedException
import cash.p.terminal.core.managers.KitStorageNaming
import io.horizontalsystems.databasenaming.DatabaseNameResolver

/** A name on disk says nothing about the network, the coin or the account, and two kits' names never collide. */
class DesktopKitStorageNaming(
    private val files: DesktopVaultFiles,
    private val vault: DesktopVault,
) : KitStorageNaming {

    override fun resolverFor(accountId: String): DatabaseNameResolver = opaqueWith(vault.storageIdOf(accountId))

    override val sharedResolver: DatabaseNameResolver
        get() = opaqueWith(vault.openSession()?.sharedNamesSecret() ?: throw StorageLockedException())

    private fun opaqueWith(secret: String) = DatabaseNameResolver { files.opaqueName(secret, it) }
}
