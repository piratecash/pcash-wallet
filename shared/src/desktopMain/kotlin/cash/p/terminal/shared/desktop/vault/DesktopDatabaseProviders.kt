package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.core.StorageLockedException
import cash.p.terminal.core.storage.AppDatabase
import cash.p.terminal.core.storage.AppDatabaseProvider
import cash.p.terminal.premium.data.database.PremiumDatabase
import cash.p.terminal.premium.data.database.PremiumDatabaseProvider

/** The databases of the vault session open right now; none before the first unlock or after a wipe. */
class DesktopAppDatabaseProvider(private val vault: DesktopVault) : AppDatabaseProvider {
    override val database: AppDatabase get() = vault.requireSession().database
}

class DesktopPremiumDatabaseProvider(private val vault: DesktopVault) : PremiumDatabaseProvider {
    override val database: PremiumDatabase get() = vault.requireSession().premiumDatabase
}

private fun DesktopVault.requireSession(): DesktopStorageSession = openSession() ?: throw StorageLockedException()
